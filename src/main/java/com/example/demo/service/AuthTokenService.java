package com.example.demo.service;

import com.example.demo.enums.TipoTokenAuthEnum;
import com.example.demo.model.AuthToken;
import com.example.demo.repository.AuthTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class AuthTokenService {

    static final int TAMANHO_TOKEN_BYTES = 32;
    static final String MENSAGEM_INVALIDO = "Link inválido ou expirado. Solicite um novo.";
    static final int TAMANHO_SAL_CODIGO_BYTES = 16;
    static final int MAX_TENTATIVAS_CODIGO = 5;
    private static final String HMAC = "HmacSHA256";
    private static final String CONTEXTO_CHAVE_CODIGO = "bokka:auth-token:codigo:v1";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthTokenRepository authTokenRepository;
    private final MongoTemplate mongoTemplate;
    private final Clock clock;
    private final SecretKeySpec chaveCodigo;

    @Autowired
    public AuthTokenService(AuthTokenRepository authTokenRepository, MongoTemplate mongoTemplate,
                            @Value("${api.security.token.secret}") String segredo) {
        this(authTokenRepository, mongoTemplate, Clock.systemUTC(), segredo);
    }

    AuthTokenService(AuthTokenRepository authTokenRepository, MongoTemplate mongoTemplate, Clock clock, String segredo) {
        this.authTokenRepository = authTokenRepository;
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
        this.chaveCodigo = derivarChave(segredo);
    }

    public record TokenEmitido(String token, Instant expiraEm) {}

    /** O {@code codigo} em claro só existe neste retorno (vai para o e-mail); no banco fica o HMAC com sal. */
    public record CodigoEmitido(String token, String codigo, Instant expiraEm) {
        @Override
        public String toString() {
            return "CodigoEmitido[expiraEm=" + expiraEm + "]";
        }
    }

    /**
     * Gera um token novo e invalida os pendentes do mesmo usuário e tipo. O token em claro só existe no
     * retorno (para ir no link do e-mail); no banco fica apenas o hash SHA-256.
     */
    public TokenEmitido emitir(String userId, String clinicaId, TipoTokenAuthEnum tipo, Duration validade,
                               String ipSolicitacao) {
        byte[] bytes = new byte[TAMANHO_TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Instant agora = clock.instant();
        Instant expiraEm = agora.plus(validade);

        authTokenRepository.deleteByUserIdAndTipoAndUsadoEmIsNull(userId, tipo);
        authTokenRepository.save(AuthToken.builder()
                .userId(userId)
                .clinicaId(clinicaId)
                .tokenHash(hash(token))
                .tipo(tipo)
                .criadoEm(agora)
                .expiraEm(expiraEm)
                .ipSolicitacao(ipSolicitacao)
                .build());
        return new TokenEmitido(token, expiraEm);
    }

    /**
     * Como {@link #emitir}, mas o mesmo documento leva também um código de 6 dígitos (SecureRandom) com
     * validade própria. Link e código morrem juntos: usar um, ou pedir um novo par, invalida o outro.
     */
    public CodigoEmitido emitirComCodigo(String userId, String clinicaId, TipoTokenAuthEnum tipo, Duration validade,
                                         Duration validadeCodigo, String ipSolicitacao) {
        byte[] bytes = new byte[TAMANHO_TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        String codigo = String.format("%06d", RANDOM.nextInt(1_000_000));
        byte[] sal = new byte[TAMANHO_SAL_CODIGO_BYTES];
        RANDOM.nextBytes(sal);
        String salHex = HexFormat.of().formatHex(sal);

        Instant agora = clock.instant();
        Instant expiraEm = agora.plus(validade);

        authTokenRepository.deleteByUserIdAndTipoAndUsadoEmIsNull(userId, tipo);
        authTokenRepository.save(AuthToken.builder()
                .userId(userId)
                .clinicaId(clinicaId)
                .tokenHash(hash(token))
                .tipo(tipo)
                .criadoEm(agora)
                .expiraEm(expiraEm)
                .ipSolicitacao(ipSolicitacao)
                .codigoHash(hmacCodigo(salHex, codigo))
                .codigoSalt(salHex)
                .codigoExpiraEm(agora.plus(validadeCodigo))
                .codigoTentativas(0)
                .build());
        return new CodigoEmitido(token, codigo, expiraEm);
    }

    /**
     * Confere o código de 6 dígitos do token de reset pendente do usuário. Acertou: queima o token inteiro
     * (o link também morre) numa operação atômica e o devolve. Errou: soma uma tentativa com {@code $inc}
     * atômico e, na quinta, bloqueia o código (o link continua valendo). Qualquer outra falha (sem token,
     * sem código, vencido, bloqueado) devolve vazio sem distinguir o motivo, sempre depois de um HMAC.
     */
    public Optional<AuthToken> consumirPorCodigo(String userId, TipoTokenAuthEnum tipo, String codigo) {
        Instant agora = clock.instant();
        AuthToken pendente = mongoTemplate.findOne(new Query(Criteria.where("userId").is(userId)
                .and("tipo").is(tipo)
                .and("usadoEm").is(null)
                .and("codigoHash").ne(null)
                .and("expiraEm").gt(agora)), AuthToken.class);
        if (pendente == null) {
            simularConferencia(codigo);
            return Optional.empty();
        }

        boolean confere = MessageDigest.isEqual(
                pendente.getCodigoHash().getBytes(StandardCharsets.UTF_8),
                hmacCodigo(pendente.getCodigoSalt(), codigo).getBytes(StandardCharsets.UTF_8));

        Criteria aindaUtilizavel = Criteria.where("id").is(pendente.getId())
                .and("usadoEm").is(null)
                .and("codigoBloqueado").ne(true)
                .and("codigoTentativas").lt(MAX_TENTATIVAS_CODIGO)
                .and("codigoExpiraEm").gt(agora)
                .and("expiraEm").gt(agora);

        if (confere) {
            return Optional.ofNullable(mongoTemplate.findAndModify(new Query(aindaUtilizavel),
                    new Update().set("usadoEm", agora), FindAndModifyOptions.options().returnNew(true), AuthToken.class));
        }

        AuthToken apos = mongoTemplate.findAndModify(new Query(aindaUtilizavel),
                new Update().inc("codigoTentativas", 1), FindAndModifyOptions.options().returnNew(true), AuthToken.class);
        if (apos != null && apos.getCodigoTentativas() >= MAX_TENTATIVAS_CODIGO) {
            mongoTemplate.updateFirst(new Query(Criteria.where("id").is(apos.getId())),
                    new Update().set("codigoBloqueado", true), AuthToken.class);
        }
        return Optional.empty();
    }

    /** Gasta o mesmo trabalho de uma conferência real (HMAC com sal aleatório) quando não há o que conferir. */
    public void simularConferencia(String codigo) {
        byte[] sal = new byte[TAMANHO_SAL_CODIGO_BYTES];
        RANDOM.nextBytes(sal);
        hmacCodigo(HexFormat.of().formatHex(sal), codigo == null ? "" : codigo);
    }

    /** Confere o token sem consumir (ex.: abrir a tela de nova senha). Qualquer falha dá a mesma resposta. */
    public AuthToken validar(String token, TipoTokenAuthEnum tipo) {
        if (token == null || token.isBlank()) {
            throw tokenInvalido();
        }
        AuthToken registro = authTokenRepository.findByTokenHash(hash(token)).orElseThrow(this::tokenInvalido);
        if (registro.getTipo() != tipo || registro.getUsadoEm() != null
                || !registro.getExpiraEm().isAfter(clock.instant())) {
            throw tokenInvalido();
        }
        return registro;
    }

    /**
     * Valida e queima o token numa única operação atômica no banco: duas requisições simultâneas com o
     * mesmo token nunca passam as duas.
     */
    public AuthToken consumir(String token, TipoTokenAuthEnum tipo) {
        if (token == null || token.isBlank()) {
            throw tokenInvalido();
        }
        Instant agora = clock.instant();
        Query query = new Query(Criteria.where("tokenHash").is(hash(token))
                .and("tipo").is(tipo)
                .and("usadoEm").is(null)
                .and("expiraEm").gt(agora));
        AuthToken consumido = mongoTemplate.findAndModify(query, new Update().set("usadoEm", agora),
                FindAndModifyOptions.options().returnNew(true), AuthToken.class);
        if (consumido == null) {
            throw tokenInvalido();
        }
        return consumido;
    }

    /** Apaga os tokens ainda não usados do usuário (ex.: depois de redefinir a senha). */
    public void invalidarPendentes(String userId, TipoTokenAuthEnum tipo) {
        authTokenRepository.deleteByUserIdAndTipoAndUsadoEmIsNull(userId, tipo);
    }

    private String hmacCodigo(String salHex, String codigo) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(chaveCodigo);
            mac.update(HexFormat.of().parseHex(salHex));
            return HexFormat.of().formatHex(mac.doFinal(codigo.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 indisponível", e);
        }
    }

    private static SecretKeySpec derivarChave(String segredo) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), HMAC));
            return new SecretKeySpec(mac.doFinal(CONTEXTO_CHAVE_CODIGO.getBytes(StandardCharsets.UTF_8)), HMAC);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 indisponível", e);
        }
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    private ResponseStatusException tokenInvalido() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, MENSAGEM_INVALIDO);
    }
}

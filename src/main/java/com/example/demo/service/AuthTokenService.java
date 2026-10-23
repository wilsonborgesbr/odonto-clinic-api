package com.example.demo.service;

import com.example.demo.enums.TipoTokenAuthEnum;
import com.example.demo.model.AuthToken;
import com.example.demo.repository.AuthTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class AuthTokenService {

    static final int TAMANHO_TOKEN_BYTES = 32;
    static final String MENSAGEM_INVALIDO = "Link inválido ou expirado. Solicite um novo.";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthTokenRepository authTokenRepository;
    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    @Autowired
    public AuthTokenService(AuthTokenRepository authTokenRepository, MongoTemplate mongoTemplate) {
        this(authTokenRepository, mongoTemplate, Clock.systemUTC());
    }

    AuthTokenService(AuthTokenRepository authTokenRepository, MongoTemplate mongoTemplate, Clock clock) {
        this.authTokenRepository = authTokenRepository;
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
    }

    public record TokenEmitido(String token, Instant expiraEm) {}

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

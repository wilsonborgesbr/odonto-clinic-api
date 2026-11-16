package com.example.demo.googlecalendar;

import com.example.demo.enums.SyncStatusGoogleEnum;
import com.example.demo.model.GoogleCalendarConexao;
import com.example.demo.repository.GoogleCalendarConexaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/** Entrega um access token válido, renovando-o 5 minutos antes de vencer, e registra quando o acesso cai. */
@Service
public class GoogleTokenService {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenService.class);

    static final String CAMPO_ACCESS = "access_token";
    static final String CAMPO_REFRESH = "refresh_token";

    @Autowired
    private GoogleCalendarApi api;
    @Autowired
    private GoogleCalendarConexaoRepository conexaoRepository;
    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private CriptografiaTokens criptografia;
    @Autowired
    private GoogleCalendarProperties props;

    private Clock relogio = Clock.systemUTC();

    /** Um lock por conexão: duas threads não renovam o mesmo token ao mesmo tempo. */
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    void usarRelogio(Clock relogio) {
        this.relogio = relogio;
    }

    /**
     * @throws ConexaoIndisponivelException se a conexão não está ATIVA ou o Google recusou a renovação
     */
    public String accessTokenValido(GoogleCalendarConexao conexao) {
        if (conexao.getSyncStatus() != SyncStatusGoogleEnum.ATIVO) {
            throw new ConexaoIndisponivelException("Conexão " + conexao.getId() + " não está ativa.");
        }
        if (!precisaRenovar(conexao)) {
            return decifrar(conexao, CAMPO_ACCESS, conexao.getTokens().getAccessTokenEncrypted());
        }
        synchronized (locks.computeIfAbsent(conexao.getId(), k -> new Object())) {
            GoogleCalendarConexao atual = conexaoRepository.findById(conexao.getId())
                    .orElseThrow(() -> new ConexaoIndisponivelException("Conexão removida."));
            if (atual.getSyncStatus() != SyncStatusGoogleEnum.ATIVO) {
                throw new ConexaoIndisponivelException("Conexão " + atual.getId() + " não está ativa.");
            }
            if (!precisaRenovar(atual)) {
                return decifrar(atual, CAMPO_ACCESS, atual.getTokens().getAccessTokenEncrypted());
            }
            return renovar(atual);
        }
    }

    private boolean precisaRenovar(GoogleCalendarConexao c) {
        GoogleCalendarConexao.Tokens t = c.getTokens();
        return t == null || t.getExpiresAt() == null
                || !relogio.instant().plus(props.margemRenovacaoToken()).isBefore(t.getExpiresAt());
    }

    private String renovar(GoogleCalendarConexao conexao) {
        String refresh = decifrar(conexao, CAMPO_REFRESH, conexao.getTokens().getRefreshTokenEncrypted());
        GoogleCalendarApi.Tokens novos;
        try {
            novos = api.renovar(refresh);
        } catch (GoogleApiException e) {
            if (e.concessaoInvalida()) {
                registrarStatus(conexao.getId(), SyncStatusGoogleEnum.REVOGADO_PELO_GOOGLE);
                throw new ConexaoIndisponivelException("O Google revogou o acesso da conexão " + conexao.getId() + ".");
            }
            if (e.status() == 400 || e.status() == 401) {
                registrarStatus(conexao.getId(), SyncStatusGoogleEnum.ERRO_AUTENTICACAO);
                throw new ConexaoIndisponivelException("Falha de autenticação na conexão " + conexao.getId() + ".");
            }
            throw e;
        }

        Update update = new Update()
                .set("tokens.accessTokenEncrypted", criptografia.cifrar(novos.accessToken(),
                        CriptografiaTokens.contexto(conexao.getClinicaId(), conexao.getUserId(), CAMPO_ACCESS)))
                .set("tokens.expiresAt", novos.expiraEm())
                .set("updatedAt", LocalDateTime.now());
        if (novos.refreshToken() != null) {
            update.set("tokens.refreshTokenEncrypted", criptografia.cifrar(novos.refreshToken(),
                    CriptografiaTokens.contexto(conexao.getClinicaId(), conexao.getUserId(), CAMPO_REFRESH)));
        }
        if (novos.scope() != null) {
            update.set("tokens.scope", novos.scope());
        }
        mongoTemplate.updateFirst(new Query(Criteria.where("id").is(conexao.getId())), update, GoogleCalendarConexao.class);
        return novos.accessToken();
    }

    /** Só sai de ATIVO: nunca sobrescreve uma pausa do dono com um erro do Google. */
    public void registrarStatus(String conexaoId, SyncStatusGoogleEnum novo) {
        mongoTemplate.updateFirst(
                new Query(Criteria.where("id").is(conexaoId).and("syncStatus").is(SyncStatusGoogleEnum.ATIVO)),
                new Update().set("syncStatus", novo).set("updatedAt", LocalDateTime.now()),
                GoogleCalendarConexao.class);
        log.warn("Conexão Google Calendar {} passou para {}.", conexaoId, novo);
    }

    /**
     * Token para operações de encerramento (parar canal, revogar) em conexão que pode já não estar ATIVA.
     * Não grava nada nem muda status; vazio se não foi possível obter um token.
     */
    public java.util.Optional<String> accessTokenParaEncerrar(GoogleCalendarConexao conexao) {
        try {
            if (conexao.getTokens() == null) {
                return java.util.Optional.empty();
            }
            if (!precisaRenovar(conexao)) {
                return java.util.Optional.of(decifrar(conexao, CAMPO_ACCESS, conexao.getTokens().getAccessTokenEncrypted()));
            }
            return java.util.Optional.of(api.renovar(refreshToken(conexao)).accessToken());
        } catch (RuntimeException e) {
            log.info("Sem token para encerrar a conexão {}: {}", conexao.getId(), e.getMessage());
            return java.util.Optional.empty();
        }
    }

    public String refreshToken(GoogleCalendarConexao conexao) {
        return decifrar(conexao, CAMPO_REFRESH, conexao.getTokens().getRefreshTokenEncrypted());
    }

    private String decifrar(GoogleCalendarConexao c, String campo, String cifrado) {
        try {
            return criptografia.decifrar(cifrado, CriptografiaTokens.contexto(c.getClinicaId(), c.getUserId(), campo));
        } catch (IllegalStateException e) {
            registrarStatus(c.getId(), SyncStatusGoogleEnum.ERRO_AUTENTICACAO);
            throw new ConexaoIndisponivelException("Token da conexão " + c.getId() + " não pôde ser decifrado.");
        }
    }

    Instant agora() {
        return relogio.instant();
    }
}

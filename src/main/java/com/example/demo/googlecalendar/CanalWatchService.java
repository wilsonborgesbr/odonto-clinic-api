package com.example.demo.googlecalendar;

import com.example.demo.enums.SyncStatusGoogleEnum;
import com.example.demo.model.GoogleCalendarConexao;
import com.example.demo.repository.GoogleCalendarConexaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Canal de notificação (watch) do Google Calendar: criação, renovação e parada. O {@code token} que o Google
 * devolve em cada notificação é derivado do id do canal por HMAC, então não precisa ser guardado: basta
 * recalcular e comparar.
 */
@Service
public class CanalWatchService {

    private static final Logger log = LoggerFactory.getLogger(CanalWatchService.class);
    private static final String ROTULO_CHAVE = "bokka-google-calendar-channel-token-v1";

    @Autowired
    private GoogleCalendarConexaoRepository conexaoRepository;
    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private GoogleCalendarApi api;
    @Autowired
    private GoogleTokenService tokenService;
    @Autowired
    private SincronizacaoEntradaService entrada;
    @Autowired
    private TentativasGoogle tentativas;
    @Autowired
    private GoogleCalendarProperties props;

    private final byte[] chave;
    private Clock relogio = Clock.systemUTC();

    @Autowired
    public CanalWatchService(@Value("${api.security.token.secret}") String segredoJwt) {
        this.chave = hmac(segredoJwt.getBytes(StandardCharsets.UTF_8), ROTULO_CHAVE.getBytes(StandardCharsets.UTF_8));
    }

    void usarRelogio(Clock relogio) {
        this.relogio = relogio;
    }

    public String tokenDoCanal(String channelId) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(chave, channelId.getBytes(StandardCharsets.UTF_8)));
    }

    public boolean tokenConfere(String channelId, String informado) {
        if (informado == null) {
            return false;
        }
        return MessageDigest.isEqual(tokenDoCanal(channelId).getBytes(StandardCharsets.UTF_8),
                informado.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Prepara a conexão para receber do Google: obtém o syncToken inicial e abre o canal. Sem URL pública
     * HTTPS configurada, não faz nada (desenvolvimento local).
     */
    public void ativar(String conexaoId) {
        if (!props.webhookConfigurado()) {
            log.info("google.calendar.webhook-url não é HTTPS: canal de notificação não criado (conexão {}).", conexaoId);
            return;
        }
        GoogleCalendarConexao conexao = conexaoRepository.findById(conexaoId).orElse(null);
        if (conexao == null || conexao.getSyncStatus() != SyncStatusGoogleEnum.ATIVO) {
            return;
        }
        try {
            if (conexao.getSyncToken() == null) {
                entrada.iniciarSyncToken(conexao);
            }
            abrirCanal(conexao);
        } catch (ConexaoIndisponivelException e) {
            log.info("Canal não criado: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.error("Falha ao criar o canal da conexão {}: {}", conexaoId, e.getMessage());
        }
    }

    /** Abre um canal novo e só depois para o antigo, para nunca ficar sem notificação. */
    private void abrirCanal(GoogleCalendarConexao conexao) {
        String token = tokenService.accessTokenValido(conexao);
        String channelId = UUID.randomUUID().toString();
        Instant expiracao = relogio.instant().plus(props.duracaoCanal());
        GoogleCalendarApi.Canal canal = tentativas.executar(() -> api.criarCanal(
                token, conexao.getGoogleCalendarId(), channelId, props.webhookUrl(), tokenDoCanal(channelId), expiracao));

        GoogleCalendarConexao.WebhookWatch antigo = conexao.getWebhookWatch();
        mongoTemplate.updateFirst(new Query(Criteria.where("id").is(conexao.getId())),
                new Update().set("webhookWatch", GoogleCalendarConexao.WebhookWatch.builder()
                                .channelId(canal.channelId()).resourceId(canal.resourceId()).expiration(canal.expiracao()).build())
                        .set("updatedAt", LocalDateTime.now()),
                GoogleCalendarConexao.class);
        if (antigo != null && antigo.getChannelId() != null) {
            pararCanal(token, antigo);
        }
    }

    /** Melhor esforço: o canal expira sozinho se a chamada falhar. */
    private void pararCanal(String accessToken, GoogleCalendarConexao.WebhookWatch watch) {
        try {
            tentativas.executar(() -> api.pararCanal(accessToken, watch.getChannelId(), watch.getResourceId()));
        } catch (RuntimeException e) {
            log.warn("Não foi possível parar o canal {} no Google: {}", watch.getChannelId(), e.getMessage());
        }
    }

    /**
     * Limpa o registro do canal (e o syncToken) e o para no Google (pausa pelo dono, desconexão). Recebe a
     * conexão como estava, pois o status já pode ter saído de ATIVO. Pode ser chamado de uma thread assíncrona.
     */
    public void encerrar(GoogleCalendarConexao conexao) {
        GoogleCalendarConexao.WebhookWatch watch = conexao.getWebhookWatch();
        mongoTemplate.updateFirst(new Query(Criteria.where("id").is(conexao.getId())),
                new Update().unset("webhookWatch").unset("syncToken").set("updatedAt", LocalDateTime.now()),
                GoogleCalendarConexao.class);
        if (watch != null && watch.getChannelId() != null) {
            tokenService.accessTokenParaEncerrar(conexao).ifPresent(token -> pararCanal(token, watch));
        }
    }

    /** Renova, todo dia, os canais que vencem em breve (e cria o que faltar para conexões ativas). */
    @Scheduled(cron = "${google.calendar.channel-renew-cron:0 30 3 * * *}", zone = "America/Sao_Paulo")
    public void renovarCanaisProximosDoVencimento() {
        if (!props.webhookConfigurado()) {
            return;
        }
        Instant limite = relogio.instant().plus(props.antecedenciaRenovacaoCanal());
        Criteria semCanalOuVencendo = new Criteria().orOperator(
                Criteria.where("webhookWatch").is(null),
                Criteria.where("webhookWatch.expiration").lt(limite));
        List<GoogleCalendarConexao> conexoes = mongoTemplate.find(
                new Query(new Criteria().andOperator(
                        Criteria.where("syncStatus").is(SyncStatusGoogleEnum.ATIVO), semCanalOuVencendo)),
                GoogleCalendarConexao.class);
        for (GoogleCalendarConexao conexao : conexoes) {
            try {
                if (conexao.getSyncToken() == null) {
                    entrada.iniciarSyncToken(conexao);
                }
                abrirCanal(conexao);
            } catch (ConexaoIndisponivelException e) {
                log.info("Canal da conexão {} não renovado: {}", conexao.getId(), e.getMessage());
            } catch (RuntimeException e) {
                log.error("Falha ao renovar o canal da conexão {}: {}", conexao.getId(), e.getMessage());
            }
        }
    }

    private static byte[] hmac(byte[] chave, byte[] dados) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(chave, "HmacSHA256"));
            return mac.doFinal(dados);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC indisponível.", e);
        }
    }
}

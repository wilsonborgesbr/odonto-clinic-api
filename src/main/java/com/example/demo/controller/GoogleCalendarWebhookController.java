package com.example.demo.controller;

import com.example.demo.config.GoogleCalendarAsyncConfig;
import com.example.demo.enums.SyncStatusGoogleEnum;
import com.example.demo.googlecalendar.CanalWatchService;
import com.example.demo.googlecalendar.SincronizacaoEntradaService;
import com.example.demo.model.GoogleCalendarConexao;
import com.example.demo.repository.GoogleCalendarConexaoRepository;
import com.example.demo.service.RateLimitService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Notificações do Google Calendar. Público (o Google não tem JWT), então a autenticação é pelos cabeçalhos:
 * o canal precisa existir, o {@code X-Goog-Resource-ID} precisa ser o do canal e o {@code X-Goog-Channel-Token}
 * precisa ser o segredo que o Bokka registrou ao abrir o canal. O corpo da notificação é vazio e ignorado;
 * o que mudou é buscado no Google, com o token da conexão.
 */
@Hidden
@RestController
@RequestMapping("/api/public/webhooks")
public class GoogleCalendarWebhookController {

    private static final int MAX_POR_CANAL = 120;
    private static final Duration JANELA = Duration.ofMinutes(1);

    @Autowired
    private GoogleCalendarConexaoRepository conexaoRepository;
    @Autowired
    private CanalWatchService canalService;
    @Autowired
    private SincronizacaoEntradaService entrada;
    @Autowired
    private RateLimitService rateLimitService;
    @Autowired
    @Qualifier(GoogleCalendarAsyncConfig.GOOGLE_CALENDAR_EXECUTOR)
    private Executor executor;

    @PostMapping("/google-calendar")
    public ResponseEntity<Void> receber(
            @RequestHeader(value = "X-Goog-Channel-ID", required = false) String channelId,
            @RequestHeader(value = "X-Goog-Resource-ID", required = false) String resourceId,
            @RequestHeader(value = "X-Goog-Channel-Token", required = false) String tokenCanal,
            @RequestHeader(value = "X-Goog-Resource-State", required = false) String estadoRecurso) {

        if (isBlank(channelId) || isBlank(resourceId)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (!rateLimitService.registrarSePermitido("gcal-webhook:" + channelId, MAX_POR_CANAL, JANELA)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        Optional<GoogleCalendarConexao> conexao = conexaoRepository.findByWebhookWatchChannelId(channelId);
        if (conexao.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        GoogleCalendarConexao.WebhookWatch watch = conexao.get().getWebhookWatch();
        if (!resourceId.equals(watch.getResourceId()) || !canalService.tokenConfere(channelId, tokenCanal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // "sync" é só a confirmação de que o canal foi aberto; "exists" indica mudança na agenda.
        if (conexao.get().getSyncStatus() == SyncStatusGoogleEnum.ATIVO && !"sync".equalsIgnoreCase(estadoRecurso)) {
            String id = conexao.get().getId();
            executor.execute(() -> entrada.sincronizar(id));
        }
        return ResponseEntity.ok().build();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

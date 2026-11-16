package com.example.demo.googlecalendar;

import com.example.demo.controller.GoogleCalendarWebhookController;
import com.example.demo.enums.SyncStatusGoogleEnum;
import com.example.demo.model.GoogleCalendarConexao;
import com.example.demo.repository.GoogleCalendarConexaoRepository;
import com.example.demo.service.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários de segurança do webhook público (POST /api/public/webhooks/google-calendar):
 * - Rejeição sem headers obrigatórios (400)
 * - Rate limit de 120/min por canal (429)
 * - Canal não encontrado (404)
 * - Resource-ID não confere (403)
 * - Token do canal não confere (403)
 * - Validação de sucesso e estado "sync" vs "exists" (200)
 */
class GoogleCalendarWebhookSegurancaTest {

    private GoogleCalendarWebhookController controller;
    private GoogleCalendarConexaoRepository conexaoRepository;
    private CanalWatchService canalService;
    private SincronizacaoEntradaService entradaService;
    private RateLimitService rateLimitService;
    private Executor executor;

    @BeforeEach
    void setUp() {
        controller = new GoogleCalendarWebhookController();
        conexaoRepository = mock(GoogleCalendarConexaoRepository.class);
        canalService = mock(CanalWatchService.class);
        entradaService = mock(SincronizacaoEntradaService.class);
        rateLimitService = mock(RateLimitService.class);
        executor = mock(Executor.class);

        ReflectionTestUtils.setField(controller, "conexaoRepository", conexaoRepository);
        ReflectionTestUtils.setField(controller, "canalService", canalService);
        ReflectionTestUtils.setField(controller, "entrada", entradaService);
        ReflectionTestUtils.setField(controller, "rateLimitService", rateLimitService);
        ReflectionTestUtils.setField(controller, "executor", executor);

        // Por padrão, rate limit permite
        when(rateLimitService.registrarSePermitido(anyString(), eq(120), any(Duration.class))).thenReturn(true);
    }

    @Test
    void semHeadersObrigatorios_Retorna400() {
        assertEquals(HttpStatus.BAD_REQUEST, controller.receber(null, null, null, null).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, controller.receber("", "res-1", "token-1", "exists").getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, controller.receber("ch-1", "", "token-1", "exists").getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, controller.receber("   ", "res-1", "token-1", "exists").getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, controller.receber("ch-1", "   ", "token-1", "exists").getStatusCode());

        verifyNoInteractions(conexaoRepository);
    }

    @Test
    void acimaDoRateLimitDe120PorCanal_Retorna429() {
        when(rateLimitService.registrarSePermitido(eq("gcal-webhook:canal-spam"), eq(120), any(Duration.class)))
                .thenReturn(false);

        ResponseEntity<Void> response = controller.receber("canal-spam", "res-1", "token-1", "exists");

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        verifyNoInteractions(conexaoRepository);
    }

    @Test
    void canalNaoEncontradoNoBanco_Retorna404() {
        when(conexaoRepository.findByWebhookWatchChannelId("canal-inexistente")).thenReturn(Optional.empty());

        ResponseEntity<Void> response = controller.receber("canal-inexistente", "res-1", "token-1", "exists");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void resourceIdNaoConfere_Retorna403() {
        GoogleCalendarConexao.WebhookWatch watch = GoogleCalendarConexao.WebhookWatch.builder()
                .channelId("canal-1")
                .resourceId("res-original")
                .build();
        GoogleCalendarConexao conexao = GoogleCalendarConexao.builder()
                .id("con-1")
                .webhookWatch(watch)
                .build();
        when(conexaoRepository.findByWebhookWatchChannelId("canal-1")).thenReturn(Optional.of(conexao));
        when(canalService.tokenConfere("canal-1", "token-correto")).thenReturn(true);

        ResponseEntity<Void> response = controller.receber("canal-1", "res-falso", "token-correto", "exists");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(executor);
    }

    @Test
    void tokenDoCanalNaoConfere_Retorna403() {
        GoogleCalendarConexao.WebhookWatch watch = GoogleCalendarConexao.WebhookWatch.builder()
                .channelId("canal-1")
                .resourceId("res-1")
                .build();
        GoogleCalendarConexao conexao = GoogleCalendarConexao.builder()
                .id("con-1")
                .webhookWatch(watch)
                .build();
        when(conexaoRepository.findByWebhookWatchChannelId("canal-1")).thenReturn(Optional.of(conexao));
        when(canalService.tokenConfere("canal-1", "token-invalido")).thenReturn(false);

        ResponseEntity<Void> response = controller.receber("canal-1", "res-1", "token-invalido", "exists");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(executor);
    }

    @Test
    void notificacaoValidaComEstadoExists_DisparaSincronizacaoAssincronaERetorna200() {
        GoogleCalendarConexao.WebhookWatch watch = GoogleCalendarConexao.WebhookWatch.builder()
                .channelId("canal-1")
                .resourceId("res-1")
                .build();
        GoogleCalendarConexao conexao = GoogleCalendarConexao.builder()
                .id("con-1")
                .syncStatus(SyncStatusGoogleEnum.ATIVO)
                .webhookWatch(watch)
                .build();
        when(conexaoRepository.findByWebhookWatchChannelId("canal-1")).thenReturn(Optional.of(conexao));
        when(canalService.tokenConfere("canal-1", "token-ok")).thenReturn(true);

        ResponseEntity<Void> response = controller.receber("canal-1", "res-1", "token-ok", "exists");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(executor, times(1)).execute(any(Runnable.class));
    }

    @Test
    void notificacaoValidaComEstadoSync_ApenasConfirmaCanalSemDispararSincronizacao() {
        GoogleCalendarConexao.WebhookWatch watch = GoogleCalendarConexao.WebhookWatch.builder()
                .channelId("canal-1")
                .resourceId("res-1")
                .build();
        GoogleCalendarConexao conexao = GoogleCalendarConexao.builder()
                .id("con-1")
                .syncStatus(SyncStatusGoogleEnum.ATIVO)
                .webhookWatch(watch)
                .build();
        when(conexaoRepository.findByWebhookWatchChannelId("canal-1")).thenReturn(Optional.of(conexao));
        when(canalService.tokenConfere("canal-1", "token-ok")).thenReturn(true);

        ResponseEntity<Void> response = controller.receber("canal-1", "res-1", "token-ok", "sync");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verifyNoInteractions(executor);
    }
}

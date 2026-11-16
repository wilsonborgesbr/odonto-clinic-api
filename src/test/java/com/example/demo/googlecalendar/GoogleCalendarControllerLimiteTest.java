package com.example.demo.googlecalendar;

import com.example.demo.controller.GoogleCalendarController;
import com.example.demo.service.RateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** O retorno do OAuth é público: acima do limite por IP, nem chega a olhar o state. */
class GoogleCalendarControllerLimiteTest {

    private GoogleCalendarController controller(RateLimitService limitador, ConexaoGoogleService servico) {
        GoogleCalendarController c = new GoogleCalendarController();
        GoogleCalendarProperties props = new GoogleCalendarProperties("id", "segredo", "http://localhost/cb", "https://app.bokka.test/",
                "/configuracoes", "", "", java.time.Duration.ofMinutes(5), java.time.Duration.ofHours(48),
                java.time.Duration.ofDays(6), 3, java.time.Duration.ZERO);
        ReflectionTestUtils.setField(c, "conexaoService", servico);
        ReflectionTestUtils.setField(c, "props", props);
        ReflectionTestUtils.setField(c, "rateLimitService", limitador);
        ReflectionTestUtils.setField(c, "maxCallbacksPorIp", 30);
        return c;
    }

    @Test
    void acimaDoLimitePorIp_RedirecionaComErroSemTocarNoServico() {
        RateLimitService limitador = mock(RateLimitService.class);
        ConexaoGoogleService servico = mock(ConexaoGoogleService.class);
        when(limitador.registrarSePermitido(eq("gcal-callback:ip:203.0.113.7"), eq(30), any())).thenReturn(false);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("203.0.113.7");

        ResponseEntity<Void> r = controller(limitador, servico).callback("code", "state", null, req);

        assertEquals(302, r.getStatusCode().value());
        assertEquals("https://app.bokka.test/configuracoes?googleCalendar=erro&motivo=muitas_tentativas",
                r.getHeaders().getFirst("Location"));
        verifyNoInteractions(servico);
    }

    @Test
    void dentroDoLimite_DelegaAoServico_ETrataBarraFinalDaUrlDoFrontend() {
        RateLimitService limitador = mock(RateLimitService.class);
        ConexaoGoogleService servico = mock(ConexaoGoogleService.class);
        when(limitador.registrarSePermitido(any(), anyInt(), any())).thenReturn(true);
        when(servico.concluirAutorizacao("c", "s", null)).thenReturn(new ConexaoGoogleService.Resultado(true, null));

        ResponseEntity<Void> r = controller(limitador, servico).callback("c", "s", null, new MockHttpServletRequest());

        assertEquals("https://app.bokka.test/configuracoes?googleCalendar=conectado", r.getHeaders().getFirst("Location"));
    }
}

package com.example.demo.googlecalendar;

import com.example.demo.enums.EscopoGoogleCalendarEnum;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class EstadoOAuthTest {

    private static final Instant T0 = Instant.parse("2026-11-09T12:00:00Z");

    private static Clock em(Instant t) {
        return Clock.fixed(t, ZoneOffset.UTC);
    }

    @Test
    void estadoValido_DevolveQuemIniciouOFluxo() {
        EstadoOAuth e = new EstadoOAuth("segredo", em(T0));

        var dados = e.validar(e.gerar("user1", "clinica1", EscopoGoogleCalendarEnum.SOMENTE_PROPRIO));

        assertTrue(dados.isPresent());
        assertEquals("user1", dados.get().userId());
        assertEquals("clinica1", dados.get().clinicaId());
        assertEquals(EscopoGoogleCalendarEnum.SOMENTE_PROPRIO, dados.get().escopo());
    }

    @Test
    void cadaEstadoEUnico() {
        EstadoOAuth e = new EstadoOAuth("segredo", em(T0));

        assertNotEquals(e.gerar("u", "c", EscopoGoogleCalendarEnum.TODOS_DA_CLINICA), e.gerar("u", "c", EscopoGoogleCalendarEnum.TODOS_DA_CLINICA));
    }

    @Test
    void venceEmDezMinutos() {
        String estado = new EstadoOAuth("segredo", em(T0)).gerar("u", "c", EscopoGoogleCalendarEnum.TODOS_DA_CLINICA);

        assertTrue(new EstadoOAuth("segredo", em(T0.plus(Duration.ofMinutes(10)))).validar(estado).isPresent());
        assertTrue(new EstadoOAuth("segredo", em(T0.plus(Duration.ofMinutes(10)).plusSeconds(2))).validar(estado).isEmpty());
    }

    @Test
    void assinaturaDeOutroSegredo_Rejeitada() {
        String estado = new EstadoOAuth("segredo-A", em(T0)).gerar("u", "c", EscopoGoogleCalendarEnum.TODOS_DA_CLINICA);

        assertTrue(new EstadoOAuth("segredo-B", em(T0)).validar(estado).isEmpty());
    }

    @Test
    void payloadAdulterado_MesmoComAssinaturaAntiga_Rejeitado() {
        EstadoOAuth e = new EstadoOAuth("segredo", em(T0));
        String estado = e.gerar("user1", "clinica1", EscopoGoogleCalendarEnum.SOMENTE_PROPRIO);
        String assinatura = estado.substring(estado.indexOf('.') + 1);
        String payloadForjado = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("outroUser|clinica1|TODOS_DA_CLINICA|nonce|" + (T0.getEpochSecond() + 600)).getBytes());

        assertTrue(e.validar(payloadForjado + "." + assinatura).isEmpty());
    }

    @Test
    void lixoNuloEFormatoInvalido_Rejeitados() {
        EstadoOAuth e = new EstadoOAuth("segredo", em(T0));

        assertTrue(e.validar(null).isEmpty());
        assertTrue(e.validar("").isEmpty());
        assertTrue(e.validar("sem-ponto").isEmpty());
        assertTrue(e.validar(".").isEmpty());
        assertTrue(e.validar("a.").isEmpty());
        assertTrue(e.validar(".b").isEmpty());
        assertTrue(e.validar("!!!.???").isEmpty());
        assertTrue(e.validar("x".repeat(5000)).isEmpty());
    }
}

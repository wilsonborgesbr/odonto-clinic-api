package com.example.demo.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitServiceTest {

    private static final Duration JANELA = Duration.ofMinutes(15);

    private static class RelogioMutavel extends Clock {
        Instant agora = Instant.parse("2026-10-22T13:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    @Test
    void registrarSePermitido_PermiteAteOMaximoEBloqueiaDepois() {
        RateLimitService limitador = new RateLimitService(new RelogioMutavel());

        assertTrue(limitador.registrarSePermitido("k", 3, JANELA));
        assertTrue(limitador.registrarSePermitido("k", 3, JANELA));
        assertTrue(limitador.registrarSePermitido("k", 3, JANELA));
        assertFalse(limitador.registrarSePermitido("k", 3, JANELA));
        assertFalse(limitador.registrarSePermitido("k", 3, JANELA));
    }

    @Test
    void registrarSePermitido_ChavesIndependentes() {
        RateLimitService limitador = new RateLimitService(new RelogioMutavel());
        assertTrue(limitador.registrarSePermitido("a", 1, JANELA));

        assertFalse(limitador.registrarSePermitido("a", 1, JANELA));
        assertTrue(limitador.registrarSePermitido("b", 1, JANELA));
    }

    @Test
    void janelaDesliza_DepoisDe15MinutosLibera() {
        RelogioMutavel relogio = new RelogioMutavel();
        RateLimitService limitador = new RateLimitService(relogio);
        limitador.registrarSePermitido("k", 1, JANELA);
        assertFalse(limitador.registrarSePermitido("k", 1, JANELA));

        relogio.agora = relogio.agora.plus(Duration.ofMinutes(14));
        assertFalse(limitador.registrarSePermitido("k", 1, JANELA));

        relogio.agora = relogio.agora.plus(Duration.ofMinutes(2));
        assertTrue(limitador.registrarSePermitido("k", 1, JANELA));
    }

    @Test
    void registrarEAtingiuLimite_ContaFalhasSemBloquearAntes() {
        RateLimitService limitador = new RateLimitService(new RelogioMutavel());

        assertFalse(limitador.atingiuLimite("f", 2, JANELA));
        limitador.registrar("f", JANELA);
        assertFalse(limitador.atingiuLimite("f", 2, JANELA));
        limitador.registrar("f", JANELA);
        assertTrue(limitador.atingiuLimite("f", 2, JANELA));
    }

    @Test
    void esquecer_ZeraOContador() {
        RateLimitService limitador = new RateLimitService(new RelogioMutavel());
        limitador.registrar("f", JANELA);
        limitador.registrar("f", JANELA);

        limitador.esquecer("f");

        assertFalse(limitador.atingiuLimite("f", 2, JANELA));
    }

    @Test
    void consultaDeChaveDesconhecidaNaoCriaEstadoNemBloqueia() {
        RateLimitService limitador = new RateLimitService(new RelogioMutavel());
        assertFalse(limitador.atingiuLimite("nunca-vista", 1, JANELA));
    }
}

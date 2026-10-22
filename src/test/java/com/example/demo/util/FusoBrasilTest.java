package com.example.demo.util;

import com.example.demo.validation.DataNaoFuturaValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

class FusoBrasilTest {

    private TimeZone fusoOriginal;

    @BeforeEach
    void guardarFuso() {
        fusoOriginal = TimeZone.getDefault();
    }

    @AfterEach
    void restaurarFuso() {
        TimeZone.setDefault(fusoOriginal);
    }

    @Test
    void hoje_UsaOFusoDeSaoPauloMesmoComJvmEmOutroFuso() {
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
        LocalDate hojeEmSaoPaulo = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

        assertEquals(hojeEmSaoPaulo, FusoBrasil.hoje());
    }

    @Test
    void hojeComRelogio_ConverteOInstanteParaSaoPauloIndependenteDoFusoDoRelogio() {
        Clock utc = Clock.fixed(Instant.parse("2026-10-24T01:30:00Z"), ZoneOffset.UTC);
        Clock quirguistao = Clock.fixed(Instant.parse("2026-10-24T01:30:00Z"), ZoneId.of("Pacific/Kiritimati"));

        assertEquals(LocalDate.of(2026, 10, 23), FusoBrasil.hoje(utc));
        assertEquals(LocalDate.of(2026, 10, 23), FusoBrasil.hoje(quirguistao));
        assertEquals(LocalDate.of(2026, 10, 24),
                FusoBrasil.hoje(Clock.fixed(Instant.parse("2026-10-24T03:00:00Z"), ZoneOffset.UTC)));
    }

    @Test
    void dataNaoFutura_UsaOFusoDeSaoPauloENaoODaJvm() {
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
        DataNaoFuturaValidator validador = new DataNaoFuturaValidator();

        assertTrue(validador.isValid(FusoBrasil.hoje(), null));
        assertTrue(validador.isValid(null, null));
        assertFalse(validador.isValid(FusoBrasil.hoje().plusDays(1), null));
    }
}

package com.example.demo.util;

import com.example.demo.validation.DataNaoFuturaValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
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
    void dataNaoFutura_UsaOFusoDeSaoPauloENaoODaJvm() {
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
        DataNaoFuturaValidator validador = new DataNaoFuturaValidator();

        assertTrue(validador.isValid(FusoBrasil.hoje(), null));
        assertTrue(validador.isValid(null, null));
        assertFalse(validador.isValid(FusoBrasil.hoje().plusDays(1), null));
    }
}

package com.example.demo.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FusoGravacaoTest {

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
    void acompanhaOFusoDaJvmEnaoOdeSaoPaulo() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        assertEquals("UTC", FusoGravacao.idMongo());
        assertEquals(ZoneId.of("UTC"), FusoGravacao.zona());

        TimeZone.setDefault(TimeZone.getTimeZone("Etc/UTC"));
        assertEquals("Etc/UTC", FusoGravacao.idMongo());

        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        assertEquals("America/Sao_Paulo", FusoGravacao.idMongo());
    }
}

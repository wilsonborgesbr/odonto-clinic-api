package com.example.demo.util;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

public final class FusoBrasil {

    public static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    private FusoBrasil() {}

    public static LocalDate hoje() {
        return LocalDate.now(ZONA);
    }

    public static LocalDate hoje(Clock relogio) {
        return LocalDate.now(relogio.withZone(ZONA));
    }
}

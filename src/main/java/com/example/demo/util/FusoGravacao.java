package com.example.demo.util;

import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Fuso com que o Spring Data grava e lê LocalDate/LocalDateTime no Mongo: o padrão da JVM, não o de São Paulo.
 * Na VPS é UTC e no ambiente local é America/Sao_Paulo, então os mesmos "horários de parede" ficam gravados como
 * instantes diferentes. Toda agregação por dia/mês deve agrupar com este fuso (e calcular as fronteiras do período
 * em LocalDate/LocalDateTime, deixando o conversor do Spring convertê-las igual à gravação), para devolver o dia
 * que o usuário viu na tela em qualquer ambiente. O "hoje" do negócio continua sendo {@link FusoBrasil#hoje()}.
 */
public final class FusoGravacao {

    private FusoGravacao() {}

    public static ZoneId zona() {
        return ZoneId.systemDefault();
    }

    /** Identificador de fuso aceito pelo operador de datas do MongoDB (nome IANA ou deslocamento). */
    public static String idMongo() {
        ZoneId zona = zona();
        return zona instanceof ZoneOffset && ((ZoneOffset) zona).getTotalSeconds() == 0 ? "UTC" : zona.getId();
    }
}

package com.example.demo.util;

import java.util.regex.Pattern;

/**
 * Notação FDI (ISO 3950): quadrante + posição. Permanentes: quadrantes 1 a 4, dentes 1 a 8 (11-18, 21-28,
 * 31-38, 41-48). Decíduos: quadrantes 5 a 8, dentes 1 a 5 (51-55, 61-65, 71-75, 81-85).
 */
public final class DenteFdi {

    /** Sem âncoras: o {@code @Pattern} do Bean Validation já exige o texto inteiro. */
    public static final String REGEX = "[1-4][1-8]|[5-8][1-5]";

    /** Para campos legados em que vazio significa "sem dente". */
    public static final String REGEX_OPCIONAL = "(?:" + REGEX + ")?";

    public static final String MENSAGEM = "O dente deve seguir a notação FDI: 11-18, 21-28, 31-38 e 41-48 (permanentes) "
            + "ou 51-55, 61-65, 71-75 e 81-85 (decíduos).";

    private static final Pattern PADRAO = Pattern.compile(REGEX);

    private DenteFdi() {}

    public static boolean valido(String dente) {
        return dente != null && PADRAO.matcher(dente).matches();
    }
}

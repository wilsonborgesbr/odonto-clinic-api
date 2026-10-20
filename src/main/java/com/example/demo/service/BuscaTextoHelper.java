package com.example.demo.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class BuscaTextoHelper {

    private static final Pattern DIACRITICOS = Pattern.compile("\\p{M}+");
    private static final Pattern SOMENTE_NUMERICO = Pattern.compile("[\\d.\\-/\\s]*\\d[\\d.\\-/\\s]*");

    private static final Map<Character, String> VARIANTES = Map.of(
            'a', "aàáâãäå",
            'e', "eèéêë",
            'i', "iìíîï",
            'o', "oòóôõö",
            'u', "uùúûü",
            'c', "cç",
            'n', "nñ",
            'y', "yýÿ"
    );

    private BuscaTextoHelper() {}

    public static String semAcento(String texto) {
        String decomposto = Normalizer.normalize(texto, Normalizer.Form.NFD);
        return DIACRITICOS.matcher(decomposto).replaceAll("").toLowerCase(Locale.ROOT);
    }

    public static String regexTolerante(String termo) {
        StringBuilder regex = new StringBuilder();
        for (char c : semAcento(termo.trim()).replaceAll("\\s+", " ").toCharArray()) {
            String variantes = VARIANTES.get(c);
            if (variantes != null) {
                regex.append('[').append(variantes).append(variantes.toUpperCase(Locale.ROOT)).append(']');
            } else if (Character.isWhitespace(c)) {
                regex.append("\\s+");
            } else if (Character.isLetterOrDigit(c)) {
                regex.append(c);
            } else {
                regex.append('\\').append(c);
            }
        }
        return regex.toString();
    }

    public static boolean pareceCpf(String termo) {
        return SOMENTE_NUMERICO.matcher(termo).matches();
    }

    public static String regexCpf(String termo) {
        String digitos = termo.replaceAll("\\D", "");
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < digitos.length(); i++) {
            if (i > 0) {
                regex.append("\\D*");
            }
            regex.append(digitos.charAt(i));
        }
        return regex.toString();
    }
}

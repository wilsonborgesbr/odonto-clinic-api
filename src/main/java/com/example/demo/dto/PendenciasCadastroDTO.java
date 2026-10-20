package com.example.demo.dto;

import java.util.List;
import java.util.Map;

public record PendenciasCadastroDTO(
        int total,
        Map<String, Integer> porEntidade,
        List<Item> itens
) {
    public record Item(String entidade, String id, String nome, List<Campo> campos) {}

    public record Campo(String campo, String valor, String motivo) {}
}

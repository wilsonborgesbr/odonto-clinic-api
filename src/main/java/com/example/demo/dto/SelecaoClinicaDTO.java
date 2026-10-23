package com.example.demo.dto;

import java.util.List;

/** Resposta do login quando a mesma senha casa em mais de uma clínica. */
public record SelecaoClinicaDTO(String status, String ticket, List<ClinicaOpcao> clinicas) implements LoginResponse {

    public static final String STATUS = "SELECAO_CLINICA";

    public record ClinicaOpcao(String id, String codigo, String nome) {}

    public static SelecaoClinicaDTO de(String ticket, List<ClinicaOpcao> clinicas) {
        return new SelecaoClinicaDTO(STATUS, ticket, clinicas);
    }
}

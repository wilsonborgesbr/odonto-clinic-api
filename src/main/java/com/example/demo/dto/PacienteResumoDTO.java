package com.example.demo.dto;

import com.example.demo.model.Paciente;

public record PacienteResumoDTO(
        String id,
        String nomeCompleto
) {
    public PacienteResumoDTO(Paciente paciente) {
        this(paciente.getId(), paciente.getNomeCompleto());
    }
}

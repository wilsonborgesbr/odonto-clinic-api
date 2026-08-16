package com.example.demo.dto;

import java.time.LocalDateTime;

public record PacienteListagemDTO(
        String id,
        String nomeCompleto,
        String cpf,
        String email,
        String telefoneCelular,
        Boolean ativo,
        LocalDateTime createdAt
) {
    public PacienteListagemDTO(com.example.demo.model.Paciente paciente) {
        this(
                paciente.getId(),
                paciente.getNomeCompleto(),
                paciente.getCpf(),
                paciente.getEmail(),
                paciente.getTelefoneCelular(),
                paciente.getAtivo(),
                paciente.getCreatedAt()
        );
    }
}

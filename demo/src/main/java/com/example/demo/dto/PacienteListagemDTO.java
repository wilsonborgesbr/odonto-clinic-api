package com.example.demo.dto;

public record PacienteListagemDTO(
        String id,
        String nomeCompleto,
        String cpf,
        String email,
        String telefoneCelular,
        Boolean ativo
) {
    public PacienteListagemDTO(com.example.demo.model.Paciente paciente) {
        this(
                paciente.getId(),
                paciente.getNomeCompleto(),
                paciente.getCpf(),
                paciente.getEmail(),
                paciente.getTelefoneCelular(),
                paciente.getAtivo()
        );
    }
}

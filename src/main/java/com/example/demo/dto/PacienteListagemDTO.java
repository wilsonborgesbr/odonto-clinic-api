package com.example.demo.dto;

import com.example.demo.util.FusoBrasil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.Year;

public record PacienteListagemDTO(
        String id,
        String nomeCompleto,
        String cpf,
        String email,
        String telefoneCelular,
        Boolean ativo,
        LocalDateTime createdAt,
        String fotoUrl,
        String responsavelNome,
        boolean menorDeIdade
) {
    private static final int MAIORIDADE = 18;

    public PacienteListagemDTO(com.example.demo.model.Paciente paciente) {
        this(
                paciente.getId(),
                paciente.getNomeCompleto(),
                paciente.getCpf(),
                paciente.getEmail(),
                paciente.getTelefoneCelular(),
                paciente.getAtivo(),
                paciente.getCreatedAt(),
                paciente.getFotoUrl(),
                paciente.getResponsavelNome(),
                menorDeIdade(paciente.getDataNascimento(), FusoBrasil.hoje())
        );
    }

    static boolean menorDeIdade(LocalDate dataNascimento, LocalDate hoje) {
        if (dataNascimento == null) {
            return false;
        }
        LocalDate aniversario = dataNascimento.plusYears(MAIORIDADE);
        boolean nascidoEm29DeFevereiro = dataNascimento.getMonth() == Month.FEBRUARY
                && dataNascimento.getDayOfMonth() == 29;
        if (nascidoEm29DeFevereiro && !Year.isLeap(aniversario.getYear())) {
            aniversario = aniversario.plusDays(1);
        }
        return aniversario.isAfter(hoje);
    }
}

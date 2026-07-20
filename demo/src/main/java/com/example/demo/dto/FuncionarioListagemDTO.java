package com.example.demo.dto;

import com.example.demo.enums.CargoFuncionarioEnum;
import com.example.demo.model.Funcionario;

public record FuncionarioListagemDTO(
        String id,
        String nomeCompleto,
        String cpf,
        CargoFuncionarioEnum cargo,
        String email,
        String telefoneCelular,
        Boolean ativo
) {
    public FuncionarioListagemDTO(Funcionario funcionario) {
        this(
                funcionario.getId(),
                funcionario.getNomeCompleto(),
                funcionario.getCpf(),
                funcionario.getCargo(),
                funcionario.getEmail(),
                funcionario.getTelefoneCelular(),
                funcionario.getAtivo()
        );
    }
}

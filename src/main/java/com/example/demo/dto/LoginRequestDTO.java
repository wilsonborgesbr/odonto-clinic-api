package com.example.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequestDTO {

    /**
     * Código único da clínica onde o usuário está cadastrado ({@code Clinica.codigo}). Opcional: sem ele,
     * o login procura o e-mail em todas as clínicas, mas só depois de a senha casar.
     */
    private String clinicaCodigo;

    @NotBlank(message = "O email não pode estar vazio")
    @Email(message = "O formato do email deve ser válido")
    private String email;

    @NotBlank(message = "A senha não pode estar vazia")
    private String password;
}

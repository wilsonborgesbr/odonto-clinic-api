package com.example.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequestDTO {

    /**
     * Código único da clínica onde o usuário está cadastrado.
     * Ex.: "odontosocorro". Corresponde a {@code Clinica.codigo}.
     */
    @NotBlank(message = "O código da clínica é obrigatório")
    private String clinicaCodigo;

    @NotBlank(message = "O email não pode estar vazio")
    @Email(message = "O formato do email deve ser válido")
    private String email;

    @NotBlank(message = "A senha não pode estar vazia")
    private String password;
}

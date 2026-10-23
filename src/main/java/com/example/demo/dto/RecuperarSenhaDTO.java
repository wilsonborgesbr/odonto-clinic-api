package com.example.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RecuperarSenhaDTO {

    @NotBlank(message = "O email não pode estar vazio")
    @Email(message = "O formato do email deve ser válido")
    private String email;

    private String clinicaCodigo;
}

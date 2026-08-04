package com.example.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateProfileDTO {
    @NotBlank(message = "O nome é obrigatório")
    private String name;

    @Email(message = "E-mail inválido")
    private String email;
}

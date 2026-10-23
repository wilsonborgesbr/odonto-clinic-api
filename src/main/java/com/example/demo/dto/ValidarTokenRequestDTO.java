package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ValidarTokenRequestDTO {

    @NotBlank(message = "O token é obrigatório")
    private String token;

    @NotBlank(message = "O tipo do token é obrigatório")
    private String tipo;
}

package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConfirmarEmailDTO {

    @NotBlank(message = "O token é obrigatório")
    private String token;
}

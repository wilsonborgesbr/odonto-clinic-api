package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SelecionarClinicaRequestDTO {

    @NotBlank(message = "O ticket é obrigatório")
    private String ticket;

    @NotBlank(message = "A clínica é obrigatória")
    private String clinicaId;
}

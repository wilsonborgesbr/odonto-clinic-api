package com.example.demo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AtualizarPreferenciasDTO {

    @NotNull(message = "hideFinancialInfo é obrigatório")
    private Boolean hideFinancialInfo;
}

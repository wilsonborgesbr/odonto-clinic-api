package com.example.demo.dto;

import com.example.demo.model.UserPreferences;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** PATCH parcial: cada campo é opcional e só os enviados são atualizados. */
@Data
public class AtualizarPreferenciasDTO {

    private Boolean hideFinancialInfo;

    @Pattern(regexp = UserPreferences.THEME_REGEX, message = "theme deve ser light, dark ou system")
    private String theme;

    public boolean vazio() {
        return hideFinancialInfo == null && theme == null;
    }
}

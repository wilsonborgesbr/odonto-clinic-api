package com.example.demo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Preferências pessoais do usuário, embutidas em {@code User}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferences {

    @Builder.Default
    private Boolean hideFinancialInfo = false;
}

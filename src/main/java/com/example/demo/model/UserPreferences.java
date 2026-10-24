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

    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";
    public static final String THEME_SYSTEM = "system";
    public static final String THEME_REGEX = "light|dark|system";

    @Builder.Default
    private Boolean hideFinancialInfo = false;

    /** Documentos antigos não têm o campo: ausente vale {@code light}, sem migração. */
    @Builder.Default
    private String theme = THEME_LIGHT;

    public String getTheme() {
        return theme == null ? THEME_LIGHT : theme;
    }
}

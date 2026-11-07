package com.example.demo.enums;

public enum CondicaoDenteEnum {
    HIGIDO,
    CARIE,
    RESTAURACAO_SATISFATORIA,
    RESTAURACAO_INSATISFATORIA,
    AUSENTE,
    ENDODONTIA,
    PROTESE_FIXA,
    PROTESE_REMOVIVEL,
    IMPLANTE,
    FRATURA,
    DENTE_INCLUSO,
    EXTRACAO_INDICADA,
    SELANTE,

    // Condições legadas (compatibilidade com dados antigos)
    SAUDAVEL,
    CARIADO,
    RESTAURADO,
    COROA,
    FRATURADO,
    EM_TRATAMENTO,
    EXTRAIR;

    /** Condições em que marcar faces do dente faz sentido (espelha o Frontend). */
    public boolean admiteFaces() {
        return switch (this) {
            case CARIE, RESTAURACAO_SATISFATORIA, RESTAURACAO_INSATISFATORIA, FRATURA, SELANTE,
                 CARIADO, RESTAURADO, FRATURADO -> true;
            default -> false;
        };
    }
}

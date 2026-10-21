package com.example.demo.util;

import org.springframework.data.mongodb.core.query.Collation;

/**
 * Collation usada nas listagens ordenadas por nome: locale pt, strength 1 (ignora acento e caixa).
 * Só deve ser aplicada em consultas de listagem; não cria nem altera índices.
 */
public final class ColacaoPtBr {

    public static final String JSON = "{ 'locale': 'pt', 'strength': 1 }";

    public static final Collation COLLATION = Collation.of("pt").strength(Collation.ComparisonLevel.primary());

    private ColacaoPtBr() {}
}

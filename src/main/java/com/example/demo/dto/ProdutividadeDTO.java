package com.example.demo.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Resposta de {@code GET /api/dashboard/produtividade} (contrato em docs/contrato-grafico-produtividade.md, seção 9).
 * Valores: contagens como inteiro, dinheiro e percentuais como decimal com 2 casas; {@code null} = sem base.
 */
public record ProdutividadeDTO(
        String metrica,
        String periodo,
        String referencia,
        String rotuloPeriodo,
        String unidade,
        Number total,
        Number totalAnterior,
        BigDecimal variacaoPercentual,
        String textoVariacao,
        InadimplenciaHistorica inadimplenciaHistorica,
        List<Ponto> pontos,
        List<Integer> anosDisponiveis,
        List<String> metricasPermitidas) {

    public record Ponto(
            String chave,
            String rotulo,
            String rotuloSecundario,
            Number valor,
            Number anterior,
            boolean semExpediente,
            boolean futuro,
            Map<String, Object> detalhes) {}

    public record InadimplenciaHistorica(BigDecimal totalAtrasado, long quantidadeTitulos) {}
}

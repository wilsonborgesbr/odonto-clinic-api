package com.example.demo.enums;

/** As 9 métricas do gráfico de produtividade, na ordem do menu (Agenda, Pacientes, Financeiro). */
public enum MetricaProdutividadeEnum {

    CONSULTAS_REALIZADAS(UnidadeMetricaEnum.CONTAGEM, PermissaoEnum.AGENDAMENTOS),
    AGENDAMENTOS(UnidadeMetricaEnum.CONTAGEM, PermissaoEnum.AGENDAMENTOS),
    FALTAS(UnidadeMetricaEnum.CONTAGEM, PermissaoEnum.AGENDAMENTOS),
    COMPARECIMENTO(UnidadeMetricaEnum.PERCENTUAL, PermissaoEnum.AGENDAMENTOS),
    CANCELAMENTOS(UnidadeMetricaEnum.CONTAGEM, PermissaoEnum.AGENDAMENTOS),
    NOVOS_PACIENTES(UnidadeMetricaEnum.CONTAGEM, PermissaoEnum.PACIENTES),
    FATURAMENTO(UnidadeMetricaEnum.MOEDA, PermissaoEnum.AUDITORIA_FINANCEIRA),
    TICKET_MEDIO(UnidadeMetricaEnum.MOEDA, PermissaoEnum.AUDITORIA_FINANCEIRA),
    A_RECEBER(UnidadeMetricaEnum.MOEDA, PermissaoEnum.AUDITORIA_FINANCEIRA);

    private final UnidadeMetricaEnum unidade;
    private final PermissaoEnum permissao;

    MetricaProdutividadeEnum(UnidadeMetricaEnum unidade, PermissaoEnum permissao) {
        this.unidade = unidade;
        this.permissao = permissao;
    }

    public UnidadeMetricaEnum unidade() {
        return unidade;
    }

    public PermissaoEnum permissao() {
        return permissao;
    }

    public boolean daAgenda() {
        return permissao == PermissaoEnum.AGENDAMENTOS;
    }

    /**
     * Métricas de previsão (agenda marcada e vencimentos a receber): os pontos futuros têm valor real, o total é o do
     * período inteiro e a comparação é com o período anterior inteiro. As demais são fatos passados.
     */
    public boolean projetaFuturo() {
        return this == AGENDAMENTOS || this == A_RECEBER;
    }
}

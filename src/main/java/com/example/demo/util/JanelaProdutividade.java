package com.example.demo.util;

import com.example.demo.enums.PeriodoProdutividadeEnum;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Janela de tempo do gráfico de produtividade: o período escolhido (semana de segunda a domingo, mês, ano ou todos),
 * os pontos (buckets) do eixo X, o corte "até hoje" e o período anterior no mesmo intervalo. Cálculo puro, em
 * calendário de São Paulo; quem chama passa o "hoje".
 */
public final class JanelaProdutividade {

    public enum Granularidade { DIA, MES, ANO }

    public record Bucket(LocalDate inicio, LocalDate fim, String chave, String rotulo, String rotuloSecundario) {
        public boolean futuro(LocalDate hoje) {
            return inicio.isAfter(hoje);
        }
    }

    private static final String[] DIAS_SEMANA = {"seg", "ter", "qua", "qui", "sex", "sáb", "dom"};
    private static final String[] MESES_CURTOS = {"jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"};
    private static final String[] MESES_ROTULO = {"Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez"};
    private static final String[] MESES_LONGOS = {"Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto",
            "Setembro", "Outubro", "Novembro", "Dezembro"};

    private final PeriodoProdutividadeEnum periodo;
    private final LocalDate inicio;
    private final LocalDate fim;
    private final LocalDate corte;
    private final boolean semCorte;
    private final LocalDate hoje;
    private final Granularidade granularidade;
    private final List<Bucket> buckets;
    private final JanelaProdutividade anterior;

    private JanelaProdutividade(PeriodoProdutividadeEnum periodo, LocalDate inicio, LocalDate fim, LocalDate corte,
                                boolean semCorte, LocalDate hoje, Granularidade granularidade, List<Bucket> buckets,
                                JanelaProdutividade anterior) {
        this.periodo = periodo;
        this.inicio = inicio;
        this.fim = fim;
        this.corte = corte;
        this.semCorte = semCorte;
        this.hoje = hoje;
        this.granularidade = granularidade;
        this.buckets = buckets;
        this.anterior = anterior;
    }

    /**
     * @param semCorte {@code true} para métrica que projeta o futuro (A receber): o intervalo vai até o fim do período
     *                 e a comparação é com o período anterior inteiro
     * @param primeiroAno e {@code ultimoAno}: anos do eixo X de "Todos" (ignorados nos demais períodos)
     */
    public static JanelaProdutividade de(PeriodoProdutividadeEnum periodo, LocalDate referencia, LocalDate hoje,
                                         boolean semCorte, int primeiroAno, int ultimoAno) {
        JanelaProdutividade atual = montar(periodo, referencia, hoje, semCorte, primeiroAno, ultimoAno);
        if (periodo == PeriodoProdutividadeEnum.TODOS) {
            return atual;
        }
        LocalDate refAnterior = switch (periodo) {
            case SEMANA -> atual.inicio.minusWeeks(1);
            case MES -> atual.inicio.minusMonths(1);
            default -> atual.inicio.minusYears(1);
        };
        JanelaProdutividade base = montar(periodo, refAnterior, hoje, true, primeiroAno, ultimoAno);
        LocalDate corteAnterior;
        if (semCorte || atual.corte.equals(atual.fim)) {
            corteAnterior = base.fim;
        } else {
            long passados = ChronoUnit.DAYS.between(atual.inicio, atual.corte);
            LocalDate alinhado = base.inicio.plusDays(passados);
            corteAnterior = alinhado.isAfter(base.fim) ? base.fim : alinhado;
        }
        JanelaProdutividade anterior = new JanelaProdutividade(base.periodo, base.inicio, base.fim, corteAnterior, false,
                hoje, base.granularidade, base.buckets, null);
        return new JanelaProdutividade(atual.periodo, atual.inicio, atual.fim, atual.corte, atual.semCorte, hoje,
                atual.granularidade, atual.buckets, anterior);
    }

    private static JanelaProdutividade montar(PeriodoProdutividadeEnum periodo, LocalDate referencia, LocalDate hoje,
                                              boolean semCorte, int primeiroAno, int ultimoAno) {
        LocalDate inicio;
        LocalDate fim;
        Granularidade granularidade;
        switch (periodo) {
            case SEMANA -> {
                inicio = referencia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                fim = inicio.plusDays(6);
                granularidade = Granularidade.DIA;
            }
            case MES -> {
                YearMonth mes = YearMonth.from(referencia);
                inicio = mes.atDay(1);
                fim = mes.atEndOfMonth();
                granularidade = Granularidade.DIA;
            }
            case ANO -> {
                inicio = LocalDate.of(referencia.getYear(), 1, 1);
                fim = LocalDate.of(referencia.getYear(), 12, 31);
                granularidade = Granularidade.MES;
            }
            default -> {
                inicio = LocalDate.of(primeiroAno, 1, 1);
                fim = LocalDate.of(ultimoAno, 12, 31);
                granularidade = Granularidade.ANO;
            }
        }
        LocalDate corte = semCorte ? fim : (hoje.isAfter(fim) ? fim : hoje);
        if (corte.isBefore(inicio.minusDays(1))) {
            corte = inicio.minusDays(1);
        }
        return new JanelaProdutividade(periodo, inicio, fim, corte, semCorte, hoje, granularidade,
                criarBuckets(periodo, granularidade, inicio, fim), null);
    }

    private static List<Bucket> criarBuckets(PeriodoProdutividadeEnum periodo, Granularidade granularidade, LocalDate inicio, LocalDate fim) {
        List<Bucket> buckets = new ArrayList<>();
        switch (granularidade) {
            case DIA -> {
                for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
                    String diaDaSemana = DIAS_SEMANA[dia.getDayOfWeek().getValue() - 1];
                    String numero = String.valueOf(dia.getDayOfMonth());
                    if (periodo == PeriodoProdutividadeEnum.SEMANA) {
                        buckets.add(new Bucket(dia, dia, dia.toString(), Character.toUpperCase(diaDaSemana.charAt(0)) + diaDaSemana.substring(1), numero));
                    } else {
                        buckets.add(new Bucket(dia, dia, dia.toString(), numero, diaDaSemana));
                    }
                }
            }
            case MES -> {
                for (int m = 1; m <= 12; m++) {
                    LocalDate primeiro = LocalDate.of(inicio.getYear(), m, 1);
                    buckets.add(new Bucket(primeiro, YearMonth.from(primeiro).atEndOfMonth(), primeiro.toString(),
                            MESES_ROTULO[m - 1], null));
                }
            }
            case ANO -> {
                for (int ano = inicio.getYear(); ano <= fim.getYear(); ano++) {
                    buckets.add(new Bucket(LocalDate.of(ano, 1, 1), LocalDate.of(ano, 12, 31),
                            LocalDate.of(ano, 1, 1).toString(), String.valueOf(ano), null));
                }
            }
        }
        return buckets;
    }

    public PeriodoProdutividadeEnum periodo() { return periodo; }

    public LocalDate inicio() { return inicio; }

    public LocalDate fim() { return fim; }

    /** Último dia já decorrido (inclusive); um dia antes do início quando nada decorreu. */
    public LocalDate corte() { return corte; }

    public Granularidade granularidade() { return granularidade; }

    public List<Bucket> buckets() { return buckets; }

    /** Período anterior no mesmo intervalo; {@code null} em "Todos". */
    public JanelaProdutividade anterior() { return anterior; }

    public boolean semDecorrido() { return corte.isBefore(inicio); }

    public String rotuloPeriodo() {
        return switch (periodo) {
            case SEMANA -> {
                String mesInicio = MESES_LONGOS[inicio.getMonthValue() - 1].toLowerCase(Locale.ROOT);
                String mesFim = MESES_LONGOS[fim.getMonthValue() - 1].toLowerCase(Locale.ROOT);
                if (inicio.getYear() != fim.getYear()) {
                    yield inicio.getDayOfMonth() + " de " + mesInicio + " de " + inicio.getYear() + " a "
                            + fim.getDayOfMonth() + " de " + mesFim + " de " + fim.getYear();
                }
                yield inicio.getMonth() == fim.getMonth()
                        ? inicio.getDayOfMonth() + " a " + fim.getDayOfMonth() + " de " + mesFim + " de " + fim.getYear()
                        : inicio.getDayOfMonth() + " de " + mesInicio + " a " + fim.getDayOfMonth() + " de " + mesFim + " de " + fim.getYear();
            }
            case MES -> MESES_LONGOS[inicio.getMonthValue() - 1] + " de " + inicio.getYear();
            case ANO -> String.valueOf(inicio.getYear());
            case TODOS -> "Todos os anos";
        };
    }

    /** Texto da comparação; {@code null} em "Todos" (sem período anterior). */
    public String textoVariacao() {
        if (anterior == null) {
            return null;
        }
        String base = switch (periodo) {
            case SEMANA -> "vs semana anterior";
            case MES -> "vs mês anterior";
            default -> "vs ano anterior";
        };
        if (semCorte) {
            return hoje.isBefore(fim) ? base + " (período inteiro)" : base;
        }
        if (!corte.isBefore(fim) || semDecorrido()) {
            return base;
        }
        String intervalo = switch (periodo) {
            case SEMANA -> DIAS_SEMANA[inicio.getDayOfWeek().getValue() - 1] + " a " + DIAS_SEMANA[corte.getDayOfWeek().getValue() - 1];
            case MES -> "dias " + inicio.getDayOfMonth() + " a " + corte.getDayOfMonth();
            default -> "1 jan a " + corte.getDayOfMonth() + " " + MESES_CURTOS[corte.getMonthValue() - 1];
        };
        return base + " (mesmo intervalo: " + intervalo + ")";
    }

}

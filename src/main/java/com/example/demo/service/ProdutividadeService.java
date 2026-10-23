package com.example.demo.service;

import com.example.demo.dto.ProdutividadeDTO;
import com.example.demo.dto.ProdutividadeDTO.InadimplenciaHistorica;
import com.example.demo.dto.ProdutividadeDTO.Ponto;
import com.example.demo.enums.MetricaProdutividadeEnum;
import com.example.demo.enums.PeriodoProdutividadeEnum;
import com.example.demo.enums.PermissaoEnum;
import com.example.demo.enums.StatusAgendamentoEnum;
import com.example.demo.enums.UnidadeMetricaEnum;
import com.example.demo.exception.CampoInvalidoException;
import com.example.demo.model.User;
import com.example.demo.repository.DentistaRepository;
import com.example.demo.security.AuthContextHelper;
import com.example.demo.service.AgregacaoDiariaService.ResumoPagamentos;
import com.example.demo.service.AgregacaoDiariaService.SaldoAberto;
import com.example.demo.util.FusoBrasil;
import com.example.demo.util.JanelaProdutividade;
import com.example.demo.util.JanelaProdutividade.Bucket;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Gráfico de produtividade do painel (docs/contrato-grafico-produtividade.md). Autoriza por métrica com as
 * permissões efetivas do usuário, monta a janela de tempo e calcula as 9 métricas sobre as agregações do Mongo.
 */
@Service
public class ProdutividadeService {

    private static final Pattern DIA_ISO = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private final AgregacaoDiariaService agregacao;
    private final DentistaRepository dentistaRepository;

    /** Só o instante importa: "hoje" e "agora" são sempre convertidos para São Paulo. Trocável nos testes. */
    private Clock relogio = Clock.systemUTC();

    public ProdutividadeService(AgregacaoDiariaService agregacao, DentistaRepository dentistaRepository) {
        this.agregacao = agregacao;
        this.dentistaRepository = dentistaRepository;
    }

    /** Resultado de uma janela: um valor e um detalhe por ponto (na ordem dos buckets) e o total do intervalo. */
    private record Calculo(List<Number> valores, List<Map<String, Object>> detalhes, Number total) {}

    public ProdutividadeDTO consultar(String metricaTexto, String periodoTexto, String referenciaTexto, String dentistaId) {
        User usuario = AuthContextHelper.currentUser();
        String clinicaId = AuthContextHelper.currentClinicaId();

        MetricaProdutividadeEnum metrica = lerMetrica(metricaTexto);
        Set<PermissaoEnum> permissoes = usuario.permissoesEfetivas();
        if (!permissoes.contains(metrica.permissao())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sem permissão para ver esta métrica.");
        }
        PeriodoProdutividadeEnum periodo = lerPeriodo(periodoTexto);
        LocalDate hoje = FusoBrasil.hoje(relogio);
        LocalDate referencia = lerReferencia(referenciaTexto, hoje);
        String dentista = dentistaDaAgenda(metrica, dentistaId, clinicaId);

        boolean projeta = metrica.projetaFuturo();
        boolean aReceber = metrica == MetricaProdutividadeEnum.A_RECEBER;
        List<Integer> anos = anosDisponiveis(metrica, clinicaId, dentista, hoje);
        JanelaProdutividade janela = JanelaProdutividade.de(periodo, referencia, hoje, projeta,
                anos.get(0), anos.get(anos.size() - 1));
        LocalDateTime agora = LocalDateTime.now(relogio.withZone(FusoBrasil.ZONA));

        Calculo atual = calcular(metrica, clinicaId, dentista, janela, hoje, agora);
        List<Boolean> semExpediente = aplicarSemExpediente(atual, janela, clinicaId);
        Calculo anterior = null;
        if (janela.anterior() != null) {
            anterior = calcular(metrica, clinicaId, dentista, janela.anterior(), hoje, agora);
            aplicarSemExpediente(anterior, janela.anterior(), clinicaId);
        }
        Number totalAnterior = anterior == null ? null : anterior.total();

        List<Ponto> pontos = new ArrayList<>();
        List<Bucket> buckets = janela.buckets();
        for (int i = 0; i < buckets.size(); i++) {
            Bucket bucket = buckets.get(i);
            Number valorAnterior = anterior != null && i < anterior.valores().size() ? anterior.valores().get(i) : null;
            pontos.add(new Ponto(bucket.chave(), bucket.rotulo(), bucket.rotuloSecundario(), atual.valores().get(i),
                    valorAnterior, semExpediente.get(i), bucket.futuro(hoje), atual.detalhes().get(i)));
        }

        InadimplenciaHistorica inadimplencia = null;
        if (aReceber) {
            SaldoAberto vencido = agregacao.saldoEmAbertoVencidoAntesDe(clinicaId, janela.inicio());
            inadimplencia = new InadimplenciaHistorica(vencido.valor(), vencido.titulos());
        }

        return new ProdutividadeDTO(metrica.name(), periodo.name(), referencia.toString(), janela.rotuloPeriodo(),
                metrica.unidade().name(), atual.total(), totalAnterior,
                variacao(metrica.unidade(), atual.total(), totalAnterior), janela.textoVariacao(), inadimplencia, pontos, anos,
                Arrays.stream(MetricaProdutividadeEnum.values()).filter(m -> permissoes.contains(m.permissao()))
                        .map(Enum::name).collect(Collectors.toList()));
    }

    /**
     * Nos períodos diários, dia em que a clínica não teve nenhuma movimentação (agendamento, recebimento, cadastro ou
     * vencimento) fica sem valor e sem detalhes e marcado como sem expediente, em qualquer uma das 9 métricas. Nos
     * períodos por mês ou por ano a marca é sempre falsa. Devolve a marca de cada ponto.
     */
    private List<Boolean> aplicarSemExpediente(Calculo calculo, JanelaProdutividade janela, String clinicaId) {
        List<Bucket> buckets = janela.buckets();
        List<Boolean> marcas = new ArrayList<>(buckets.size());
        Set<LocalDate> comMovimento = janela.granularidade() == JanelaProdutividade.Granularidade.DIA
                ? agregacao.diasComMovimento(clinicaId, janela.inicio(), janela.fim()) : null;
        for (int i = 0; i < buckets.size(); i++) {
            boolean sem = comMovimento != null && !comMovimento.contains(buckets.get(i).inicio());
            if (sem) {
                calculo.valores().set(i, null);
                calculo.detalhes().set(i, null);
            }
            marcas.add(sem);
        }
        return marcas;
    }

    private Calculo calcular(MetricaProdutividadeEnum metrica, String clinicaId, String dentistaId, JanelaProdutividade janela,
                             LocalDate hoje, LocalDateTime agora) {
        return switch (metrica) {
            case CONSULTAS_REALIZADAS, AGENDAMENTOS, FALTAS, COMPARECIMENTO, CANCELAMENTOS ->
                    calcularAgenda(metrica, clinicaId, dentistaId, janela, agora);
            case NOVOS_PACIENTES -> calcularNovosPacientes(clinicaId, janela);
            case FATURAMENTO, TICKET_MEDIO -> calcularPagamentos(metrica, clinicaId, janela);
            case A_RECEBER -> calcularAReceber(clinicaId, janela, hoje);
        };
    }

    private Calculo calcularAgenda(MetricaProdutividadeEnum metrica, String clinicaId, String dentistaId,
                                   JanelaProdutividade janela, LocalDateTime agora) {
        TreeMap<LocalDate, Map<StatusAgendamentoEnum, Long>> diario = new TreeMap<>();
        if (!janela.semDecorrido()) {
            LocalDateTime fimExclusivo = janela.corte().plusDays(1).atStartOfDay();
            if (metrica == MetricaProdutividadeEnum.COMPARECIMENTO) {
                LocalDateTime jaPassado = agora.truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);
                fimExclusivo = jaPassado.isBefore(fimExclusivo) ? jaPassado : fimExclusivo;
            }
            diario.putAll(agregacao.contarAgendamentosPorDiaEStatus(clinicaId, janela.inicio().atStartOfDay(), fimExclusivo, dentistaId));
        }

        List<Number> valores = new ArrayList<>();
        List<Map<String, Object>> detalhes = new ArrayList<>();
        for (Bucket bucket : janela.buckets()) {
            if (bucket.inicio().isAfter(janela.corte())) {
                valores.add(null);
                detalhes.add(null);
                continue;
            }
            Contagem c = Contagem.de(diario.subMap(bucket.inicio(), true, bucket.fim(), true).values());
            valores.add(valorAgenda(metrica, c));
            detalhes.add(detalhesAgenda(metrica, c));
        }
        return new Calculo(valores, detalhes, valorAgenda(metrica, Contagem.de(diario.values())));
    }

    private static Number valorAgenda(MetricaProdutividadeEnum metrica, Contagem c) {
        return switch (metrica) {
            case CONSULTAS_REALIZADAS -> c.realizadas;
            case AGENDAMENTOS -> c.total();
            case FALTAS -> c.faltas;
            case CANCELAMENTOS -> c.canceladas;
            case COMPARECIMENTO -> c.realizadas + c.faltas == 0 ? null
                    : BigDecimal.valueOf(c.realizadas).multiply(CEM).divide(BigDecimal.valueOf(c.realizadas + c.faltas), 2, RoundingMode.HALF_UP);
            default -> throw new IllegalStateException("Métrica fora da agenda: " + metrica);
        };
    }

    private static Map<String, Object> detalhesAgenda(MetricaProdutividadeEnum metrica, Contagem c) {
        Map<String, Object> detalhes = new LinkedHashMap<>();
        switch (metrica) {
            case CONSULTAS_REALIZADAS, FALTAS, CANCELAMENTOS -> detalhes.put("agendamentos", c.total());
            case AGENDAMENTOS -> {
                detalhes.put("realizadas", c.realizadas);
                detalhes.put("faltas", c.faltas);
                detalhes.put("canceladas", c.canceladas);
                detalhes.put("agendadas", c.agendadas);
            }
            case COMPARECIMENTO -> {
                detalhes.put("realizadas", c.realizadas);
                detalhes.put("faltas", c.faltas);
                detalhes.put("previstos", c.realizadas + c.faltas);
            }
            default -> throw new IllegalStateException("Métrica fora da agenda: " + metrica);
        }
        return detalhes;
    }

    /** Contagem de agendamentos por situação; "agendadas" = ainda em aberto (AGENDADO e CONFIRMADO). */
    private static final class Contagem {
        long realizadas;
        long faltas;
        long canceladas;
        long agendadas;

        static Contagem de(Iterable<Map<StatusAgendamentoEnum, Long>> dias) {
            Contagem c = new Contagem();
            for (Map<StatusAgendamentoEnum, Long> dia : dias) {
                c.realizadas += dia.getOrDefault(StatusAgendamentoEnum.REALIZADO, 0L);
                c.faltas += dia.getOrDefault(StatusAgendamentoEnum.FALTOU, 0L);
                c.canceladas += dia.getOrDefault(StatusAgendamentoEnum.CANCELADO, 0L);
                c.agendadas += dia.getOrDefault(StatusAgendamentoEnum.AGENDADO, 0L) + dia.getOrDefault(StatusAgendamentoEnum.CONFIRMADO, 0L);
            }
            return c;
        }

        long total() {
            return realizadas + faltas + canceladas + agendadas;
        }
    }

    private Calculo calcularNovosPacientes(String clinicaId, JanelaProdutividade janela) {
        TreeMap<LocalDate, Long> diario = new TreeMap<>();
        if (!janela.semDecorrido()) {
            diario.putAll(agregacao.contarNovosPacientesPorDia(clinicaId, janela.inicio(), janela.corte()));
        }
        List<Number> valores = new ArrayList<>();
        List<Map<String, Object>> detalhes = new ArrayList<>();
        for (Bucket bucket : janela.buckets()) {
            if (bucket.inicio().isAfter(janela.corte())) {
                valores.add(null);
                detalhes.add(null);
            } else {
                valores.add(diario.subMap(bucket.inicio(), true, bucket.fim(), true).values().stream().mapToLong(Long::longValue).sum());
                detalhes.add(Map.of());
            }
        }
        return new Calculo(valores, detalhes, diario.values().stream().mapToLong(Long::longValue).sum());
    }

    private Calculo calcularPagamentos(MetricaProdutividadeEnum metrica, String clinicaId, JanelaProdutividade janela) {
        boolean ticket = metrica == MetricaProdutividadeEnum.TICKET_MEDIO;
        Map<String, ResumoPagamentos> porBucket = janela.semDecorrido() ? Map.of()
                : agregacao.resumirPagamentos(clinicaId, janela.inicio(), janela.corte(), janela.granularidade());

        List<Number> valores = new ArrayList<>();
        List<Map<String, Object>> detalhes = new ArrayList<>();
        BigDecimal faturamentoTotal = BigDecimal.ZERO.setScale(2);
        for (Bucket bucket : janela.buckets()) {
            if (bucket.inicio().isAfter(janela.corte())) {
                valores.add(null);
                detalhes.add(null);
                continue;
            }
            ResumoPagamentos resumo = porBucket.get(AgregacaoDiariaService.chaveDoBucket(janela.granularidade(), bucket.inicio()));
            BigDecimal valor = resumo == null ? BigDecimal.ZERO.setScale(2) : resumo.valor();
            faturamentoTotal = faturamentoTotal.add(valor);
            Map<String, Object> detalhe = new LinkedHashMap<>();
            if (ticket) {
                long pacientes = resumo == null ? 0 : resumo.pacientes();
                valores.add(media(valor, pacientes));
                detalhe.put("pacientesPagantes", pacientes);
            } else {
                valores.add(valor);
                detalhe.put("recebimentos", resumo == null ? 0L : resumo.recebimentos());
            }
            detalhes.add(detalhe);
        }
        if (!ticket) {
            return new Calculo(valores, detalhes, faturamentoTotal);
        }
        ResumoPagamentos periodoInteiro = janela.semDecorrido() ? null
                : agregacao.resumirPagamentos(clinicaId, janela.inicio(), janela.corte(), null).values().stream().findFirst().orElse(null);
        return new Calculo(valores, detalhes, periodoInteiro == null ? null : media(periodoInteiro.valor(), periodoInteiro.pacientes()));
    }

    private static BigDecimal media(BigDecimal valor, long pacientes) {
        return pacientes == 0 ? null : valor.divide(BigDecimal.valueOf(pacientes), 2, RoundingMode.HALF_UP);
    }

    private Calculo calcularAReceber(String clinicaId, JanelaProdutividade janela, LocalDate hoje) {
        TreeMap<LocalDate, SaldoAberto> diario = new TreeMap<>(agregacao.somarAReceberPorDia(clinicaId, janela.inicio(), janela.fim()));
        List<Number> valores = new ArrayList<>();
        List<Map<String, Object>> detalhes = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (Bucket bucket : janela.buckets()) {
            BigDecimal valor = BigDecimal.ZERO.setScale(2);
            BigDecimal atrasado = BigDecimal.ZERO.setScale(2);
            for (Map.Entry<LocalDate, SaldoAberto> dia : diario.subMap(bucket.inicio(), true, bucket.fim(), true).entrySet()) {
                valor = valor.add(dia.getValue().valor());
                if (dia.getKey().isBefore(hoje)) {
                    atrasado = atrasado.add(dia.getValue().valor());
                }
            }
            total = total.add(valor);
            valores.add(valor);
            Map<String, Object> detalhe = new LinkedHashMap<>();
            detalhe.put("atrasado", atrasado);
            detalhes.add(detalhe);
        }
        return new Calculo(valores, detalhes, total);
    }

    /**
     * Variação sobre o mesmo intervalo do período anterior: em pontos percentuais para a taxa de comparecimento, em
     * porcentagem relativa (uma casa) para as demais. {@code null} quando falta um dos lados ou o anterior é zero.
     */
    static BigDecimal variacao(UnidadeMetricaEnum unidade, Number total, Number anterior) {
        if (total == null || anterior == null) {
            return null;
        }
        BigDecimal atual = paraDecimal(total);
        BigDecimal base = paraDecimal(anterior);
        if (unidade == UnidadeMetricaEnum.PERCENTUAL) {
            return atual.subtract(base).setScale(1, RoundingMode.HALF_UP);
        }
        if (base.signum() == 0) {
            return null;
        }
        return atual.subtract(base).multiply(CEM).divide(base, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal paraDecimal(Number numero) {
        return numero instanceof BigDecimal d ? d : BigDecimal.valueOf(numero.doubleValue());
    }

    private List<Integer> anosDisponiveis(MetricaProdutividadeEnum metrica, String clinicaId, String dentistaId, LocalDate hoje) {
        int[] faixa = switch (metrica) {
            case CONSULTAS_REALIZADAS, AGENDAMENTOS, FALTAS, COMPARECIMENTO, CANCELAMENTOS ->
                    agregacao.faixaDeAnosAgendamentos(clinicaId, dentistaId);
            case NOVOS_PACIENTES -> agregacao.faixaDeAnosNovosPacientes(clinicaId);
            case FATURAMENTO, TICKET_MEDIO -> agregacao.faixaDeAnosPagamentos(clinicaId);
            case A_RECEBER -> agregacao.faixaDeAnosAReceber(clinicaId);
        };
        int primeiro = hoje.getYear();
        int ultimo = hoje.getYear();
        if (faixa != null) {
            primeiro = Math.min(primeiro, faixa[0]);
            // só agenda marcada e vencimentos a receber têm anos futuros navegáveis; as demais param no ano corrente
            ultimo = metrica.projetaFuturo() ? Math.max(ultimo, faixa[1]) : ultimo;
        }
        List<Integer> anos = new ArrayList<>();
        for (int ano = primeiro; ano <= ultimo; ano++) {
            anos.add(ano);
        }
        return anos;
    }

    private String dentistaDaAgenda(MetricaProdutividadeEnum metrica, String dentistaId, String clinicaId) {
        if (!metrica.daAgenda() || dentistaId == null || dentistaId.isBlank()) {
            return null;
        }
        String id = dentistaId.trim();
        dentistaRepository.findByIdAndClinicaId(id, clinicaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dentista não encontrado."));
        return id;
    }

    private static MetricaProdutividadeEnum lerMetrica(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new CampoInvalidoException("metrica", "Informe a métrica.");
        }
        try {
            return MetricaProdutividadeEnum.valueOf(texto.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CampoInvalidoException("metrica", "Métrica inválida. Use uma de: "
                    + Arrays.stream(MetricaProdutividadeEnum.values()).map(Enum::name).collect(Collectors.joining(", ")) + ".");
        }
    }

    private static PeriodoProdutividadeEnum lerPeriodo(String texto) {
        if (texto == null || texto.isBlank()) {
            return PeriodoProdutividadeEnum.MES;
        }
        try {
            return PeriodoProdutividadeEnum.valueOf(texto.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CampoInvalidoException("periodo", "Período inválido. Use SEMANA, MES, ANO ou TODOS.");
        }
    }

    private static LocalDate lerReferencia(String texto, LocalDate hoje) {
        if (texto == null || texto.isBlank()) {
            return hoje;
        }
        String valor = texto.trim();
        String mensagem = "Data de referência inválida. Use o formato AAAA-MM-DD.";
        if (!DIA_ISO.matcher(valor).matches()) {
            throw new CampoInvalidoException("referencia", mensagem);
        }
        try {
            LocalDate data = LocalDate.parse(valor);
            if (data.getYear() < 1900 || data.getYear() > 2200) {
                throw new CampoInvalidoException("referencia", mensagem);
            }
            return data;
        } catch (DateTimeParseException e) {
            throw new CampoInvalidoException("referencia", mensagem);
        }
    }
}

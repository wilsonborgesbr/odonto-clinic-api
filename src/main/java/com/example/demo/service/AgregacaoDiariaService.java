package com.example.demo.service;

import com.example.demo.enums.StatusAgendamentoEnum;
import com.example.demo.enums.StatusFinanceiroEnum;
import com.example.demo.model.Agendamento;
import com.example.demo.model.ContaReceber;
import com.example.demo.model.Paciente;
import com.example.demo.util.FusoBrasil;
import com.example.demo.util.FusoGravacao;
import com.example.demo.util.JanelaProdutividade.Granularidade;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.DateOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Base dos gráficos do painel: agregações do Mongo que devolvem séries por dia (ou por mês/ano, quando a métrica não
 * pode ser somada), que o chamador agrupa em Java. Todas as consultas começam por clinicaId.
 *
 * <p>Fusos: campos de horário de parede ou data de negócio (dataHoraInicio, dataVencimento, dataPagamento) são
 * agrupados no fuso de gravação da JVM ({@link FusoGravacao}), por isso dão o mesmo dia visto na tela na VPS (UTC) e no
 * ambiente local (São Paulo). Carimbos gerados pelo sistema (createdAt) são instantes reais e são agrupados em
 * America/Sao_Paulo, com fronteiras em instantes.
 */
@Service
public class AgregacaoDiariaService {

    private static final String FORMATO_DIA = "%Y-%m-%d";
    private static final String FORMATO_MES = "%Y-%m";
    private static final String FORMATO_ANO = "%Y";
    private static final String CHAVE_TOTAL = "total";
    private static final List<String> STATUS_EM_ABERTO = List.of(
            StatusFinanceiroEnum.PENDENTE.name(), StatusFinanceiroEnum.ATRASADO.name());

    /** Faturamento, quantidade de recebimentos e pacientes distintos que pagaram em um bucket. */
    public record ResumoPagamentos(BigDecimal valor, long recebimentos, long pacientes) {}

    /** Saldo em aberto (valorTotal - valorPago) e quantidade de títulos com saldo. */
    public record SaldoAberto(BigDecimal valor, long titulos) {}

    private final MongoTemplate mongoTemplate;

    public AgregacaoDiariaService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** Agendamentos por dia de início; {@code de} e {@code ate} inclusivos. Dias sem registro não aparecem. */
    public Map<LocalDate, Long> contarAgendamentosPorDia(String clinicaId, LocalDate de, LocalDate ate,
                                                         Collection<StatusAgendamentoEnum> status, String dentistaId) {
        Criteria criterio = Criteria.where("clinicaId").is(clinicaId)
                .and("dataHoraInicio").gte(de.atStartOfDay()).lt(ate.plusDays(1).atStartOfDay());
        if (status != null && !status.isEmpty()) {
            criterio = criterio.and("status").in(status);
        }
        if (dentistaId != null) {
            criterio = criterio.and("dentistaId").is(dentistaId);
        }

        Aggregation agregacao = Aggregation.newAggregation(Agendamento.class,
                Aggregation.match(criterio),
                Aggregation.project().and(diaDe("dataHoraInicio")).as("dia"),
                Aggregation.group("dia").count().as("total"));

        Map<LocalDate, Long> porDia = new TreeMap<>();
        for (Document linha : mongoTemplate.aggregate(agregacao, Agendamento.class, Document.class).getMappedResults()) {
            porDia.put(LocalDate.parse(linha.getString("_id")), ((Number) linha.get("total")).longValue());
        }
        return porDia;
    }

    /** Receita em regime de caixa: contas PAGO somadas no dia de dataPagamento; {@code de} e {@code ate} inclusivos. */
    public Map<LocalDate, BigDecimal> somarReceitaPorDia(String clinicaId, LocalDate de, LocalDate ate) {
        Map<LocalDate, BigDecimal> porDia = new TreeMap<>();
        resumirPagamentos(clinicaId, de, ate, Granularidade.DIA)
                .forEach((chave, resumo) -> porDia.put(LocalDate.parse(chave), resumo.valor()));
        return porDia;
    }

    /**
     * Agendamentos por dia de início e status. {@code inicio} é inclusivo e {@code fimExclusivo} exclusivo, em horário de
     * parede de São Paulo; {@code dentistaId} é opcional.
     */
    public Map<LocalDate, Map<StatusAgendamentoEnum, Long>> contarAgendamentosPorDiaEStatus(
            String clinicaId, LocalDateTime inicio, LocalDateTime fimExclusivo, String dentistaId) {
        Document filtro = new Document("clinicaId", clinicaId)
                .append("dataHoraInicio", new Document("$gte", paraData(inicio)).append("$lt", paraData(fimExclusivo)));
        if (dentistaId != null) {
            filtro.append("dentistaId", dentistaId);
        }
        List<Document> pipeline = List.of(
                new Document("$match", filtro),
                new Document("$group", new Document("_id", new Document("dia", paraTexto("dataHoraInicio", FusoGravacao.idMongo(), FORMATO_DIA))
                        .append("status", "$status")).append("total", new Document("$sum", 1))));

        Map<LocalDate, Map<StatusAgendamentoEnum, Long>> porDia = new TreeMap<>();
        for (Document linha : executar(Agendamento.class, pipeline)) {
            Document id = linha.get("_id", Document.class);
            if (id.getString("status") == null) {
                continue;
            }
            porDia.computeIfAbsent(LocalDate.parse(id.getString("dia")), d -> new EnumMap<>(StatusAgendamentoEnum.class))
                    .merge(StatusAgendamentoEnum.valueOf(id.getString("status")), ((Number) linha.get("total")).longValue(), Long::sum);
        }
        return porDia;
    }

    /**
     * Pacientes cadastrados por dia de createdAt (ativos e inativos), no dia de São Paulo. createdAt é um instante
     * real, então as fronteiras também são instantes; {@code de} e {@code ate} inclusivos.
     */
    public Map<LocalDate, Long> contarNovosPacientesPorDia(String clinicaId, LocalDate de, LocalDate ate) {
        Document filtro = new Document("clinicaId", clinicaId).append("createdAt", new Document(
                "$gte", Date.from(de.atStartOfDay(FusoBrasil.ZONA).toInstant()))
                .append("$lt", Date.from(ate.plusDays(1).atStartOfDay(FusoBrasil.ZONA).toInstant())));
        List<Document> pipeline = List.of(
                new Document("$match", filtro),
                new Document("$group", new Document("_id", paraTexto("createdAt", FusoBrasil.ZONA.getId(), FORMATO_DIA))
                        .append("total", new Document("$sum", 1))));

        Map<LocalDate, Long> porDia = new TreeMap<>();
        for (Document linha : executar(Paciente.class, pipeline)) {
            porDia.put(LocalDate.parse(linha.getString("_id")), ((Number) linha.get("total")).longValue());
        }
        return porDia;
    }

    /**
     * Recebimentos (contas PAGO, valorPago ou valorTotal na falta dele) por dataPagamento, agrupados em dia, mês ou ano,
     * com pacientes distintos por bucket (distintos não somam de um bucket para o seguinte). Com {@code granularidade}
     * nula devolve um único bucket {@value #CHAVE_TOTAL} com o período inteiro. Chaves: {@code AAAA-MM-DD},
     * {@code AAAA-MM} ou {@code AAAA}. {@code de} e {@code ate} inclusivos.
     */
    public Map<String, ResumoPagamentos> resumirPagamentos(String clinicaId, LocalDate de, LocalDate ate, Granularidade granularidade) {
        Document filtro = new Document("clinicaId", clinicaId)
                .append("status", StatusFinanceiroEnum.PAGO.name())
                .append("dataPagamento", new Document("$gte", paraData(de)).append("$lt", paraData(ate.plusDays(1))));
        Object bucket = granularidade == null ? new Document("$literal", CHAVE_TOTAL)
                : paraTexto("dataPagamento", FusoGravacao.idMongo(), formato(granularidade));
        List<Document> pipeline = List.of(
                new Document("$match", filtro),
                new Document("$group", new Document("_id", new Document("b", bucket).append("p", "$pacienteId"))
                        .append("valor", new Document("$sum", new Document("$ifNull", List.of("$valorPago", "$valorTotal"))))
                        .append("recebimentos", new Document("$sum", 1))),
                new Document("$group", new Document("_id", "$_id.b")
                        .append("valor", new Document("$sum", "$valor"))
                        .append("recebimentos", new Document("$sum", "$recebimentos"))
                        .append("pacientes", new Document("$sum", 1))));

        Map<String, ResumoPagamentos> porBucket = new TreeMap<>();
        for (Document linha : executar(ContaReceber.class, pipeline)) {
            porBucket.put(linha.getString("_id"), new ResumoPagamentos(
                    dinheiro(linha.get("valor")), ((Number) linha.get("recebimentos")).longValue(),
                    ((Number) linha.get("pacientes")).longValue()));
        }
        return porBucket;
    }

    /**
     * Saldo em aberto (valorTotal - valorPago, nunca negativo) das contas PENDENTE e ATRASADO por dia de dataVencimento;
     * {@code de} e {@code ate} inclusivos. Só entram títulos com saldo.
     */
    public Map<LocalDate, SaldoAberto> somarAReceberPorDia(String clinicaId, LocalDate de, LocalDate ate) {
        Document filtro = new Document("clinicaId", clinicaId)
                .append("status", new Document("$in", STATUS_EM_ABERTO))
                .append("dataVencimento", new Document("$gte", paraData(de)).append("$lt", paraData(ate.plusDays(1))));
        List<Document> pipeline = List.of(
                new Document("$match", filtro),
                new Document("$project", new Document("dia", paraTexto("dataVencimento", FusoGravacao.idMongo(), FORMATO_DIA))
                        .append("saldo", expressaoSaldo())),
                new Document("$match", new Document("saldo", new Document("$gt", 0))),
                new Document("$group", new Document("_id", "$dia")
                        .append("valor", new Document("$sum", "$saldo")).append("titulos", new Document("$sum", 1))));

        Map<LocalDate, SaldoAberto> porDia = new TreeMap<>();
        for (Document linha : executar(ContaReceber.class, pipeline)) {
            porDia.put(LocalDate.parse(linha.getString("_id")),
                    new SaldoAberto(dinheiro(linha.get("valor")), ((Number) linha.get("titulos")).longValue()));
        }
        return porDia;
    }

    /** Saldo em aberto de títulos que venceram antes de {@code antesDe} (inadimplência de períodos anteriores). */
    public SaldoAberto saldoEmAbertoVencidoAntesDe(String clinicaId, LocalDate antesDe) {
        Document filtro = new Document("clinicaId", clinicaId)
                .append("status", new Document("$in", STATUS_EM_ABERTO))
                .append("dataVencimento", new Document("$lt", paraData(antesDe)));
        List<Document> pipeline = List.of(
                new Document("$match", filtro),
                new Document("$project", new Document("saldo", expressaoSaldo())),
                new Document("$match", new Document("saldo", new Document("$gt", 0))),
                new Document("$group", new Document("_id", null)
                        .append("valor", new Document("$sum", "$saldo")).append("titulos", new Document("$sum", 1))));

        for (Document linha : executar(ContaReceber.class, pipeline)) {
            return new SaldoAberto(dinheiro(linha.get("valor")), ((Number) linha.get("titulos")).longValue());
        }
        return new SaldoAberto(BigDecimal.ZERO.setScale(2), 0);
    }

    /** Primeiro e último ano com agendamento (qualquer status), na data de parede; {@code null} se não houver. */
    public int[] faixaDeAnosAgendamentos(String clinicaId, String dentistaId) {
        Document filtro = new Document("clinicaId", clinicaId);
        if (dentistaId != null) {
            filtro.append("dentistaId", dentistaId);
        }
        return faixaDeAnos(Agendamento.class, filtro, "dataHoraInicio", FusoGravacao.zona());
    }

    /** Primeiro e último ano de cadastro de paciente, no ano de São Paulo; {@code null} se não houver. */
    public int[] faixaDeAnosNovosPacientes(String clinicaId) {
        return faixaDeAnos(Paciente.class, new Document("clinicaId", clinicaId), "createdAt", FusoBrasil.ZONA);
    }

    /** Primeiro e último ano com recebimento (conta PAGO com dataPagamento); {@code null} se não houver. */
    public int[] faixaDeAnosPagamentos(String clinicaId) {
        return faixaDeAnos(ContaReceber.class, new Document("clinicaId", clinicaId)
                .append("status", StatusFinanceiroEnum.PAGO.name()), "dataPagamento", FusoGravacao.zona());
    }

    /** Primeiro e último ano de vencimento de títulos em aberto; {@code null} se não houver. */
    public int[] faixaDeAnosAReceber(String clinicaId) {
        return faixaDeAnos(ContaReceber.class, new Document("clinicaId", clinicaId)
                .append("status", new Document("$in", STATUS_EM_ABERTO)), "dataVencimento", FusoGravacao.zona());
    }

    /**
     * Dias do intervalo ({@code de} e {@code ate} inclusivos) em que a clínica teve alguma movimentação: algum
     * agendamento (qualquer status), recebimento quitado, paciente cadastrado ou conta com vencimento (qualquer status).
     * Os outros dias são "sem expediente". Não considera filtro de dentista: é a atividade da clínica.
     */
    public Set<LocalDate> diasComMovimento(String clinicaId, LocalDate de, LocalDate ate) {
        Set<LocalDate> dias = new TreeSet<>();
        dias.addAll(contarAgendamentosPorDia(clinicaId, de, ate, null, null).keySet());
        resumirPagamentos(clinicaId, de, ate, Granularidade.DIA).keySet().forEach(chave -> dias.add(LocalDate.parse(chave)));
        dias.addAll(contarNovosPacientesPorDia(clinicaId, de, ate).keySet());
        dias.addAll(diasComVencimento(clinicaId, de, ate));
        return dias;
    }

    private Set<LocalDate> diasComVencimento(String clinicaId, LocalDate de, LocalDate ate) {
        List<String> todosOsStatus = Arrays.stream(StatusFinanceiroEnum.values()).map(Enum::name).toList();
        Document filtro = new Document("clinicaId", clinicaId)
                .append("status", new Document("$in", todosOsStatus))
                .append("dataVencimento", new Document("$gte", paraData(de)).append("$lt", paraData(ate.plusDays(1))));
        List<Document> pipeline = List.of(
                new Document("$match", filtro),
                new Document("$group", new Document("_id", paraTexto("dataVencimento", FusoGravacao.idMongo(), FORMATO_DIA))));
        Set<LocalDate> dias = new TreeSet<>();
        for (Document linha : executar(ContaReceber.class, pipeline)) {
            dias.add(LocalDate.parse(linha.getString("_id")));
        }
        return dias;
    }

    /** Chave que {@link #resumirPagamentos} usa para o bucket que começa em {@code inicio}. */
    public static String chaveDoBucket(Granularidade granularidade, LocalDate inicio) {
        return switch (granularidade) {
            case DIA -> inicio.format(DateTimeFormatter.ISO_LOCAL_DATE);
            case MES -> String.format("%04d-%02d", inicio.getYear(), inicio.getMonthValue());
            case ANO -> String.format("%04d", inicio.getYear());
        };
    }

    private int[] faixaDeAnos(Class<?> tipo, Document filtro, String campo, ZoneId zona) {
        Document comData = new Document(filtro).append(campo, new Document("$type", "date"));
        MongoCollection<Document> colecao = mongoTemplate.getCollection(mongoTemplate.getCollectionName(tipo));
        Document menor = colecao.find(comData).sort(new Document(campo, 1)).limit(1).first();
        Document maior = colecao.find(comData).sort(new Document(campo, -1)).limit(1).first();
        if (menor == null || maior == null) {
            return null;
        }
        return new int[]{ano(menor.get(campo, Date.class), zona), ano(maior.get(campo, Date.class), zona)};
    }

    private static int ano(Date data, ZoneId zona) {
        return data.toInstant().atZone(zona).getYear();
    }

    private List<Document> executar(Class<?> tipo, List<Document> pipeline) {
        List<Document> linhas = new ArrayList<>();
        mongoTemplate.getCollection(mongoTemplate.getCollectionName(tipo)).aggregate(pipeline).into(linhas);
        return linhas;
    }

    /** Data de parede (LocalDate/LocalDateTime) como o conversor do Spring a grava: no fuso da JVM. */
    private static Date paraData(LocalDate dia) {
        return Date.from(dia.atStartOfDay(FusoGravacao.zona()).toInstant());
    }

    private static Date paraData(LocalDateTime horario) {
        return Date.from(horario.atZone(FusoGravacao.zona()).toInstant());
    }

    private static Document paraTexto(String campo, String fuso, String formato) {
        return new Document("$dateToString", new Document("format", formato).append("date", "$" + campo).append("timezone", fuso));
    }

    private static String formato(Granularidade granularidade) {
        return switch (granularidade) {
            case DIA -> FORMATO_DIA;
            case MES -> FORMATO_MES;
            case ANO -> FORMATO_ANO;
        };
    }

    private static Document expressaoSaldo() {
        return new Document("$max", List.of(0, new Document("$subtract", List.of(
                new Document("$ifNull", List.of("$valorTotal", 0)), new Document("$ifNull", List.of("$valorPago", 0))))));
    }

    private static BigDecimal dinheiro(Object valor) {
        double numero = valor == null ? 0.0 : ((Number) valor).doubleValue();
        return BigDecimal.valueOf(numero).setScale(2, RoundingMode.HALF_UP);
    }

    private static DateOperators.DateToString diaDe(String campo) {
        return DateOperators.dateOf(campo)
                .withTimezone(DateOperators.Timezone.valueOf(FusoGravacao.idMongo()))
                .toString(FORMATO_DIA);
    }
}

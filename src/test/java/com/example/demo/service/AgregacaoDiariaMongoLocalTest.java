package com.example.demo.service;

import com.example.demo.enums.FormaPagamentoEnum;
import com.example.demo.enums.StatusAgendamentoEnum;
import com.example.demo.enums.StatusFinanceiroEnum;
import com.example.demo.model.Agendamento;
import com.example.demo.model.ContaReceber;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

// Usa um banco temporário próprio (derrubado ao final); é ignorado quando não há Mongo local.
@SpringBootTest(properties = "spring.mongodb.uri=mongodb://localhost:27017/tmp_teste_agregacao_diaria")
@EnabledIf("mongoLocalDisponivel")
class AgregacaoDiariaMongoLocalTest {

    private static final String CLINICA = "clinica-a";
    private static final String OUTRA = "clinica-b";

    static boolean mongoLocalDisponivel() {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("localhost", 27017), 500);
            return true;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    @Autowired MongoTemplate mongo;
    @Autowired AgregacaoDiariaService agregacao;

    private void agendar(String clinica, String dentista, StatusAgendamentoEnum status, LocalDateTime inicio) {
        Agendamento a = new Agendamento();
        a.setClinicaId(clinica);
        a.setPacienteId("p");
        a.setDentistaId(dentista);
        a.setDataHoraInicio(inicio);
        a.setDataHoraFim(inicio.plusMinutes(30));
        a.setStatus(status);
        mongo.save(a);
    }

    private void receber(String clinica, StatusFinanceiroEnum status, double valorPago, LocalDate dataPagamento) {
        mongo.save(ContaReceber.builder().clinicaId(clinica).pacienteId("p").descricao("c").valorTotal(valorPago)
                .valorPago(valorPago).formaPagamento(FormaPagamentoEnum.PIX).dataVencimento(dataPagamento)
                .dataPagamento(dataPagamento).status(status).build());
    }

    private void semear() {
        mongo.getDb().drop();
        // Horários de parede rente à meia-noite: é onde um fuso errado joga o atendimento para o dia vizinho.
        agendar(CLINICA, "d1", StatusAgendamentoEnum.REALIZADO, LocalDateTime.of(2026, 10, 1, 0, 30));
        agendar(CLINICA, "d1", StatusAgendamentoEnum.REALIZADO, LocalDateTime.of(2026, 10, 1, 23, 30));
        agendar(CLINICA, "d2", StatusAgendamentoEnum.REALIZADO, LocalDateTime.of(2026, 10, 2, 0, 0));
        agendar(CLINICA, "d2", StatusAgendamentoEnum.CANCELADO, LocalDateTime.of(2026, 10, 2, 9, 0));
        agendar(CLINICA, "d1", StatusAgendamentoEnum.REALIZADO, LocalDateTime.of(2026, 10, 31, 23, 59, 59));
        agendar(CLINICA, "d1", StatusAgendamentoEnum.REALIZADO, LocalDateTime.of(2026, 9, 30, 23, 59, 59));
        agendar(CLINICA, "d1", StatusAgendamentoEnum.REALIZADO, LocalDateTime.of(2026, 11, 1, 0, 0));
        agendar(OUTRA, "d1", StatusAgendamentoEnum.REALIZADO, LocalDateTime.of(2026, 10, 1, 10, 0));

        receber(CLINICA, StatusFinanceiroEnum.PAGO, 100.10, LocalDate.of(2026, 10, 1));
        receber(CLINICA, StatusFinanceiroEnum.PAGO, 200.20, LocalDate.of(2026, 10, 1));
        receber(CLINICA, StatusFinanceiroEnum.PAGO, 50.0, LocalDate.of(2026, 10, 31));
        receber(CLINICA, StatusFinanceiroEnum.PENDENTE, 999.0, LocalDate.of(2026, 10, 1));
        receber(CLINICA, StatusFinanceiroEnum.PAGO, 70.0, LocalDate.of(2026, 9, 30));
        receber(CLINICA, StatusFinanceiroEnum.PAGO, 70.0, LocalDate.of(2026, 11, 1));
        receber(OUTRA, StatusFinanceiroEnum.PAGO, 500.0, LocalDate.of(2026, 10, 1));
    }

    private static final LocalDate DE = LocalDate.of(2026, 10, 1);
    private static final LocalDate ATE = LocalDate.of(2026, 10, 31);

    private void verificarDias(String ambiente) {
        Map<LocalDate, Long> consultas = agregacao.contarAgendamentosPorDia(CLINICA, DE, ATE,
                Set.of(StatusAgendamentoEnum.REALIZADO), null);
        assertEquals(Map.of(LocalDate.of(2026, 10, 1), 2L, LocalDate.of(2026, 10, 2), 1L, LocalDate.of(2026, 10, 31), 1L),
                consultas, ambiente);

        assertEquals(Map.of(LocalDate.of(2026, 10, 2), 1L),
                agregacao.contarAgendamentosPorDia(CLINICA, DE, ATE, Set.of(StatusAgendamentoEnum.REALIZADO), "d2"), ambiente);
        assertEquals(Map.of(LocalDate.of(2026, 10, 2), 1L),
                agregacao.contarAgendamentosPorDia(CLINICA, DE, ATE, Set.of(StatusAgendamentoEnum.CANCELADO), null), ambiente);
        assertEquals(Map.of(LocalDate.of(2026, 10, 1), 2L, LocalDate.of(2026, 10, 2), 2L, LocalDate.of(2026, 10, 31), 1L),
                agregacao.contarAgendamentosPorDia(CLINICA, DE, ATE, null, null), ambiente);
        assertEquals(Map.of(LocalDate.of(2026, 10, 1), 1L),
                agregacao.contarAgendamentosPorDia(OUTRA, DE, ATE, null, null), ambiente);

        Map<LocalDate, BigDecimal> receita = agregacao.somarReceitaPorDia(CLINICA, DE, ATE);
        assertEquals(Map.of(LocalDate.of(2026, 10, 1), new BigDecimal("300.30"), LocalDate.of(2026, 10, 31), new BigDecimal("50.00")),
                receita, ambiente);
        assertTrue(agregacao.somarReceitaPorDia(CLINICA, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31)).isEmpty(), ambiente);
        assertTrue(agregacao.contarAgendamentosPorDia("clinica-inexistente", DE, ATE, null, null).isEmpty(), ambiente);
    }

    @Test
    void diasSaemCorretosComJvmEmUtcQueEOAmbienteDeProducaoEEmSaoPauloQueEOLocal() {
        assertEquals("tmp_teste_agregacao_diaria", mongo.getDb().getName());
        TimeZone original = TimeZone.getDefault();
        try {
            for (String fuso : new String[]{"UTC", "Etc/UTC", "America/Sao_Paulo", "Pacific/Kiritimati", "Pacific/Pago_Pago"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(fuso));
                semear();
                verificarDias("JVM em " + fuso);
            }
        } finally {
            TimeZone.setDefault(original);
            mongo.getDb().drop();
        }
    }

    @Test
    void comoOsDadosFicamGravadosEmCadaAmbiente_ConfirmaAInformacaoDoDevOps() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            mongo.getDb().drop();
            receber(CLINICA, StatusFinanceiroEnum.PAGO, 10.0, LocalDate.of(2026, 9, 10));
            Instant emProducao = mongo.getCollection("contas_receber").find().first().get("dataPagamento", Date.class).toInstant();
            assertEquals(Instant.parse("2026-09-10T00:00:00Z"), emProducao, "JVM UTC grava 10/09 como 00:00Z");

            TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
            mongo.getDb().drop();
            receber(CLINICA, StatusFinanceiroEnum.PAGO, 10.0, LocalDate.of(2026, 9, 10));
            Instant emLocal = mongo.getCollection("contas_receber").find().first().get("dataPagamento", Date.class).toInstant();
            assertEquals(Instant.parse("2026-09-10T03:00:00Z"), emLocal, "JVM São Paulo grava 10/09 como 03:00Z");
        } finally {
            TimeZone.setDefault(original);
            mongo.getDb().drop();
        }
    }

    @Test
    void fixarSaoPauloNaAgregacaoErraOsDiasQuandoAJvmEUtc_PorIssoOFusoEODeGravacao() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            mongo.getDb().drop();
            agendar(CLINICA, "d1", StatusAgendamentoEnum.REALIZADO, LocalDateTime.of(2026, 10, 1, 0, 30));
            receber(CLINICA, StatusFinanceiroEnum.PAGO, 10.0, LocalDate.of(2026, 10, 1));

            assertEquals(Map.of(LocalDate.of(2026, 10, 1), 1L),
                    agregacao.contarAgendamentosPorDia(CLINICA, DE, ATE, null, null));
            assertEquals(Map.of(LocalDate.of(2026, 10, 1), new BigDecimal("10.00")), agregacao.somarReceitaPorDia(CLINICA, DE, ATE));

            // Mesma consulta, mas com timezone fixo "America/Sao_Paulo": 00:30Z vira 21:30 de 30/09 e 01/10 00:00Z vira 30/09 21:00.
            assertEquals("2026-09-30", diaComFusoFixo("agendamentos", "dataHoraInicio", "America/Sao_Paulo"));
            assertEquals("2026-09-30", diaComFusoFixo("contas_receber", "dataPagamento", "America/Sao_Paulo"));
            assertEquals("2026-10-01", diaComFusoFixo("agendamentos", "dataHoraInicio", "UTC"));
        } finally {
            TimeZone.setDefault(original);
            mongo.getDb().drop();
        }
    }

    private String diaComFusoFixo(String colecao, String campo, String timezone) {
        List<Document> pipeline = new ArrayList<>();
        pipeline.add(new Document("$group", new Document("_id", new Document("$dateToString",
                new Document("format", "%Y-%m-%d").append("date", "$" + campo).append("timezone", timezone)))));
        return mongo.getCollection(colecao).aggregate(pipeline).first().getString("_id");
    }
}

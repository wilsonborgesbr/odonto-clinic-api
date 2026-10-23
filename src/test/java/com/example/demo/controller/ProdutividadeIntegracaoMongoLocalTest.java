package com.example.demo.controller;

import com.example.demo.enums.FormaPagamentoEnum;
import com.example.demo.enums.MetricaProdutividadeEnum;
import com.example.demo.enums.PermissaoEnum;
import com.example.demo.enums.RoleEnum;
import com.example.demo.enums.StatusAgendamentoEnum;
import com.example.demo.enums.StatusFinanceiroEnum;
import com.example.demo.model.Agendamento;
import com.example.demo.model.ContaReceber;
import com.example.demo.model.Dentista;
import com.example.demo.model.User;
import com.example.demo.service.ProdutividadeService;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.EnumSet;
import java.util.Set;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// Usa um banco temporário próprio (derrubado ao final); é ignorado quando não há Mongo local.
@SpringBootTest(properties = "spring.mongodb.uri=mongodb://localhost:27017/tmp_teste_produtividade")
@AutoConfigureMockMvc
@EnabledIf("mongoLocalDisponivel")
class ProdutividadeIntegracaoMongoLocalTest {

    private static final String A = "clinica-a";
    private static final String B = "clinica-b";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    // 17:00Z = 14:00 em São Paulo de sexta-feira, 23/10/2026
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-23T17:00:00Z"), ZoneOffset.UTC);

    static boolean mongoLocalDisponivel() {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("localhost", 27017), 500);
            return true;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    @Autowired MongoTemplate mongo;
    @Autowired MockMvc mvc;
    @Autowired ProdutividadeService service;

    // ---------- dados ----------

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

    private void pagar(String clinica, String paciente, Double valorPago, double valorTotal, LocalDate dataPagamento) {
        mongo.save(ContaReceber.builder().clinicaId(clinica).pacienteId(paciente).descricao("c").valorTotal(valorTotal)
                .valorPago(valorPago).formaPagamento(FormaPagamentoEnum.PIX).dataVencimento(dataPagamento)
                .dataPagamento(dataPagamento).status(StatusFinanceiroEnum.PAGO).build());
    }

    private void aberta(String clinica, StatusFinanceiroEnum status, double total, double pago, LocalDate vencimento) {
        mongo.save(ContaReceber.builder().clinicaId(clinica).pacienteId("p1").descricao("c").valorTotal(total)
                .valorPago(pago).formaPagamento(FormaPagamentoEnum.PIX).dataVencimento(vencimento).status(status).build());
    }

    private void paciente(String clinica, boolean ativo, String instante) {
        mongo.getCollection("pacientes").insertOne(new Document("clinicaId", clinica).append("ativo", ativo)
                .append("nomeCompleto", "P").append("createdAt", Date.from(Instant.parse(instante))));
    }

    private void semear() {
        mongo.getDb().drop();
        Dentista d1 = new Dentista();
        d1.setId("d1");
        d1.setClinicaId(A);
        d1.setNomeCompleto("Dr. Um");
        d1.setAtivo(true);
        mongo.save(d1);

        // outubro
        agendar(A, "d1", StatusAgendamentoEnum.REALIZADO, LocalDateTime.of(2026, 10, 5, 9, 0));
        agendar(A, "d1", StatusAgendamentoEnum.REALIZADO, LocalDateTime.of(2026, 10, 5, 10, 0));
        agendar(A, "d2", StatusAgendamentoEnum.FALTOU, LocalDateTime.of(2026, 10, 5, 11, 0));
        agendar(A, "d1", StatusAgendamentoEnum.CANCELADO, LocalDateTime.of(2026, 10, 5, 15, 0));
        agendar(A, "d1", StatusAgendamentoEnum.REALIZADO, LocalDateTime.of(2026, 10, 6, 0, 30));
        agendar(A, "d2", StatusAgendamentoEnum.REALIZADO, LocalDateTime.of(2026, 10, 6, 23, 30));
        agendar(A, "d1", StatusAgendamentoEnum.REALIZADO, LocalDateTime.of(2026, 10, 23, 9, 0));
        agendar(A, "d1", StatusAgendamentoEnum.AGENDADO, LocalDateTime.of(2026, 10, 23, 16, 0)); // hoje, mais tarde
        agendar(A, "d1", StatusAgendamentoEnum.AGENDADO, LocalDateTime.of(2026, 10, 30, 9, 0)); // futuro
        // setembro (a 25/09 fica fora do mesmo intervalo, 1 a 23)
        agendar(A, "d1", StatusAgendamentoEnum.REALIZADO, LocalDateTime.of(2026, 9, 5, 9, 0));
        agendar(A, "d1", StatusAgendamentoEnum.REALIZADO, LocalDateTime.of(2026, 9, 25, 9, 0));
        // outra clínica
        for (int i = 0; i < 5; i++) {
            agendar(B, "d1", StatusAgendamentoEnum.REALIZADO, LocalDateTime.of(2026, 10, 5, 9 + i, 0));
        }

        // novos pacientes (instantes reais); 06/10 01:30Z = 05/10 22:30 em São Paulo
        paciente(A, true, "2026-10-06T01:30:00Z");
        paciente(A, false, "2026-10-07T02:59:59Z"); // 06/10 23:59:59 em SP, inativo também conta
        paciente(A, true, "2026-10-07T03:00:00Z");  // 07/10 00:00 em SP
        paciente(A, true, "2026-10-23T12:00:00Z");
        paciente(A, true, "2026-10-24T02:00:00Z");  // 23/10 23:00 em SP
        paciente(A, true, "2026-09-02T12:00:00Z");
        paciente(B, true, "2026-10-05T12:00:00Z");

        // recebimentos
        pagar(A, "p1", 100.10, 100.10, LocalDate.of(2026, 10, 5));
        pagar(A, "p1", 200.20, 200.20, LocalDate.of(2026, 10, 5));
        pagar(A, "p2", 50.0, 50.0, LocalDate.of(2026, 10, 6));
        pagar(A, "p3", null, 40.0, LocalDate.of(2026, 10, 6)); // sem valorPago: vale o valorTotal
        pagar(A, "p4", 999.0, 999.0, LocalDate.of(2026, 10, 24)); // depois de hoje
        pagar(A, "p1", 70.0, 70.0, LocalDate.of(2026, 9, 10));
        pagar(B, "p9", 777.0, 777.0, LocalDate.of(2026, 10, 5));

        // a receber
        aberta(A, StatusFinanceiroEnum.PENDENTE, 500, 100, LocalDate.of(2026, 10, 10));
        aberta(A, StatusFinanceiroEnum.ATRASADO, 300, 0, LocalDate.of(2026, 10, 12));
        aberta(A, StatusFinanceiroEnum.PENDENTE, 1200, 0, LocalDate.of(2026, 10, 28));
        aberta(A, StatusFinanceiroEnum.CANCELADO, 999, 0, LocalDate.of(2026, 10, 15));
        aberta(A, StatusFinanceiroEnum.PENDENTE, 850, 0, LocalDate.of(2026, 9, 15));
        aberta(A, StatusFinanceiroEnum.PENDENTE, 150, 0, LocalDate.of(2026, 8, 1));
        aberta(B, StatusFinanceiroEnum.PENDENTE, 888, 0, LocalDate.of(2026, 10, 10));

        // ano que vem: agenda marcada e título a vencer
        agendar(A, "d1", StatusAgendamentoEnum.AGENDADO, LocalDateTime.of(2027, 1, 15, 9, 0));
        aberta(A, StatusFinanceiroEnum.PENDENTE, 300, 0, LocalDate.of(2027, 2, 10));
    }

    // ---------- chamadas ----------

    private static UsernamePasswordAuthenticationToken login(String clinica, RoleEnum role, Set<PermissaoEnum> permissoes) {
        User user = User.builder().id("u-" + role).name("U").email("u@x.com").clinicaId(clinica).role(role)
                .permissoes(permissoes).ativo(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private MvcResult chamar(UsernamePasswordAuthenticationToken auth, String metrica, String periodo, String dentistaId) throws Exception {
        return chamar(auth, metrica, periodo, dentistaId, null);
    }

    private MvcResult chamar(UsernamePasswordAuthenticationToken auth, String metrica, String periodo, String dentistaId,
                             String referencia) throws Exception {
        var req = get("/api/dashboard/produtividade").param("metrica", metrica).param("periodo", periodo).with(authentication(auth));
        if (dentistaId != null) {
            req = req.param("dentistaId", dentistaId);
        }
        if (referencia != null) {
            req = req.param("referencia", referencia);
        }
        return mvc.perform(req).andReturn();
    }

    private JsonNode ok(String metrica, String periodo) throws Exception {
        return ok(A, metrica, periodo, null);
    }

    private JsonNode ok(String clinica, String metrica, String periodo, String dentistaId) throws Exception {
        return ok(clinica, metrica, periodo, dentistaId, null);
    }

    private JsonNode ok(String clinica, String metrica, String periodo, String dentistaId, String referencia) throws Exception {
        MvcResult r = chamar(login(clinica, RoleEnum.PROPRIETARIO, null), metrica, periodo, dentistaId, referencia);
        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        return JSON.readTree(r.getResponse().getContentAsString());
    }

    private static JsonNode ponto(JsonNode corpo, String chave) {
        for (JsonNode p : corpo.get("pontos")) {
            if (chave.equals(p.get("chave").asString())) {
                return p;
            }
        }
        throw new AssertionError("ponto " + chave + " não existe");
    }

    private static void igual(String esperado, JsonNode valor, String msg) {
        assertEquals(0, new BigDecimal(esperado).compareTo(valor.decimalValue()), msg + " esperado=" + esperado + " obtido=" + valor);
    }

    // ---------- testes ----------

    @Test
    void asNoveMetricasDaMesmaBaseDaoOsMesmosNumerosEmJvmUtcSaoPauloOuKiritimati() throws Exception {
        assertEquals("tmp_teste_produtividade", mongo.getDb().getName());
        TimeZone original = TimeZone.getDefault();
        Object relogioOriginal = ReflectionTestUtils.getField(service, "relogio");
        try {
            ReflectionTestUtils.setField(service, "relogio", RELOGIO);
            for (String fuso : new String[]{"UTC", "America/Sao_Paulo", "Pacific/Kiritimati"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(fuso));
                semear();
                verificarAgenda(fuso);
                verificarPacientes(fuso);
                verificarFinanceiro(fuso);
                verificarSemExpediente(fuso);
            }
        } finally {
            ReflectionTestUtils.setField(service, "relogio", relogioOriginal);
            TimeZone.setDefault(original);
            mongo.getDb().drop();
        }
    }

    private void verificarAgenda(String fuso) throws Exception {
        String m = "JVM " + fuso + ": ";
        JsonNode consultas = ok("CONSULTAS_REALIZADAS", "MES");
        assertEquals("CONTAGEM", consultas.get("unidade").asString(), m);
        assertEquals("Outubro de 2026", consultas.get("rotuloPeriodo").asString(), m);
        assertEquals(31, consultas.get("pontos").size(), m);
        assertEquals(2, ponto(consultas, "2026-10-05").get("valor").longValue(), m + "05/10");
        assertEquals(2, ponto(consultas, "2026-10-06").get("valor").longValue(), m + "00:30 e 23:30 ficam no dia 06");
        assertEquals(1, ponto(consultas, "2026-10-23").get("valor").longValue(), m);
        assertEquals(5, consultas.get("total").longValue(), m + "outra clínica não entra");
        assertEquals(1, consultas.get("totalAnterior").longValue(), m + "25/09 está fora do intervalo 1 a 23");
        igual("400.0", consultas.get("variacaoPercentual"), m);
        assertEquals("vs mês anterior (mesmo intervalo: dias 1 a 23)", consultas.get("textoVariacao").asString(), m);
        assertEquals(1, ponto(consultas, "2026-10-05").get("anterior").longValue(), m + "anterior alinhado: 05/09");
        assertTrue(ponto(consultas, "2026-10-24").get("valor").isNull(), m + "ponto futuro sem valor");
        assertTrue(ponto(consultas, "2026-10-24").get("futuro").booleanValue(), m);
        assertFalse(ponto(consultas, "2026-10-23").get("futuro").booleanValue(), m);
        assertTrue(consultas.get("inadimplenciaHistorica").isNull(), m);

        JsonNode agendamentos = ok("AGENDAMENTOS", "MES");
        JsonNode dia5 = ponto(agendamentos, "2026-10-05");
        assertEquals(4, dia5.get("valor").longValue(), m);
        assertEquals(2, dia5.get("detalhes").get("realizadas").longValue(), m);
        assertEquals(1, dia5.get("detalhes").get("faltas").longValue(), m);
        assertEquals(1, dia5.get("detalhes").get("canceladas").longValue(), m);
        assertEquals(0, dia5.get("detalhes").get("agendadas").longValue(), m);
        assertEquals(1, ponto(agendamentos, "2026-10-23").get("detalhes").get("agendadas").longValue(), m + "o das 16h de hoje ainda conta");
        assertEquals(2, ponto(agendamentos, "2026-10-23").get("valor").longValue(), m);
        assertEquals(9, agendamentos.get("total").longValue(), m + "total do mês inteiro: o agendamento futuro de 30/10 entra");
        assertEquals(2, agendamentos.get("totalAnterior").longValue(), m + "setembro INTEIRO (05/09 e 25/09), sem corte no dia 23");
        igual("350.0", agendamentos.get("variacaoPercentual"), m + "(9 - 2) / 2");
        assertEquals("vs mês anterior (período inteiro)", agendamentos.get("textoVariacao").asString(), m);
        JsonNode dia30 = ponto(agendamentos, "2026-10-30");
        assertEquals(1, dia30.get("valor").longValue(), m + "ponto futuro com valor real");
        assertTrue(dia30.get("futuro").booleanValue(), m);
        assertFalse(dia30.get("semExpediente").booleanValue(), m);
        assertEquals(1, dia30.get("detalhes").get("agendadas").longValue(), m);
        assertEquals(0, ponto(agendamentos, "2026-10-24").get("valor").longValue(), m + "sábado com recebimento previsto: zero numérico");
        assertTrue(ponto(agendamentos, "2026-10-31").get("valor").isNull(), m + "dia futuro sem nenhuma movimentação: sem expediente");
        assertEquals(java.util.List.of(2026, 2027), java.util.stream.StreamSupport.stream(agendamentos.get("anosDisponiveis").spliterator(), false)
                .map(JsonNode::intValue).toList(), m + "o ano com agenda marcada é navegável");

        JsonNode janeiro2027 = ok(A, "AGENDAMENTOS", "MES", null, "2027-01-15");
        assertEquals("Janeiro de 2027", janeiro2027.get("rotuloPeriodo").asString(), m);
        assertEquals(1, ponto(janeiro2027, "2027-01-15").get("valor").longValue(), m);
        assertTrue(ponto(janeiro2027, "2027-01-15").get("futuro").booleanValue(), m);
        assertEquals(1, janeiro2027.get("total").longValue(), m);
        assertEquals(1, ponto(ok(A, "AGENDAMENTOS", "ANO", null, "2027-06-01"), "2027-01-01").get("valor").longValue(), m);
        assertEquals(12, ok("AGENDAMENTOS", "TODOS").get("total").longValue(), m + "todos os anos, inclusive o futuro");
        assertEquals(java.util.List.of(2026), java.util.stream.StreamSupport.stream(ok("FALTAS", "MES").get("anosDisponiveis").spliterator(), false)
                .map(JsonNode::intValue).toList(), m + "fato passado não vai para 2027");

        assertEquals(1, ok("FALTAS", "MES").get("total").longValue(), m);
        assertEquals(1, ok("CANCELAMENTOS", "MES").get("total").longValue(), m);

        JsonNode comparecimento = ok("COMPARECIMENTO", "MES");
        assertEquals("PERCENTUAL", comparecimento.get("unidade").asString(), m);
        igual("83.33", comparecimento.get("total"), m + "5 realizadas / (5 + 1 falta)");
        igual("66.67", ponto(comparecimento, "2026-10-05").get("valor"), m + "2 / (2 + 1)");
        igual("100.00", ponto(comparecimento, "2026-10-06").get("valor"), m);
        assertTrue(ponto(comparecimento, "2026-10-07").get("valor").isNull(), m + "dia sem atendimento: null");
        assertTrue(comparecimento.get("totalAnterior").decimalValue().compareTo(new BigDecimal("100")) == 0, m + "setembro: 1/1");

        JsonNode doDentista = ok(A, "CONSULTAS_REALIZADAS", "MES", "d1");
        assertEquals(4, doDentista.get("total").longValue(), m + "filtro por dentista");
        assertEquals(1, ponto(doDentista, "2026-10-06").get("valor").longValue(), m);
        assertEquals(404, chamar(login(A, RoleEnum.PROPRIETARIO, null), "FALTAS", "MES", "d-de-outra-clinica").getResponse().getStatus(), m);

        JsonNode ano = ok("CONSULTAS_REALIZADAS", "ANO");
        assertEquals(12, ano.get("pontos").size(), m);
        assertEquals(5, ponto(ano, "2026-10-01").get("valor").longValue(), m);
        assertEquals(2, ponto(ano, "2026-09-01").get("valor").longValue(), m);
        assertTrue(ponto(ano, "2026-11-01").get("valor").isNull(), m);
        JsonNode todos = ok("CONSULTAS_REALIZADAS", "TODOS");
        assertEquals(7, todos.get("total").longValue(), m);
        assertTrue(todos.get("totalAnterior").isNull(), m);
        assertTrue(todos.get("variacaoPercentual").isNull(), m);
        assertEquals(1, todos.get("pontos").size(), m);
        assertEquals(java.util.List.of(2026), java.util.stream.StreamSupport.stream(todos.get("anosDisponiveis").spliterator(), false)
                .map(JsonNode::intValue).toList(), m);
        JsonNode semana = ok("CONSULTAS_REALIZADAS", "SEMANA");
        assertEquals(7, semana.get("pontos").size(), m);
        assertEquals("2026-10-19", semana.get("pontos").get(0).get("chave").asString(), m);
        assertEquals(1, semana.get("total").longValue(), m + "só 23/10 na semana de 19 a 25");
    }

    private void verificarPacientes(String fuso) throws Exception {
        String m = "JVM " + fuso + ": ";
        JsonNode novos = ok("NOVOS_PACIENTES", "MES");
        assertEquals(1, ponto(novos, "2026-10-05").get("valor").longValue(), m + "cadastro às 22:30 de SP é dia 05, não 06");
        assertEquals(1, ponto(novos, "2026-10-06").get("valor").longValue(), m + "23:59:59 em SP, inativo também conta");
        assertEquals(1, ponto(novos, "2026-10-07").get("valor").longValue(), m + "00:00 em SP");
        assertEquals(2, ponto(novos, "2026-10-23").get("valor").longValue(), m + "23:00 de SP ainda é dia 23");
        assertEquals(5, novos.get("total").longValue(), m);
        assertEquals(1, novos.get("totalAnterior").longValue(), m);
        igual("400.0", novos.get("variacaoPercentual"), m);
        assertEquals(0, ponto(novos, "2026-10-07").get("detalhes").size(), m + "novos pacientes não tem linha extra: objeto vazio");
        assertTrue(ponto(novos, "2026-10-08").get("semExpediente").booleanValue(), m);
        assertTrue(ponto(novos, "2026-10-08").get("valor").isNull(), m);
    }

    /** Semana de 19 a 25/10 e mês de outubro: dias sem nenhuma movimentação da clínica viram "sem expediente". */
    private void verificarSemExpediente(String fuso) throws Exception {
        String m = "JVM " + fuso + ": ";
        // movimento na semana: 23/10 (agenda e cadastros) e 24/10 (recebimento já lançado); 19, 20, 21, 22 e 25 sem nada
        for (MetricaProdutividadeEnum metrica : MetricaProdutividadeEnum.values()) {
            JsonNode semana = ok(metrica.name(), "SEMANA");
            for (String vazio : new String[]{"2026-10-20", "2026-10-21", "2026-10-22", "2026-10-25"}) {
                JsonNode ponto = ponto(semana, vazio);
                assertTrue(ponto.get("semExpediente").booleanValue(), m + metrica + " " + vazio);
                assertTrue(ponto.get("valor").isNull(), m + metrica + " " + vazio);
                assertTrue(ponto.get("detalhes").isNull(), m + metrica + " " + vazio);
            }
            assertFalse(ponto(semana, "2026-10-23").get("semExpediente").booleanValue(), m + metrica);
            assertFalse(ponto(semana, "2026-10-24").get("semExpediente").booleanValue(), m + metrica + " sábado tem recebimento lançado");
            assertTrue(ponto(semana, "2026-10-24").get("futuro").booleanValue(), m + metrica);
            assertEquals("Seg", ponto(semana, "2026-10-19").get("rotulo").asString(), m);
            assertEquals("19", ponto(semana, "2026-10-19").get("rotuloSecundario").asString(), m);
            assertEquals("19 a 25 de outubro de 2026", semana.get("rotuloPeriodo").asString(), m);
        }
        JsonNode semana = ok("AGENDAMENTOS", "SEMANA");
        assertEquals(0, ponto(semana, "2026-10-24").get("valor").longValue(), m + "futuro com movimento: zero numérico, não null");
        assertTrue(ponto(semana, "2026-10-25").get("valor").isNull(), m);
        assertTrue(ponto(ok("FALTAS", "SEMANA"), "2026-10-24").get("valor").isNull(), m + "fato passado segue sem valor no futuro");

        JsonNode mes = ok("CONSULTAS_REALIZADAS", "MES");
        assertEquals(0, ponto(mes, "2026-10-10").get("valor").longValue(), m + "só havia um vencimento: tem expediente, vale zero");
        assertFalse(ponto(mes, "2026-10-10").get("semExpediente").booleanValue(), m);
        assertFalse(ponto(mes, "2026-10-15").get("semExpediente").booleanValue(), m + "vencimento de conta cancelada também é movimento");
        assertTrue(ponto(mes, "2026-10-08").get("semExpediente").booleanValue(), m);
        assertEquals(5, mes.get("total").longValue(), m + "o total não muda por causa dos dias sem expediente");
        assertTrue(ponto(mes, "2026-10-12").get("anterior").isNull() || ponto(mes, "2026-10-12").get("anterior").longValue() == 0, m);
        assertTrue(ponto(mes, "2026-10-08").get("anterior").isNull(), m + "08/09 sem movimento: anterior null");

        for (String periodo : new String[]{"ANO", "TODOS"}) {
            for (JsonNode ponto : ok("CONSULTAS_REALIZADAS", periodo).get("pontos")) {
                assertFalse(ponto.get("semExpediente").booleanValue(), m + periodo);
            }
        }
        assertEquals(0, ponto(ok("CONSULTAS_REALIZADAS", "ANO"), "2026-01-01").get("valor").longValue(), m + "mês sem nada vale 0");
    }

    private void verificarFinanceiro(String fuso) throws Exception {
        String m = "JVM " + fuso + ": ";
        JsonNode faturamento = ok("FATURAMENTO", "MES");
        assertEquals("MOEDA", faturamento.get("unidade").asString(), m);
        igual("300.30", ponto(faturamento, "2026-10-05").get("valor"), m);
        assertEquals(2, ponto(faturamento, "2026-10-05").get("detalhes").get("recebimentos").longValue(), m);
        igual("90.00", ponto(faturamento, "2026-10-06").get("valor"), m + "50 + 40 (valorPago ausente vale o valorTotal)");
        igual("0.00", ponto(faturamento, "2026-10-07").get("valor"), m);
        igual("390.30", faturamento.get("total"), m + "a conta de 24/10 e a de outra clínica não entram");
        igual("70.00", faturamento.get("totalAnterior"), m);
        igual("457.6", faturamento.get("variacaoPercentual"), m);
        assertTrue(ponto(faturamento, "2026-10-24").get("valor").isNull(), m);

        JsonNode ticket = ok("TICKET_MEDIO", "MES");
        igual("300.30", ponto(ticket, "2026-10-05").get("valor"), m + "1 paciente pagou duas vezes");
        igual("45.00", ponto(ticket, "2026-10-06").get("valor"), m);
        assertEquals(2, ponto(ticket, "2026-10-06").get("detalhes").get("pacientesPagantes").longValue(), m);
        assertTrue(ponto(ticket, "2026-10-07").get("valor").isNull(), m + "sem pagamento: null");
        igual("130.10", ticket.get("total"), m + "390,30 / 3 pacientes distintos (p1, p2, p3)");
        igual("70.00", ticket.get("totalAnterior"), m);

        JsonNode aReceber = ok("A_RECEBER", "MES");
        igual("1900.00", aReceber.get("total"), m + "400 + 300 + 1200; cancelada e pagas fora");
        igual("850.00", aReceber.get("totalAnterior"), m + "setembro inteiro");
        igual("123.5", aReceber.get("variacaoPercentual"), m + "(1900 - 850) / 850");
        igual("400.00", ponto(aReceber, "2026-10-10").get("detalhes").get("atrasado"), m);
        igual("1000.00", aReceber.get("inadimplenciaHistorica").get("totalAtrasado"), m);
    }

    @Test
    void permissaoPorPerfilNasNoveMetricas_403ParaQuemNaoTemAPermissaoEOServidorNaoDevolveValor() throws Exception {
        TimeZone original = TimeZone.getDefault();
        Object relogioOriginal = ReflectionTestUtils.getField(service, "relogio");
        try {
            ReflectionTestUtils.setField(service, "relogio", RELOGIO);
            semear();
            for (RoleEnum role : RoleEnum.values()) {
                for (MetricaProdutividadeEnum metrica : MetricaProdutividadeEnum.values()) {
                    MvcResult r = chamar(login(A, role, null), metrica.name(), "MES", null);
                    boolean permitido = role.permissoesPadrao().contains(metrica.permissao());
                    assertEquals(permitido ? 200 : 403, r.getResponse().getStatus(), role + " " + metrica);
                    if (!permitido) {
                        assertFalse(r.getResponse().getContentAsString().contains("pontos"), role + " " + metrica);
                    }
                }
            }
            // literais do QA: perfil x métrica
            assertEquals(403, chamar(login(A, RoleEnum.FINANCEIRO, null), "FALTAS", "MES", null).getResponse().getStatus());
            assertEquals(200, chamar(login(A, RoleEnum.FINANCEIRO, null), "FATURAMENTO", "MES", null).getResponse().getStatus());
            assertEquals(403, chamar(login(A, RoleEnum.DENTISTA, null), "FATURAMENTO", "MES", null).getResponse().getStatus());
            assertEquals(200, chamar(login(A, RoleEnum.DENTISTA, null), "NOVOS_PACIENTES", "MES", null).getResponse().getStatus());
            assertEquals(403, chamar(login(A, RoleEnum.RECEPCIONISTA, null), "A_RECEBER", "MES", null).getResponse().getStatus());
            assertEquals(403, chamar(login(A, RoleEnum.ESTOQUISTA, null), "CONSULTAS_REALIZADAS", "MES", null).getResponse().getStatus());
            assertEquals(403, chamar(login(A, RoleEnum.ESTOQUISTA, null), "NOVOS_PACIENTES", "MES", null).getResponse().getStatus());
            // permissões personalizadas valem acima do padrão do papel
            assertEquals(403, chamar(login(A, RoleEnum.PROPRIETARIO, EnumSet.of(PermissaoEnum.PACIENTES)), "FATURAMENTO", "MES", null)
                    .getResponse().getStatus());
            // metricasPermitidas reflete as permissões
            MvcResult fin = chamar(login(A, RoleEnum.FINANCEIRO, null), "FATURAMENTO", "MES", null);
            assertEquals("[\"FATURAMENTO\",\"TICKET_MEDIO\",\"A_RECEBER\"]",
                    JSON.readTree(fin.getResponse().getContentAsString()).get("metricasPermitidas").toString());
        } finally {
            ReflectionTestUtils.setField(service, "relogio", relogioOriginal);
            TimeZone.setDefault(original);
            mongo.getDb().drop();
        }
    }

    @Test
    void semAutenticacao401EParametrosInvalidos400() throws Exception {
        try {
            semear();
            assertTrue(mvc.perform(get("/api/dashboard/produtividade").param("metrica", "FALTAS")).andReturn()
                    .getResponse().getStatus() >= 401);
            MvcResult semMetrica = mvc.perform(get("/api/dashboard/produtividade").with(authentication(login(A, RoleEnum.PROPRIETARIO, null)))).andReturn();
            assertEquals(400, semMetrica.getResponse().getStatus());
            assertEquals("Informe a métrica.", JSON.readTree(semMetrica.getResponse().getContentAsString()).get("mensagem").get("metrica").asString());
            assertEquals(400, chamar(login(A, RoleEnum.PROPRIETARIO, null), "FALTAS", "DECADA", null).getResponse().getStatus());
        } finally {
            mongo.getDb().drop();
        }
    }

    @Test
    void aReceberTrazInadimplenciaHistoricaIsolaClinicasEOutraClinicaNaoVeDadosDaA() throws Exception {
        Object relogioOriginal = ReflectionTestUtils.getField(service, "relogio");
        try {
            ReflectionTestUtils.setField(service, "relogio", RELOGIO);
            semear();
            JsonNode aReceber = ok("A_RECEBER", "MES");

            igual("1000.00", aReceber.get("inadimplenciaHistorica").get("totalAtrasado"), "850 (set) + 150 (ago), pendentes antes de 01/10");
            assertEquals(2, aReceber.get("inadimplenciaHistorica").get("quantidadeTitulos").longValue());
            igual("1900.00", aReceber.get("total"), "400 + 300 + 1200");
            igual("400.00", ponto(aReceber, "2026-10-10").get("valor"), "500 - 100 pagos");
            igual("400.00", ponto(aReceber, "2026-10-10").get("detalhes").get("atrasado"), "vencido antes de hoje");
            igual("300.00", ponto(aReceber, "2026-10-12").get("detalhes").get("atrasado"), "ATRASADO também entra");
            igual("1200.00", ponto(aReceber, "2026-10-28").get("valor"), "futuro com valor");
            igual("0.00", ponto(aReceber, "2026-10-28").get("detalhes").get("atrasado"), "ainda não venceu");
            assertTrue(ponto(aReceber, "2026-10-28").get("futuro").booleanValue());
            igual("850.00", aReceber.get("totalAnterior"), "setembro inteiro");
            igual("123.5", aReceber.get("variacaoPercentual"), "(1900 - 850) / 850");
            assertEquals("vs mês anterior (período inteiro)", aReceber.get("textoVariacao").asString());
            assertEquals(java.util.List.of(2026, 2027), java.util.stream.StreamSupport.stream(aReceber.get("anosDisponiveis").spliterator(), false)
                    .map(JsonNode::intValue).toList(), "o título que vence em 2027 torna 2027 navegável");
            JsonNode fevereiro2027 = ok(A, "A_RECEBER", "MES", null, "2027-02-10");
            igual("300.00", fevereiro2027.get("total"), "título de 2027");
            igual("300.00", ponto(fevereiro2027, "2027-02-10").get("valor"), "ponto futuro com valor");
            assertTrue(ponto(fevereiro2027, "2027-02-10").get("futuro").booleanValue());
            igual("0.00", fevereiro2027.get("totalAnterior"), "janeiro de 2027 inteiro");
            assertTrue(fevereiro2027.get("variacaoPercentual").isNull(), "anterior zero: sem selo");
            igual("300.00", ponto(ok(A, "A_RECEBER", "ANO", null, "2027-05-01"), "2027-02-01").get("valor"), "visão anual de 2027");

            JsonNode outra = ok(B, "A_RECEBER", "MES", null);
            igual("888.00", outra.get("total"), "só o que é da clínica B");
            igual("0.00", outra.get("inadimplenciaHistorica").get("totalAtrasado"), "B não tem vencidos antes do mês");
            assertEquals(0, outra.get("inadimplenciaHistorica").get("quantidadeTitulos").longValue());
            igual("777.00", ok(B, "FATURAMENTO", "MES", null).get("total"), "faturamento da B");
            assertEquals(1, ok(B, "NOVOS_PACIENTES", "MES", null).get("total").longValue());
            assertEquals(5, ok(B, "CONSULTAS_REALIZADAS", "MES", null).get("total").longValue());
            assertEquals(404, chamar(login(B, RoleEnum.PROPRIETARIO, null), "FALTAS", "MES", "d1").getResponse().getStatus(),
                    "o dentista d1 é da clínica A");
        } finally {
            ReflectionTestUtils.setField(service, "relogio", relogioOriginal);
            mongo.getDb().drop();
        }
    }

    @Test
    void zeroSemBaseENuloEClinicaVaziaNaoQuebra() throws Exception {
        Object relogioOriginal = ReflectionTestUtils.getField(service, "relogio");
        try {
            ReflectionTestUtils.setField(service, "relogio", RELOGIO);
            mongo.getDb().drop();
            for (String metrica : new String[]{"COMPARECIMENTO", "TICKET_MEDIO"}) {
                JsonNode vazio = ok(metrica, "MES");
                assertTrue(vazio.get("total").isNull(), metrica);
                assertTrue(vazio.get("variacaoPercentual").isNull(), metrica);
                assertTrue(ponto(vazio, "2026-10-05").get("valor").isNull(), metrica);
            }
            for (String metrica : new String[]{"CONSULTAS_REALIZADAS", "AGENDAMENTOS", "FALTAS", "CANCELAMENTOS", "NOVOS_PACIENTES", "FATURAMENTO", "A_RECEBER"}) {
                JsonNode vazio = ok(metrica, "MES");
                assertEquals(0, vazio.get("total").decimalValue().compareTo(BigDecimal.ZERO), metrica);
                assertTrue(vazio.get("variacaoPercentual").isNull(), metrica);
                for (JsonNode p : vazio.get("pontos")) {
                    assertTrue(p.get("semExpediente").booleanValue(), metrica + ": clínica vazia, nenhum dia tem expediente");
                    assertTrue(p.get("valor").isNull(), metrica);
                }
                JsonNode ano = ok(metrica, "ANO");
                assertEquals(0, ponto(ano, "2026-01-01").get("valor").decimalValue().compareTo(BigDecimal.ZERO), metrica + ": mês sem nada vale 0");
                assertFalse(ponto(ano, "2026-01-01").get("semExpediente").booleanValue(), metrica);
                assertEquals(java.util.List.of(2026), java.util.stream.StreamSupport.stream(vazio.get("anosDisponiveis").spliterator(), false)
                        .map(JsonNode::intValue).toList(), metrica);
            }
        } finally {
            ReflectionTestUtils.setField(service, "relogio", relogioOriginal);
            mongo.getDb().drop();
        }
    }
}

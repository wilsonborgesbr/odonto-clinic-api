package com.example.demo.repository;

import com.example.demo.enums.StatusAgendamentoEnum;
import com.example.demo.model.Agendamento;
import com.example.demo.service.AgendamentoService;
import com.example.demo.service.TenantTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.TimeZone;

import static com.example.demo.service.TenantTestSupport.TEST_CLINICA_ID;
import static org.junit.jupiter.api.Assertions.*;

// Usa um banco temporário próprio (derrubado ao final); é ignorado quando não há Mongo local.
@SpringBootTest(properties = "spring.mongodb.uri=mongodb://localhost:27017/tmp_teste_agendamentos_periodo")
@EnabledIf("mongoLocalDisponivel")
class AgendamentoPeriodoMongoLocalTest {

    static boolean mongoLocalDisponivel() {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("localhost", 27017), 500);
            return true;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    @Autowired MongoTemplate mongo;
    @Autowired AgendamentoService service;

    private void salvar(String clinicaId, String obs, LocalDateTime inicio) {
        Agendamento a = new Agendamento();
        a.setClinicaId(clinicaId);
        a.setPacienteId("p");
        a.setDentistaId("d");
        a.setDataHoraInicio(inicio);
        a.setDataHoraFim(inicio.plusMinutes(30));
        a.setStatus(StatusAgendamentoEnum.AGENDADO);
        a.setObservacoes(obs);
        mongo.save(a);
    }

    private List<String> obs(Page<Agendamento> pagina) {
        return pagina.map(Agendamento::getObservacoes).getContent();
    }

    private void semear() {
        mongo.getDb().drop();
        salvar(TEST_CLINICA_ID, "set-30-23h59", LocalDateTime.of(2026, 9, 30, 23, 59, 59));
        salvar(TEST_CLINICA_ID, "out-01-00h00", LocalDateTime.of(2026, 10, 1, 0, 0));
        salvar(TEST_CLINICA_ID, "out-15-10h", LocalDateTime.of(2026, 10, 15, 10, 0));
        salvar(TEST_CLINICA_ID, "out-31-23h59", LocalDateTime.of(2026, 10, 31, 23, 59, 59));
        salvar(TEST_CLINICA_ID, "nov-01-00h00", LocalDateTime.of(2026, 11, 1, 0, 0));
        salvar("outra-clinica", "outra-clinica-out-15", LocalDateTime.of(2026, 10, 15, 11, 0));
    }

    @Test
    void filtroPorDiaEInclusivoNasDuasPontasIsolaClinicaEFuncionaComJvmEmQualquerFuso() {
        assertEquals("tmp_teste_agendamentos_periodo", mongo.getDb().getName());
        TimeZone original = TimeZone.getDefault();
        try {
            TenantTestSupport.loginTestUser();
            // O conversor do Spring grava e consulta com o fuso da JVM: o resultado não pode depender dele.
            for (String fuso : new String[]{"UTC", "America/Sao_Paulo", "Pacific/Kiritimati"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(fuso));
                semear();
                var pagina = PageRequest.of(0, 300, Sort.by("dataHoraInicio", "id"));

                assertEquals(List.of("out-01-00h00", "out-15-10h", "out-31-23h59"),
                        obs(service.listarTodos(pagina, "2026-10-01", "2026-10-31")), fuso);
                assertEquals(List.of("out-15-10h"), obs(service.listarTodos(pagina, "2026-10-15", "2026-10-15")), fuso);
                assertEquals(List.of("out-31-23h59", "nov-01-00h00"),
                        obs(service.listarTodos(pagina, "2026-10-31", null)), fuso);
                assertEquals(List.of("set-30-23h59", "out-01-00h00"),
                        obs(service.listarTodos(pagina, null, "2026-10-01")), fuso);
                assertEquals(5, service.listarTodos(pagina, null, null).getTotalElements(), fuso);
                assertEquals(List.of(), obs(service.listarTodos(pagina, "2027-01-01", "2027-01-31")), fuso);
            }
        } finally {
            TimeZone.setDefault(original);
            TenantTestSupport.logout();
            mongo.getDb().drop();
        }
    }

    @Test
    void paginacaoDentroDoPeriodoNaoRepeteNemPerdeAgendamentosNoMesmoHorario() {
        try {
            TenantTestSupport.loginTestUser();
            mongo.getDb().drop();
            LocalDateTime mesmoHorario = LocalDateTime.of(2026, 10, 15, 9, 0);
            for (int i = 0; i < 7; i++) {
                salvar(TEST_CLINICA_ID, "ag-" + i, mesmoHorario);
            }
            var ordenacao = Sort.by("dataHoraInicio", "id");

            java.util.Set<String> vistos = new java.util.HashSet<>();
            for (int p = 0; p < 4; p++) {
                var pagina = service.listarTodos(PageRequest.of(p, 2, ordenacao), "2026-10-15", "2026-10-15");
                assertEquals(7, pagina.getTotalElements());
                vistos.addAll(obs(pagina));
            }
            assertEquals(7, vistos.size(), "toda página deve trazer agendamentos diferentes");
        } finally {
            TenantTestSupport.logout();
            mongo.getDb().drop();
        }
    }

    @Test
    void periodoDeUmMesTrazMaisQueOsTrezentosAntigosDoLimiteAntigoDoPainel() {
        try {
            TenantTestSupport.loginTestUser();
            mongo.getDb().drop();
            for (int i = 0; i < 350; i++) {
                salvar(TEST_CLINICA_ID, "antigo-" + i, LocalDateTime.of(2025, 1, 1, 8, 0).plusHours(i));
            }
            salvar(TEST_CLINICA_ID, "hoje", LocalDateTime.of(2026, 10, 23, 14, 0));

            var primeiros300 = service.listarTodos(PageRequest.of(0, 300, Sort.by("dataHoraInicio", "id")));
            assertFalse(obs(primeiros300).contains("hoje"), "sem filtro, o painel antigo não enxergava o dia atual");

            var hoje = service.listarTodos(PageRequest.of(0, 300, Sort.by("dataHoraInicio", "id")), "2026-10-23", "2026-10-23");
            assertEquals(List.of("hoje"), obs(hoje));
        } finally {
            TenantTestSupport.logout();
            mongo.getDb().drop();
        }
    }
}

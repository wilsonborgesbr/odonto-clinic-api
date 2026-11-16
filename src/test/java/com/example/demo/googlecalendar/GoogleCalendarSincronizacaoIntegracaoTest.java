package com.example.demo.googlecalendar;

import com.example.demo.email.EmailService;
import com.example.demo.enums.EscopoGoogleCalendarEnum;
import com.example.demo.enums.PermissaoEnum;
import com.example.demo.enums.RoleEnum;
import com.example.demo.enums.StatusAgendamentoEnum;
import com.example.demo.enums.SyncStatusGoogleEnum;
import com.example.demo.googlecalendar.GcalTestSupport.Clinica;
import com.example.demo.googlecalendar.GcalTestSupport.Usuario;
import com.example.demo.model.Agendamento;
import com.example.demo.model.GoogleCalendarConexao;
import com.example.demo.model.GoogleCalendarEvento;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.TokenService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.example.demo.googlecalendar.GcalTestSupport.aguardar;
import static com.example.demo.googlecalendar.GcalTestSupport.esperarSemMudanca;
import static com.example.demo.googlecalendar.GcalTestSupport.json;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integração da sincronização Bokka ⇄ Google (MockMvc + Mongo local em banco temporário próprio, derrubado ao
 * final; Google substituído por um duplo em memória). Ignorado sem Mongo em localhost:27017.
 */
@EnabledIf("mongoLocalDisponivel")
@SpringBootTest(properties = {
        "spring.mongodb.uri=mongodb://localhost:27017/tmp_teste_google_calendar_sync",
        "app.frontend-url=https://bokka.com.br",
        "google.calendar.client-id=test-client-id.apps.googleusercontent.com",
        "google.calendar.client-secret=test-secret",
        "google.calendar.webhook-url=https://api.bokka.com.br/api/public/webhooks/google-calendar",
        "google.calendar.api-retry-wait=PT0S",
        "google.calendar.callback-max-per-ip=100000"})
@AutoConfigureMockMvc
class GoogleCalendarSincronizacaoIntegracaoTest {

    static boolean mongoLocalDisponivel() {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("localhost", 27017), 500);
            return true;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        FakeGoogleCalendarApi fakeGoogleCalendarApi() {
            return new FakeGoogleCalendarApi();
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    MongoTemplate mongo;
    @Autowired
    TokenService tokenService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    FakeGoogleCalendarApi fake;
    @Autowired
    CanalWatchService canalService;
    @MockitoBean
    EmailService emailService;

    GcalTestSupport s;

    @AfterAll
    static void derrubarBanco(@Autowired MongoTemplate mongo) {
        mongo.getDb().drop();
    }

    @BeforeEach
    void preparar() {
        s = new GcalTestSupport(mvc, mongo, tokenService, userRepository);
        fake.limpar();
    }

    private static final Set<PermissaoEnum> COM_GOOGLE = EnumSet.of(PermissaoEnum.DASHBOARD, PermissaoEnum.AGENDAMENTOS,
            PermissaoEnum.INTEGRACAO_GOOGLE_CALENDAR);
    private static final LocalDateTime DIA = LocalDateTime.of(2030, 3, 12, 0, 0);

    private static LocalDateTime h(int hora, int minuto) {
        return DIA.withHour(hora).withMinute(minuto);
    }

    // ───────────── cenário ─────────────

    /**
     * Clínica cujo dono (também dentista "Dr. Carlos Lima") conectou a própria conta Google com escopo
     * TODOS_DA_CLINICA: a agenda dele recebe os agendamentos de todos os dentistas.
     */
    private record Cenario(Clinica clinica, String googleDono, String conexaoDono, String paciente, String dentista) {}

    private Cenario cenario() throws Exception {
        Clinica c = s.registrar();
        definirEscopo(c.donoId(), EscopoGoogleCalendarEnum.TODOS_DA_CLINICA);
        String google = "dono-" + GcalTestSupport.unico() + "@gmail.com";
        String conexao = conectarEEsperar(new Usuario(c.donoId(), c.donoToken()), c, google);
        return new Cenario(c, google, conexao, s.criarPaciente(c, "Maria Aparecida de Souza"), c.donoDentistaId());
    }

    private void definirEscopo(String userId, EscopoGoogleCalendarEnum escopo) {
        mongo.updateFirst(new Query(Criteria.where("id").is(userId)), new Update().set("escopoGoogleCalendar", escopo), User.class);
    }

    private String conectarEEsperar(Usuario u, Clinica c, String google) throws Exception {
        String id = s.conectar(u, c, google);
        aguardar("canal e syncToken", () -> {
            GoogleCalendarConexao con = s.conexao(id);
            return con.getWebhookWatch() != null && con.getSyncToken() != null;
        });
        return id;
    }

    private record Profissional(Usuario usuario, String dentistaId, String google, String conexaoId) {}

    private Profissional profissional(Clinica c, String nome) throws Exception {
        String dentistaId = s.criarDentista(c, nome, true);
        Usuario u = s.criarUsuario(c, RoleEnum.DENTISTA, COM_GOOGLE, dentistaId);
        String google = "prof-" + GcalTestSupport.unico() + "@gmail.com";
        return new Profissional(u, dentistaId, google, conectarEEsperar(u, c, google));
    }

    private String corpoAgendamento(String paciente, String dentista, LocalDateTime ini, LocalDateTime fim, String status, String extras) {
        return "{\"pacienteId\":\"" + paciente + "\",\"dentistaId\":\"" + dentista + "\",\"dataHoraInicio\":\"" + ini
                + "\",\"dataHoraFim\":\"" + fim + "\",\"status\":\"" + status + "\"" + (extras == null ? "" : "," + extras) + "}";
    }

    private String criarAgendamento(Clinica c, String paciente, String dentista, LocalDateTime ini, LocalDateTime fim, String extras) throws Exception {
        MvcResult r = s.post(c.donoToken(), "/api/agendamentos", corpoAgendamento(paciente, dentista, ini, fim, "AGENDADO", extras));
        assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        return json(r).get("id").asString();
    }

    private MvcResult editarAgendamento(Clinica c, String id, String paciente, String dentista, LocalDateTime ini, LocalDateTime fim, String status) throws Exception {
        return s.put(c.donoToken(), "/api/agendamentos/" + id, corpoAgendamento(paciente, dentista, ini, fim, status, null));
    }

    private Agendamento agendamento(String id) {
        return mongo.findById(id, Agendamento.class);
    }

    private static String eventoId(String conexaoId, String agendamentoId) {
        return SincronizacaoSaidaService.idDoEvento(conexaoId, agendamentoId);
    }

    private FakeGoogleCalendarApi.Armazenado esperarEvento(String conexaoId, String agendamentoId) throws Exception {
        String eid = eventoId(conexaoId, agendamentoId);
        aguardar("evento " + eid, () -> fake.evento(eid) != null && !fake.evento(eid).cancelado);
        return fake.evento(eid);
    }

    private MvcResult notificar(String conexaoId, String estado) throws Exception {
        GoogleCalendarConexao.WebhookWatch w = s.conexao(conexaoId).getWebhookWatch();
        return mvc.perform(MockMvcRequestBuilders.post("/api/public/webhooks/google-calendar")
                .header("X-Goog-Channel-ID", w.getChannelId())
                .header("X-Goog-Resource-ID", w.getResourceId())
                .header("X-Goog-Channel-Token", canalService.tokenDoCanal(w.getChannelId()))
                .header("X-Goog-Resource-State", estado)).andReturn();
    }

    // ═════════════ Bokka → Google ═════════════

    @Test
    void criar_EventoComNomeCompletoProcedimentoLinkEPropriedadePrivada_SemObservacoes() throws Exception {
        Cenario k = cenario();

        String id = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(14, 0), h(14, 30),
                "\"tipoAgendamento\":\"CONSULTA\",\"nomeProcedimento\":\"PROFILAXIA\",\"observacoes\":\"SEGREDO CLINICO DO PACIENTE\"");
        var ev = esperarEvento(k.conexaoDono(), id);

        assertEquals("Maria Aparecida de Souza — Profilaxia", ev.titulo);
        assertTrue(ev.descricao.contains("Paciente: Maria Aparecida de Souza"), ev.descricao);
        assertTrue(ev.descricao.contains("Procedimento: Profilaxia"), ev.descricao);
        assertTrue(ev.descricao.contains("Dentista: Dr. Carlos Lima"), ev.descricao);
        assertTrue(ev.descricao.contains("https://bokka.com.br/pacientes/" + k.paciente()), "link de volta ao prontuário: " + ev.descricao);
        assertFalse((ev.titulo + ev.descricao).contains("SEGREDO"), "observações nunca vão para o Google");
        assertEquals(id, ev.privadas.get("bokkaAgendamentoId"));
        assertEquals(h(14, 0), ev.inicio);
        assertEquals(h(14, 30), ev.fim);
        assertEquals(k.googleDono(), ev.conta);
        GoogleCalendarEvento elo = mongo.findOne(new Query(Criteria.where("conexaoId").is(k.conexaoDono()).and("agendamentoId").is(id)), GoogleCalendarEvento.class);
        assertNotNull(elo);
        assertEquals(ev.id, elo.getGoogleEventId());
        assertEquals(SincronizacaoSaidaService.horario(h(14, 0), h(14, 30)), elo.getUltimoHorarioEnviado());
    }

    @Test
    void criar_SemNomeDeProcedimento_UsaOTipoDoAgendamento() throws Exception {
        Cenario k = cenario();

        String id = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(9, 0), h(9, 30), "\"tipoAgendamento\":\"RETORNO\"");

        assertEquals("Maria Aparecida de Souza — Retorno", esperarEvento(k.conexaoDono(), id).titulo);
    }

    @Test
    void editar_HorarioAtualizaOMesmoEvento_SemDuplicar() throws Exception {
        Cenario k = cenario();
        String id = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(14, 0), h(14, 30), null);
        esperarEvento(k.conexaoDono(), id);

        assertEquals(200, editarAgendamento(k.clinica(), id, k.paciente(), k.dentista(), h(15, 0), h(15, 45), "CONFIRMADO").getResponse().getStatus());

        String eid = eventoId(k.conexaoDono(), id);
        aguardar("evento remarcado", () -> h(15, 0).equals(fake.evento(eid).inicio));
        assertEquals(h(15, 45), fake.evento(eid).fim);
        assertEquals(1, fake.eventosDa(k.googleDono()).size(), "um agendamento, um evento");
        assertEquals(1, fake.contar("insert:" + eid));
    }

    @Test
    void editar_SemMudarONadaVisivel_NaoReenviaAoGoogle() throws Exception {
        Cenario k = cenario();
        String id = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(14, 0), h(14, 30), null);
        esperarEvento(k.conexaoDono(), id);
        String eid = eventoId(k.conexaoDono(), id);

        editarAgendamento(k.clinica(), id, k.paciente(), k.dentista(), h(14, 0), h(14, 30), "CONFIRMADO");
        esperarSemMudanca();

        assertEquals(0, fake.contar("update:" + eid), "status CONFIRMADO não muda o evento");
    }

    @Test
    void cancelar_ExcluiOEventoERemoveOElo_ReativarORecria() throws Exception {
        Cenario k = cenario();
        String id = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(14, 0), h(14, 30), null);
        esperarEvento(k.conexaoDono(), id);
        String eid = eventoId(k.conexaoDono(), id);

        editarAgendamento(k.clinica(), id, k.paciente(), k.dentista(), h(14, 0), h(14, 30), "CANCELADO");

        aguardar("evento excluído", () -> fake.evento(eid).cancelado);
        aguardar("elo removido", () -> mongo.count(new Query(Criteria.where("agendamentoId").is(id)), GoogleCalendarEvento.class) == 0);

        editarAgendamento(k.clinica(), id, k.paciente(), k.dentista(), h(14, 0), h(14, 30), "AGENDADO");

        aguardar("evento restaurado", () -> !fake.evento(eid).cancelado);
        assertEquals(1, fake.eventosAtivosDa(k.googleDono()).size());
        assertEquals(1, mongo.count(new Query(Criteria.where("agendamentoId").is(id)), GoogleCalendarEvento.class));
    }

    @Test
    void excluir_ApagaOEventoNoGoogle() throws Exception {
        Cenario k = cenario();
        String id = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(14, 0), h(14, 30), null);
        esperarEvento(k.conexaoDono(), id);
        String eid = eventoId(k.conexaoDono(), id);

        assertEquals(204, s.delete(k.clinica().donoToken(), "/api/agendamentos/" + id).getResponse().getStatus());

        aguardar("evento excluído", () -> fake.evento(eid).cancelado);
        aguardar("elo removido", () -> mongo.count(new Query(Criteria.where("agendamentoId").is(id)), GoogleCalendarEvento.class) == 0);
    }

    // ═════════════ desconectar apaga tudo no Google ═════════════

    @Test
    void desconectar_ApagaNoGoogleTodosOsEventosDaClinica_AntesDeResponder() throws Exception {
        Cenario k = cenario();
        String a = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(9, 0), h(9, 30), null);
        String b = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(10, 0), h(10, 30), null);
        String c = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(11, 0), h(11, 30), null);
        for (String id : List.of(a, b, c)) {
            esperarEvento(k.conexaoDono(), id);
        }
        assertEquals(3, fake.eventosAtivosDa(k.googleDono()).size());

        MvcResult r = s.post(k.clinica().donoToken(), "/api/google-calendar/desconectar", null);

        // Sem aguardar: quando a resposta volta, os eventos já saíram da agenda Google.
        assertEquals(204, r.getResponse().getStatus());
        assertEquals(0, fake.eventosAtivosDa(k.googleDono()).size(), "nenhum evento da clínica pode sobrar na conta Google");
        assertNull(s.conexao(k.conexaoDono()));
        assertEquals(0, mongo.count(new Query(Criteria.where("conexaoId").is(k.conexaoDono())), GoogleCalendarEvento.class));
        assertNotNull(agendamento(a), "o agendamento no Bokka continua");
    }

    @Test
    void desconectar_UmaFalhaNaExclusaoNaoImpedeOsDemaisNemADesconexao() throws Exception {
        Cenario k = cenario();
        String a = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(9, 0), h(9, 30), null);
        String b = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(10, 0), h(10, 30), null);
        String c = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(11, 0), h(11, 30), null);
        for (String id : List.of(a, b, c)) {
            esperarEvento(k.conexaoDono(), id);
        }
        String eidB = eventoId(k.conexaoDono(), b);
        fake.falharExclusaoDe(eidB, new GoogleApiException(403, "forbidden"));

        MvcResult r = s.post(k.clinica().donoToken(), "/api/google-calendar/desconectar", null);

        assertEquals(204, r.getResponse().getStatus(), "a desconexão conclui mesmo com uma falha no Google");
        assertTrue(fake.evento(eventoId(k.conexaoDono(), a)).cancelado);
        assertTrue(fake.evento(eventoId(k.conexaoDono(), c)).cancelado);
        assertFalse(fake.evento(eidB).cancelado, "o que falhou fica no Google (e foi registrado em log)");
        assertNull(s.conexao(k.conexaoDono()));
        assertEquals(0, mongo.count(new Query(Criteria.where("conexaoId").is(k.conexaoDono())), GoogleCalendarEvento.class));
    }

    @Test
    void desconectar_ApagaTambemEventosComVinculoEmRemocao() throws Exception {
        Cenario k = cenario();
        String a = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(9, 0), h(9, 30), null);
        esperarEvento(k.conexaoDono(), a);
        mongo.updateFirst(new Query(Criteria.where("agendamentoId").is(a)), new Update().set("removendo", true), GoogleCalendarEvento.class);

        assertEquals(204, s.post(k.clinica().donoToken(), "/api/google-calendar/desconectar", null).getResponse().getStatus());

        assertEquals(0, fake.eventosAtivosDa(k.googleDono()).size());
    }

    @Test
    void desconectar_SoApagaOsEventosDaConexaoDesconectada() throws Exception {
        Cenario k = cenario();
        Profissional ana = profissional(k.clinica(), "Dra. Ana");
        String id = criarAgendamento(k.clinica(), k.paciente(), ana.dentistaId(), h(9, 0), h(9, 30), null);
        esperarEvento(k.conexaoDono(), id);
        esperarEvento(ana.conexaoId(), id);

        assertEquals(204, s.post(ana.usuario().token(), "/api/google-calendar/desconectar", null).getResponse().getStatus());

        assertEquals(0, fake.eventosAtivosDa(ana.google()).size());
        assertEquals(1, fake.eventosAtivosDa(k.googleDono()).size(), "a agenda geral da clínica não é afetada");
    }

    @Test
    void pausarPelaRetiradaDePermissao_NaoApagaEventos() throws Exception {
        Cenario k = cenario();
        Profissional ana = profissional(k.clinica(), "Dra. Ana");
        String id = criarAgendamento(k.clinica(), k.paciente(), ana.dentistaId(), h(9, 0), h(9, 30), null);
        esperarEvento(ana.conexaoId(), id);
        User alvo = userRepository.findById(ana.usuario().id()).orElseThrow();

        s.put(k.clinica().donoToken(), "/api/usuarios/" + ana.usuario().id(),
                "{\"name\":\"" + alvo.getName() + "\",\"email\":\"" + alvo.getEmail() + "\",\"role\":\"DENTISTA\",\"permissoes\":[\"DASHBOARD\"]}");
        esperarSemMudanca();

        assertEquals(SyncStatusGoogleEnum.PAUSADO_PELO_DONO, s.conexao(ana.conexaoId()).getSyncStatus());
        assertEquals(1, fake.eventosAtivosDa(ana.google()).size(), "pausar é temporário: os eventos ficam");
        assertEquals(1, mongo.count(new Query(Criteria.where("conexaoId").is(ana.conexaoId())), GoogleCalendarEvento.class));
    }

    @Test
    void desconectarEReconectar_NaoDuplicaEventosFuturos_VinculoNovoDoZero() throws Exception {
        Cenario k = cenario();
        String a = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(9, 0), h(9, 30), null);
        String b = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(10, 0), h(10, 30), null);
        esperarEvento(k.conexaoDono(), a);
        esperarEvento(k.conexaoDono(), b);
        String antigoA = eventoId(k.conexaoDono(), a);
        String antigoB = eventoId(k.conexaoDono(), b);

        assertEquals(204, s.post(k.clinica().donoToken(), "/api/google-calendar/desconectar", null).getResponse().getStatus());
        assertEquals(0, fake.eventosAtivosDa(k.googleDono()).size());

        String novaConexao = conectarEEsperar(new Usuario(k.clinica().donoId(), k.clinica().donoToken()), k.clinica(), k.googleDono());
        assertNotEquals(k.conexaoDono(), novaConexao, "reconectar gera conexão nova");
        assertEquals(0, mongo.count(new Query(Criteria.where("agendamentoId").in(a, b)), GoogleCalendarEvento.class),
                "nenhum vínculo antigo sobrevive");

        // Dispara a sincronização dos mesmos agendamentos futuros.
        editarAgendamento(k.clinica(), a, k.paciente(), k.dentista(), h(9, 0), h(9, 45), "AGENDADO");
        editarAgendamento(k.clinica(), b, k.paciente(), k.dentista(), h(10, 0), h(10, 45), "AGENDADO");
        esperarEvento(novaConexao, a);
        esperarEvento(novaConexao, b);
        esperarSemMudanca();

        assertEquals(2, fake.eventosAtivosDa(k.googleDono()).size(), "um evento por agendamento, sem duplicar");
        assertTrue(fake.evento(antigoA).cancelado, "o evento antigo continua apagado");
        assertTrue(fake.evento(antigoB).cancelado);
        for (String id : List.of(a, b)) {
            List<GoogleCalendarEvento> elos = mongo.find(new Query(Criteria.where("agendamentoId").is(id)), GoogleCalendarEvento.class);
            assertEquals(1, elos.size());
            assertEquals(novaConexao, elos.get(0).getConexaoId());
            assertEquals(eventoId(novaConexao, id), elos.get(0).getGoogleEventId());
            assertNotEquals(eventoId(k.conexaoDono(), id), elos.get(0).getGoogleEventId(), "id novo, sem reaproveitar o antigo");
        }
    }

    // ═════════════ última sincronização e "sincronizar agora" ═════════════

    private tools.jackson.databind.JsonNode conexaoNoStatus(String token) throws Exception {
        return json(s.get(token, "/api/google-calendar/status")).get("conexao");
    }

    private void aguardarFimDaSincronizacaoManual(String token) throws Exception {
        aguardar("sincronização manual terminar", () -> {
            try {
                return !conexaoNoStatus(token).get("sincronizando").asBoolean();
            } catch (Exception e) {
                return false;
            }
        });
    }

    @Test
    void status_MostraUltimaSincronizacao_VaziaAteAPrimeiraSincronizacao() throws Exception {
        Cenario k = cenario();
        assertFalse(conexaoNoStatus(k.clinica().donoToken()).hasNonNull("ultimaSincronizacaoEm"));
        assertFalse(conexaoNoStatus(k.clinica().donoToken()).get("sincronizando").asBoolean());
        assertFalse(conexaoNoStatus(k.clinica().donoToken()).get("ultimaSincronizacaoFalhou").asBoolean());

        String id = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(10, 0), h(10, 30), null);
        esperarEvento(k.conexaoDono(), id);
        aguardar("timestamp gravado", () -> s.conexao(k.conexaoDono()).getUltimaSincronizacaoEm() != null);

        assertTrue(conexaoNoStatus(k.clinica().donoToken()).hasNonNull("ultimaSincronizacaoEm"));
    }

    @Test
    void agendamentoDeOutroDentista_NaoAtualizaAUltimaSincronizacaoDaConexaoIndividual() throws Exception {
        Cenario k = cenario();
        Profissional ana = profissional(k.clinica(), "Dra. Ana");
        String outro = k.dentista();
        String id = criarAgendamento(k.clinica(), k.paciente(), outro, h(10, 0), h(10, 30), null);
        esperarEvento(k.conexaoDono(), id);
        esperarSemMudanca();

        assertNull(s.conexao(ana.conexaoId()).getUltimaSincronizacaoEm(), "nada daquele agendamento diz respeito à agenda da Dra. Ana");
    }

    @Test
    void sincronizarAgora_ReenviaOsAgendamentosFuturosQueNaoChegaramAoGoogle() throws Exception {
        Cenario k = cenario();
        fake.falharEscritas(new GoogleApiException(500, "backendError"));
        String a = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(9, 0), h(9, 30), null);
        String b = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(10, 0), h(10, 30), null);
        aguardar("tentativas esgotadas", () -> fake.contar("insert:" + eventoId(k.conexaoDono(), b)) == 3);
        assertEquals(0, fake.eventosAtivosDa(k.googleDono()).size());
        fake.limpar();

        MvcResult r = s.post(k.clinica().donoToken(), "/api/google-calendar/sincronizar", null);

        assertEquals(202, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        assertTrue(json(r).get("iniciada").asBoolean());
        assertFalse(json(r).get("jaEmAndamento").asBoolean());
        assertTrue(json(r).hasNonNull("iniciadaEm"));
        aguardarFimDaSincronizacaoManual(k.clinica().donoToken());
        esperarEvento(k.conexaoDono(), a);
        esperarEvento(k.conexaoDono(), b);
        assertEquals(2, fake.eventosAtivosDa(k.googleDono()).size());
        var conexao = conexaoNoStatus(k.clinica().donoToken());
        assertTrue(conexao.hasNonNull("ultimaSincronizacaoEm"));
        assertFalse(conexao.get("ultimaSincronizacaoFalhou").asBoolean());
    }

    @Test
    void sincronizarAgora_NaoReenviaOQueJaEstaIgual_ENaoDuplica() throws Exception {
        Cenario k = cenario();
        String a = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(9, 0), h(9, 30), null);
        esperarEvento(k.conexaoDono(), a);
        String eid = eventoId(k.conexaoDono(), a);
        long inserts = fake.contar("insert:" + eid);
        long updates = fake.contar("update:" + eid);

        s.post(k.clinica().donoToken(), "/api/google-calendar/sincronizar", null);
        aguardarFimDaSincronizacaoManual(k.clinica().donoToken());

        assertEquals(1, fake.eventosAtivosDa(k.googleDono()).size());
        assertEquals(inserts, fake.contar("insert:" + eid));
        assertEquals(updates, fake.contar("update:" + eid), "conteúdo igual ao já enviado não é reenviado");
    }

    @Test
    void sincronizarAgora_ApagaDoGoogleOQueFoiCanceladoSemQueOGoogleTenhaSidoAvisado() throws Exception {
        Cenario k = cenario();
        String a = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(9, 0), h(9, 30), null);
        esperarEvento(k.conexaoDono(), a);
        // Cancelamento direto no banco: nenhum aviso foi publicado, o Google ainda tem o evento.
        mongo.updateFirst(new Query(Criteria.where("id").is(a)), new Update().set("status", StatusAgendamentoEnum.CANCELADO), Agendamento.class);

        s.post(k.clinica().donoToken(), "/api/google-calendar/sincronizar", null);

        aguardar("evento removido", () -> fake.evento(eventoId(k.conexaoDono(), a)).cancelado);
    }

    @Test
    void sincronizarAgora_FalhaNoGoogle_MarcaFalhouENaoAtualizaOHorario() throws Exception {
        Cenario k = cenario();
        String a = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(9, 0), h(9, 30), null);
        esperarEvento(k.conexaoDono(), a);
        aguardar("timestamp gravado", () -> s.conexao(k.conexaoDono()).getUltimaSincronizacaoEm() != null);
        esperarSemMudanca();
        var antes = s.conexao(k.conexaoDono()).getUltimaSincronizacaoEm();
        mongo.updateFirst(new Query(Criteria.where("id").is(a)), new Update().set("dataHoraFim", h(9, 45)), Agendamento.class);
        fake.falharEscritas(new GoogleApiException(500, "backendError"));

        MvcResult r = s.post(k.clinica().donoToken(), "/api/google-calendar/sincronizar", null);

        assertEquals(202, r.getResponse().getStatus());
        aguardarFimDaSincronizacaoManual(k.clinica().donoToken());
        var conexao = conexaoNoStatus(k.clinica().donoToken());
        assertTrue(conexao.get("ultimaSincronizacaoFalhou").asBoolean());
        assertEquals(antes, s.conexao(k.conexaoDono()).getUltimaSincronizacaoEm(), "falha não conta como sincronizada");

        fake.limpar();
        s.post(k.clinica().donoToken(), "/api/google-calendar/sincronizar", null);
        aguardarFimDaSincronizacaoManual(k.clinica().donoToken());
        assertFalse(conexaoNoStatus(k.clinica().donoToken()).get("ultimaSincronizacaoFalhou").asBoolean(), "uma sincronização bem-sucedida limpa a falha");
        assertEquals(h(9, 45), fake.evento(eventoId(k.conexaoDono(), a)).fim);
    }

    @Test
    void sincronizarAgora_BuscaTambemAsMudancasFeitasNoGoogle_MesmoSemAvisoDeWebhook() throws Exception {
        Cenario k = cenario();
        String a = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(9, 0), h(9, 30), null);
        esperarEvento(k.conexaoDono(), a);
        fake.usuarioEditaHorario(eventoId(k.conexaoDono(), a), h(15, 0), h(15, 30));
        assertEquals(h(9, 0), agendamento(a).getDataHoraInicio(), "sem notificação, o Bokka ainda não sabe");

        s.post(k.clinica().donoToken(), "/api/google-calendar/sincronizar", null);
        aguardarFimDaSincronizacaoManual(k.clinica().donoToken());

        assertEquals(h(15, 0), agendamento(a).getDataHoraInicio());
        assertEquals(h(15, 30), agendamento(a).getDataHoraFim());
        assertEquals(h(15, 0), fake.evento(eventoId(k.conexaoDono(), a)).inicio);
    }

    @Test
    void sincronizarAgora_SoComConexaoAtiva_SemConexao404_Pausada409_SemLogin401ou403() throws Exception {
        Cenario k = cenario();
        Usuario semConexao = s.criarUsuario(k.clinica(), RoleEnum.DENTISTA, COM_GOOGLE, s.criarDentista(k.clinica(), "Dr. Sem", true));
        assertEquals(404, s.post(semConexao.token(), "/api/google-calendar/sincronizar", null).getResponse().getStatus());

        Profissional ana = profissional(k.clinica(), "Dra. Ana");
        User alvo = userRepository.findById(ana.usuario().id()).orElseThrow();
        s.put(k.clinica().donoToken(), "/api/usuarios/" + ana.usuario().id(),
                "{\"name\":\"" + alvo.getName() + "\",\"email\":\"" + alvo.getEmail() + "\",\"role\":\"DENTISTA\",\"permissoes\":[\"DASHBOARD\"]}");
        assertEquals(SyncStatusGoogleEnum.PAUSADO_PELO_DONO, s.conexao(ana.conexaoId()).getSyncStatus());
        assertEquals(409, s.post(ana.usuario().token(), "/api/google-calendar/sincronizar", null).getResponse().getStatus());

        int semToken = s.post(null, "/api/google-calendar/sincronizar", null).getResponse().getStatus();
        assertTrue(semToken == 401 || semToken == 403, "sem login: " + semToken);
    }

    @Test
    void sincronizarAgora_UsaSoAConexaoDoProprioUsuario_ENaoMexeNaDeOutraClinica() throws Exception {
        Cenario k = cenario();
        Cenario outra = cenario();
        fake.falharEscritas(new GoogleApiException(500, "backendError"));
        String meu = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(9, 0), h(9, 30), null);
        String dela = criarAgendamento(outra.clinica(), outra.paciente(), outra.dentista(), h(9, 0), h(9, 30), null);
        aguardar("tentativas", () -> fake.contar("insert:" + eventoId(k.conexaoDono(), meu)) == 3
                && fake.contar("insert:" + eventoId(outra.conexaoDono(), dela)) == 3);
        fake.limpar();

        s.post(k.clinica().donoToken(), "/api/google-calendar/sincronizar", null);
        aguardarFimDaSincronizacaoManual(k.clinica().donoToken());

        assertEquals(1, fake.eventosAtivosDa(k.googleDono()).size());
        assertEquals(0, fake.eventosAtivosDa(outra.googleDono()).size(), "a outra clínica não foi tocada");
    }

    @Test
    void sincronizarAgora_JaEmAndamento_NaoIniciaOutra_ERetomaDepoisDeUmaTravaVelha() throws Exception {
        Cenario k = cenario();
        mongo.updateFirst(new Query(Criteria.where("id").is(k.conexaoDono())),
                new Update().set("sincronizandoDesde", LocalDateTime.now()), GoogleCalendarConexao.class);

        MvcResult r = s.post(k.clinica().donoToken(), "/api/google-calendar/sincronizar", null);

        assertEquals(202, r.getResponse().getStatus());
        assertFalse(json(r).get("iniciada").asBoolean());
        assertTrue(json(r).get("jaEmAndamento").asBoolean());
        assertTrue(conexaoNoStatus(k.clinica().donoToken()).get("sincronizando").asBoolean());

        mongo.updateFirst(new Query(Criteria.where("id").is(k.conexaoDono())),
                new Update().set("sincronizandoDesde", LocalDateTime.now().minusMinutes(11)), GoogleCalendarConexao.class);
        assertFalse(conexaoNoStatus(k.clinica().donoToken()).get("sincronizando").asBoolean(), "trava de 11 min não conta mais");

        MvcResult depois = s.post(k.clinica().donoToken(), "/api/google-calendar/sincronizar", null);

        assertTrue(json(depois).get("iniciada").asBoolean());
        aguardarFimDaSincronizacaoManual(k.clinica().donoToken());
        assertTrue(conexaoNoStatus(k.clinica().donoToken()).hasNonNull("ultimaSincronizacaoEm"));
    }

    // ═════════════ escopo da agenda (todos da clínica x só os meus) ═════════════

    private MvcResult definirEscopoPelaApi(String tokenDeQuemEdita, String userId, RoleEnum role, EscopoGoogleCalendarEnum escopo) throws Exception {
        User alvo = userRepository.findById(userId).orElseThrow();
        String campo = escopo == null ? "" : ",\"escopoGoogleCalendar\":\"" + escopo + "\"";
        return s.put(tokenDeQuemEdita, "/api/usuarios/" + userId,
                "{\"name\":\"" + alvo.getName() + "\",\"email\":\"" + alvo.getEmail() + "\",\"role\":\"" + role
                        + "\",\"permissoes\":[\"DASHBOARD\",\"AGENDAMENTOS\",\"INTEGRACAO_GOOGLE_CALENDAR\"]" + campo + "}");
    }

    @Test
    void escopoTodos_RecepcaoSemDentista_RecebeTudo_CadaPessoaNaPropriaConta_SemCompartilhar() throws Exception {
        Cenario k = cenario();
        Profissional ana = profissional(k.clinica(), "Dra. Ana");
        Usuario recepcao = s.criarUsuario(k.clinica(), RoleEnum.RECEPCIONISTA, COM_GOOGLE, null);
        String googleRecepcao = "recepcao-" + GcalTestSupport.unico() + "@gmail.com";

        assertEquals("SEM_DENTISTA_VINCULADO", json(s.get(recepcao.token(), "/api/google-calendar/status")).get("motivoBloqueio").asString(),
                "sem escopo de clínica, quem não tem dentista não conecta");
        assertEquals(400, s.get(recepcao.token(), "/api/google-calendar/auth-url").getResponse().getStatus());

        assertEquals(200, definirEscopoPelaApi(k.clinica().donoToken(), recepcao.id(), RoleEnum.RECEPCIONISTA, EscopoGoogleCalendarEnum.TODOS_DA_CLINICA).getResponse().getStatus());
        assertTrue(json(s.get(recepcao.token(), "/api/google-calendar/status")).get("podeConectar").asBoolean());
        String conexaoRecepcao = conectarEEsperar(recepcao, k.clinica(), googleRecepcao);

        GoogleCalendarConexao conexao = s.conexao(conexaoRecepcao);
        assertEquals(EscopoGoogleCalendarEnum.TODOS_DA_CLINICA, conexao.getEscopo());
        assertNull(conexao.getDentistaId());

        String id = criarAgendamento(k.clinica(), k.paciente(), ana.dentistaId(), h(10, 0), h(10, 30), null);
        esperarEvento(conexaoRecepcao, id);
        esperarEvento(ana.conexaoId(), id);
        esperarEvento(k.conexaoDono(), id);

        // Três pessoas, três contas Google, três eventos (um por conexão): intencional, não é duplicação.
        assertEquals(1, fake.eventosAtivosDa(googleRecepcao).size());
        assertEquals(1, fake.eventosAtivosDa(ana.google()).size());
        assertEquals(1, fake.eventosAtivosDa(k.googleDono()).size());
        assertEquals(3, java.util.stream.Stream.of(conexaoRecepcao, ana.conexaoId(), k.conexaoDono()).map(c -> eventoId(c, id)).distinct().count());
        assertEquals(3, mongo.count(new Query(Criteria.where("agendamentoId").is(id)), GoogleCalendarEvento.class));

        // Desconectar a Ana só apaga o que é dela.
        assertEquals(204, s.post(ana.usuario().token(), "/api/google-calendar/desconectar", null).getResponse().getStatus());
        assertEquals(0, fake.eventosAtivosDa(ana.google()).size());
        assertEquals(1, fake.eventosAtivosDa(googleRecepcao).size());
        assertEquals(1, fake.eventosAtivosDa(k.googleDono()).size());
    }

    @Test
    void escopoSomenteProprio_NaoRecebeOsDeOutrosDentistas_EAmpliarTrazOsDemais() throws Exception {
        Cenario k = cenario();
        Profissional ana = profissional(k.clinica(), "Dra. Ana");
        Profissional beto = profissional(k.clinica(), "Dr. Beto");
        String doBeto = criarAgendamento(k.clinica(), k.paciente(), beto.dentistaId(), h(9, 0), h(9, 30), null);
        esperarEvento(beto.conexaoId(), doBeto);
        esperarSemMudanca();
        assertEquals(0, fake.eventosAtivosDa(ana.google()).size(), "escopo SOMENTE_PROPRIO: consulta do Beto não chega na Ana");

        assertEquals(200, definirEscopoPelaApi(k.clinica().donoToken(), ana.usuario().id(), RoleEnum.DENTISTA, EscopoGoogleCalendarEnum.TODOS_DA_CLINICA).getResponse().getStatus());

        assertEquals(EscopoGoogleCalendarEnum.TODOS_DA_CLINICA, s.conexao(ana.conexaoId()).getEscopo());
        esperarEvento(ana.conexaoId(), doBeto);
        assertEquals(1, fake.eventosAtivosDa(ana.google()).size(), "ao ampliar, o que já existia chega (em segundo plano)");
    }

    @Test
    void restringirOEscopo_ApagaDoGoogleOQueDeixouDeValer_AntesDeResponder() throws Exception {
        Cenario k = cenario();
        Profissional ana = profissional(k.clinica(), "Dra. Ana");
        Profissional beto = profissional(k.clinica(), "Dr. Beto");
        definirEscopoPelaApi(k.clinica().donoToken(), ana.usuario().id(), RoleEnum.DENTISTA, EscopoGoogleCalendarEnum.TODOS_DA_CLINICA);
        String daAna = criarAgendamento(k.clinica(), k.paciente(), ana.dentistaId(), h(9, 0), h(9, 30), null);
        String doBeto = criarAgendamento(k.clinica(), k.paciente(), beto.dentistaId(), h(10, 0), h(10, 30), null);
        esperarEvento(ana.conexaoId(), daAna);
        esperarEvento(ana.conexaoId(), doBeto);

        MvcResult r = definirEscopoPelaApi(k.clinica().donoToken(), ana.usuario().id(), RoleEnum.DENTISTA, EscopoGoogleCalendarEnum.SOMENTE_PROPRIO);

        // Sem aguardar: a restrição é de privacidade e termina antes da resposta.
        assertEquals(200, r.getResponse().getStatus());
        assertEquals(1, fake.eventosAtivosDa(ana.google()).size());
        assertTrue(fake.evento(eventoId(ana.conexaoId(), doBeto)).cancelado, "o agendamento do Beto saiu da agenda Google da Ana");
        assertFalse(fake.evento(eventoId(ana.conexaoId(), daAna)).cancelado);
        GoogleCalendarConexao conexao = s.conexao(ana.conexaoId());
        assertEquals(EscopoGoogleCalendarEnum.SOMENTE_PROPRIO, conexao.getEscopo());
        assertEquals(ana.dentistaId(), conexao.getDentistaId());
        assertEquals(0, mongo.count(new Query(Criteria.where("conexaoId").is(ana.conexaoId()).and("agendamentoId").is(doBeto)), GoogleCalendarEvento.class));
        assertNotNull(agendamento(doBeto), "o agendamento no Bokka continua");
    }

    @Test
    void soProprietarioOuSocioDefineOEscopo_AusenteNaEdicaoMantemOAtual() throws Exception {
        Cenario k = cenario();
        Usuario admin = s.criarUsuario(k.clinica(), RoleEnum.ADMINISTRADOR, EnumSet.of(PermissaoEnum.USUARIOS_E_PERMISSOES), null);
        Usuario socio = s.criarUsuario(k.clinica(), RoleEnum.SOCIO, null, null);
        Usuario recepcao = s.criarUsuario(k.clinica(), RoleEnum.RECEPCIONISTA, COM_GOOGLE, null);

        assertEquals(403, definirEscopoPelaApi(admin.token(), recepcao.id(), RoleEnum.RECEPCIONISTA, EscopoGoogleCalendarEnum.TODOS_DA_CLINICA).getResponse().getStatus(),
                "administrador não define escopo");
        assertEquals(200, definirEscopoPelaApi(admin.token(), recepcao.id(), RoleEnum.RECEPCIONISTA, EscopoGoogleCalendarEnum.SOMENTE_PROPRIO).getResponse().getStatus(),
                "repetir o valor atual não conta como mudança");
        assertEquals(200, definirEscopoPelaApi(socio.token(), recepcao.id(), RoleEnum.RECEPCIONISTA, EscopoGoogleCalendarEnum.TODOS_DA_CLINICA).getResponse().getStatus());
        assertEquals(EscopoGoogleCalendarEnum.TODOS_DA_CLINICA, userRepository.findById(recepcao.id()).orElseThrow().getEscopoGoogleCalendar());

        MvcResult semCampo = definirEscopoPelaApi(k.clinica().donoToken(), recepcao.id(), RoleEnum.RECEPCIONISTA, null);
        assertEquals(200, semCampo.getResponse().getStatus());
        assertEquals("TODOS_DA_CLINICA", json(semCampo).get("escopoGoogleCalendar").asString(), "o Frontend antigo não zera o escopo sem querer");
    }

    @Test
    void criarUsuarioComEscopo_SoOProprietarioOuSocio_EAListagemMostraOEscopo() throws Exception {
        Cenario k = cenario();
        Usuario admin = s.criarUsuario(k.clinica(), RoleEnum.ADMINISTRADOR, EnumSet.of(PermissaoEnum.USUARIOS_E_PERMISSOES), null);
        String corpo = "{\"name\":\"Recepção\",\"email\":\"rec-" + GcalTestSupport.unico() + "@teste.com\",\"password\":\"senha123\",\"role\":\"RECEPCIONISTA\","
                + "\"escopoGoogleCalendar\":\"TODOS_DA_CLINICA\"}";

        assertEquals(403, s.post(admin.token(), "/api/usuarios", corpo).getResponse().getStatus());
        MvcResult criado = s.post(k.clinica().donoToken(), "/api/usuarios", corpo);

        assertEquals(201, criado.getResponse().getStatus(), criado.getResponse().getContentAsString());
        assertEquals("TODOS_DA_CLINICA", json(criado).get("escopoGoogleCalendar").asString());
        String semEscopo = "{\"name\":\"Outro\",\"email\":\"o-" + GcalTestSupport.unico() + "@teste.com\",\"password\":\"senha123\",\"role\":\"RECEPCIONISTA\"}";
        assertEquals("SOMENTE_PROPRIO", json(s.post(k.clinica().donoToken(), "/api/usuarios", semEscopo)).get("escopoGoogleCalendar").asString(),
                "padrão seguro: só os próprios");
    }

    @Test
    void escopoSomenteProprio_SoRecebeOsAgendamentosDoProprioDentista_EAgendaMudaDeDentista() throws Exception {
        Cenario k = cenario();
        Profissional ana = profissional(k.clinica(), "Dra. Ana");
        Profissional beto = profissional(k.clinica(), "Dr. Beto");

        String id = criarAgendamento(k.clinica(), k.paciente(), ana.dentistaId(), h(10, 0), h(10, 30), null);
        esperarEvento(k.conexaoDono(), id);
        esperarEvento(ana.conexaoId(), id);
        esperarSemMudanca();
        assertEquals(0, fake.eventosDa(beto.google()).size(), "a agenda do Beto não recebe consulta da Ana");

        editarAgendamento(k.clinica(), id, k.paciente(), beto.dentistaId(), h(10, 0), h(10, 30), "AGENDADO");

        FakeGoogleCalendarApi.Armazenado novo = esperarEvento(beto.conexaoId(), id);
        aguardar("some da agenda da Ana", () -> fake.eventosAtivosDa(ana.google()).isEmpty());
        assertEquals(beto.google(), novo.conta);
        assertEquals(1, fake.eventosAtivosDa(k.googleDono()).size(), "a agenda geral mantém o evento, sem duplicar");
        assertEquals(1, mongo.count(new Query(Criteria.where("agendamentoId").is(id).and("conexaoId").is(ana.conexaoId())), GoogleCalendarEvento.class) == 0 ? 1 : 0);
    }

    @Test
    void conexaoPausada_NaoRecebeNada() throws Exception {
        Cenario k = cenario();
        Profissional ana = profissional(k.clinica(), "Dra. Ana");
        User alvo = userRepository.findById(ana.usuario().id()).orElseThrow();
        s.put(k.clinica().donoToken(), "/api/usuarios/" + ana.usuario().id(),
                "{\"name\":\"" + alvo.getName() + "\",\"email\":\"" + alvo.getEmail() + "\",\"role\":\"DENTISTA\",\"permissoes\":[\"DASHBOARD\"]}");
        assertEquals(SyncStatusGoogleEnum.PAUSADO_PELO_DONO, s.conexao(ana.conexaoId()).getSyncStatus());

        String id = criarAgendamento(k.clinica(), k.paciente(), ana.dentistaId(), h(10, 0), h(10, 30), null);
        esperarEvento(k.conexaoDono(), id);
        esperarSemMudanca();

        assertEquals(0, fake.eventosDa(ana.google()).size());
    }

    @Test
    void googleForaDoAr_NaoImpedeSalvarOAgendamento_ENaoVazaOErro_EConvergeDepois() throws Exception {
        Cenario k = cenario();
        fake.falharEscritas(new GoogleApiException(500, "backendError"));

        MvcResult r = s.post(k.clinica().donoToken(), "/api/agendamentos", corpoAgendamento(k.paciente(), k.dentista(), h(14, 0), h(14, 30), "AGENDADO", null));

        assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        assertFalse(r.getResponse().getContentAsString().contains("Google"), "nenhum detalhe da integração na resposta");
        String id = json(r).get("id").asString();
        String eid = eventoId(k.conexaoDono(), id);
        aguardar("3 tentativas", () -> fake.contar("insert:" + eid) == 3);
        assertNull(fake.evento(eid));
        assertEquals(0, mongo.count(new Query(Criteria.where("agendamentoId").is(id)), GoogleCalendarEvento.class));

        fake.limpar();
        editarAgendamento(k.clinica(), id, k.paciente(), k.dentista(), h(14, 0), h(15, 0), "AGENDADO");

        FakeGoogleCalendarApi.Armazenado ev = esperarEvento(k.conexaoDono(), id);
        assertEquals(h(15, 0), ev.fim, "a próxima edição leva o estado atual completo");
    }

    @Test
    void erroDeClienteNoGoogle_NaoRepete() throws Exception {
        Cenario k = cenario();
        fake.falharEscritas(new GoogleApiException(400, "badRequest"));

        String id = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(14, 0), h(14, 30), null);
        String eid = eventoId(k.conexaoDono(), id);
        aguardar("uma tentativa", () -> fake.contar("insert:" + eid) >= 1);
        esperarSemMudanca();

        assertEquals(1, fake.contar("insert:" + eid), "400 não é transitório: sem nova tentativa");
    }

    @Test
    void acessoRevogadoNoGoogle_MarcaAConexaoERespeitaOAgendamento() throws Exception {
        Cenario k = cenario();
        mongo.updateFirst(new Query(Criteria.where("id").is(k.conexaoDono())),
                new Update().set("tokens.expiresAt", Instant.now().minusSeconds(60)), GoogleCalendarConexao.class);
        fake.revogarConta(k.googleDono());

        String id = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(14, 0), h(14, 30), null);

        aguardar("conexão revogada", () -> s.conexao(k.conexaoDono()).getSyncStatus() == SyncStatusGoogleEnum.REVOGADO_PELO_GOOGLE);
        assertNotNull(agendamento(id));
        assertEquals(0, fake.contar("insert:" + eventoId(k.conexaoDono(), id)), "sem token válido, nada é enviado");
        assertEquals("REVOGADO_PELO_GOOGLE", json(s.get(k.clinica().donoToken(), "/api/google-calendar/status")).get("conexao").get("syncStatus").asString());
    }

    @Test
    void tokenDeAcesso_RenovadoCincoMinutosAntesDeVencer() throws Exception {
        Cenario k = cenario();
        String antes = s.conexao(k.conexaoDono()).getTokens().getAccessTokenEncrypted();
        long renovacoes = fake.contar("renovar");

        mongo.updateFirst(new Query(Criteria.where("id").is(k.conexaoDono())),
                new Update().set("tokens.expiresAt", Instant.now().plus(Duration.ofMinutes(10))), GoogleCalendarConexao.class);
        String id1 = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(8, 0), h(8, 30), null);
        esperarEvento(k.conexaoDono(), id1);
        assertEquals(renovacoes, fake.contar("renovar"), "faltando 10 min ainda não renova");

        mongo.updateFirst(new Query(Criteria.where("id").is(k.conexaoDono())),
                new Update().set("tokens.expiresAt", Instant.now().plus(Duration.ofMinutes(4))), GoogleCalendarConexao.class);
        String id2 = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(9, 0), h(9, 30), null);
        esperarEvento(k.conexaoDono(), id2);

        assertEquals(renovacoes + 1, fake.contar("renovar"), "faltando 4 min renova (uma vez)");
        GoogleCalendarConexao depois = s.conexao(k.conexaoDono());
        assertNotEquals(antes, depois.getTokens().getAccessTokenEncrypted());
        assertTrue(depois.getTokens().getExpiresAt().isAfter(Instant.now().plus(Duration.ofMinutes(50))));
        assertEquals(SyncStatusGoogleEnum.ATIVO, depois.getSyncStatus());
    }

    @Test
    void clinicasNaoVazamEntreSi() throws Exception {
        Cenario a = cenario();
        Cenario b = cenario();

        String id = criarAgendamento(a.clinica(), a.paciente(), a.dentista(), h(14, 0), h(14, 30), null);
        esperarEvento(a.conexaoDono(), id);
        esperarSemMudanca();

        assertEquals(0, fake.eventosDa(b.googleDono()).size());
        assertEquals(1, fake.eventosDa(a.googleDono()).size());
    }

    @Test
    void criarEEditarEmSequenciaRapida_TerminaComUmEventoNoEstadoFinal() throws Exception {
        Cenario k = cenario();
        String id = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(8, 0), h(8, 30), null);
        for (int i = 1; i <= 6; i++) {
            editarAgendamento(k.clinica(), id, k.paciente(), k.dentista(), h(8 + i, 0), h(8 + i, 30), "AGENDADO");
        }

        String eid = eventoId(k.conexaoDono(), id);
        aguardar("estado final", () -> fake.evento(eid) != null && h(14, 0).equals(fake.evento(eid).inicio));
        esperarSemMudanca();
        assertEquals(1, fake.eventosDa(k.googleDono()).size());
        assertEquals(h(14, 30), fake.evento(eid).fim);
        assertEquals(1, mongo.count(new Query(Criteria.where("agendamentoId").is(id)), GoogleCalendarEvento.class));
    }

    // ═════════════ webhook: autenticação dos cabeçalhos ═════════════

    @Test
    void webhook_ValidaCabecalhosDoGoogle() throws Exception {
        Cenario k = cenario();
        GoogleCalendarConexao.WebhookWatch w = s.conexao(k.conexaoDono()).getWebhookWatch();
        String tokenOk = canalService.tokenDoCanal(w.getChannelId());
        String url = "/api/public/webhooks/google-calendar";

        assertEquals(400, mvc.perform(MockMvcRequestBuilders.post(url)).andReturn().getResponse().getStatus(), "sem cabeçalhos");
        assertEquals(400, mvc.perform(MockMvcRequestBuilders.post(url).header("X-Goog-Channel-ID", w.getChannelId())).andReturn().getResponse().getStatus());
        assertEquals(404, mvc.perform(MockMvcRequestBuilders.post(url).header("X-Goog-Channel-ID", "canal-inexistente")
                .header("X-Goog-Resource-ID", "res").header("X-Goog-Channel-Token", tokenOk)).andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(MockMvcRequestBuilders.post(url).header("X-Goog-Channel-ID", w.getChannelId())
                .header("X-Goog-Resource-ID", "res-errado").header("X-Goog-Channel-Token", tokenOk)).andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(MockMvcRequestBuilders.post(url).header("X-Goog-Channel-ID", w.getChannelId())
                .header("X-Goog-Resource-ID", w.getResourceId()).header("X-Goog-Channel-Token", "token-errado")).andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(MockMvcRequestBuilders.post(url).header("X-Goog-Channel-ID", w.getChannelId())
                .header("X-Goog-Resource-ID", w.getResourceId())).andReturn().getResponse().getStatus(), "sem token do canal");
        assertEquals(200, notificar(k.conexaoDono(), "exists").getResponse().getStatus());
    }

    @Test
    void webhook_TokenDeUmCanalNaoVaiParaOutro() throws Exception {
        Cenario a = cenario();
        Cenario b = cenario();
        GoogleCalendarConexao.WebhookWatch wa = s.conexao(a.conexaoDono()).getWebhookWatch();
        GoogleCalendarConexao.WebhookWatch wb = s.conexao(b.conexaoDono()).getWebhookWatch();

        int status = mvc.perform(MockMvcRequestBuilders.post("/api/public/webhooks/google-calendar")
                .header("X-Goog-Channel-ID", wa.getChannelId()).header("X-Goog-Resource-ID", wa.getResourceId())
                .header("X-Goog-Channel-Token", canalService.tokenDoCanal(wb.getChannelId()))).andReturn().getResponse().getStatus();

        assertEquals(403, status);
        assertNotEquals(canalService.tokenDoCanal(wa.getChannelId()), canalService.tokenDoCanal(wb.getChannelId()));
        assertFalse(canalService.tokenConfere(wa.getChannelId(), null));
    }

    @Test
    void webhook_MensagemDeSync_ConfirmaOCanalSemBuscarNada() throws Exception {
        Cenario k = cenario();
        long listagens = fake.contar("listar:");

        assertEquals(200, notificar(k.conexaoDono(), "sync").getResponse().getStatus());
        esperarSemMudanca();

        assertEquals(listagens, fake.contar("listar:"));
    }

    @Test
    void webhook_NaoExigeJwt_EConexaoPausadaNaoTemMaisCanal() throws Exception {
        Cenario k = cenario();
        Profissional ana = profissional(k.clinica(), "Dra. Ana");
        GoogleCalendarConexao.WebhookWatch w = s.conexao(ana.conexaoId()).getWebhookWatch();
        User alvo = userRepository.findById(ana.usuario().id()).orElseThrow();
        s.put(k.clinica().donoToken(), "/api/usuarios/" + ana.usuario().id(),
                "{\"name\":\"" + alvo.getName() + "\",\"email\":\"" + alvo.getEmail() + "\",\"role\":\"DENTISTA\",\"permissoes\":[\"DASHBOARD\"]}");
        aguardar("canal limpo", () -> s.conexao(ana.conexaoId()).getWebhookWatch() == null);

        int status = mvc.perform(MockMvcRequestBuilders.post("/api/public/webhooks/google-calendar")
                .header("X-Goog-Channel-ID", w.getChannelId()).header("X-Goog-Resource-ID", w.getResourceId())
                .header("X-Goog-Channel-Token", canalService.tokenDoCanal(w.getChannelId()))).andReturn().getResponse().getStatus();

        assertEquals(404, status, "canal parado: o Google deve desistir");
    }

    // ═════════════ Google → Bokka ═════════════

    @Test
    void usuarioMoveOEventoNoGoogle_AgendamentoAcompanha_SemLoop() throws Exception {
        Cenario k = cenario();
        Profissional ana = profissional(k.clinica(), "Dra. Ana");
        String id = criarAgendamento(k.clinica(), k.paciente(), ana.dentistaId(), h(10, 0), h(10, 30), null);
        esperarEvento(k.conexaoDono(), id);
        esperarEvento(ana.conexaoId(), id);
        String eidAna = eventoId(ana.conexaoId(), id);
        String eidGeral = eventoId(k.conexaoDono(), id);

        fake.usuarioEditaHorario(eidAna, h(16, 0), h(16, 40));
        assertEquals(200, notificar(ana.conexaoId(), "exists").getResponse().getStatus());

        aguardar("agendamento remarcado", () -> h(16, 0).equals(agendamento(id).getDataHoraInicio()));
        assertEquals(h(16, 40), agendamento(id).getDataHoraFim());
        assertEquals(StatusAgendamentoEnum.AGENDADO, agendamento(id).getStatus());
        aguardar("a outra agenda acompanha", () -> h(16, 0).equals(fake.evento(eidGeral).inicio));

        // Convergência: depois de assentar, novas notificações não mudam nem reenviam nada.
        esperarSemMudanca();
        long atualizacoes = fake.contar("update:");
        for (int i = 0; i < 3; i++) {
            notificar(ana.conexaoId(), "exists");
            notificar(k.conexaoDono(), "exists");
        }
        esperarSemMudanca();
        assertEquals(atualizacoes, fake.contar("update:"), "eco das próprias gravações não pode gerar laço");
        assertEquals(h(16, 0), agendamento(id).getDataHoraInicio());
        assertEquals(1, mongo.count(new Query(Criteria.where("clinicaId").is(k.clinica().id())), Agendamento.class));
    }

    @Test
    void usuarioExcluiOEventoNoGoogle_SoRemoveOVinculo_AgendamentoNaoMudaDeStatus() throws Exception {
        Cenario k = cenario();
        Profissional ana = profissional(k.clinica(), "Dra. Ana");
        String ativo = criarAgendamento(k.clinica(), k.paciente(), ana.dentistaId(), h(10, 0), h(10, 30), null);
        String realizado = criarAgendamento(k.clinica(), k.paciente(), ana.dentistaId(), h(11, 0), h(11, 30), null);
        esperarEvento(ana.conexaoId(), ativo);
        esperarEvento(ana.conexaoId(), realizado);
        esperarEvento(k.conexaoDono(), ativo);
        mongo.updateFirst(new Query(Criteria.where("id").is(realizado)), new Update().set("status", StatusAgendamentoEnum.REALIZADO), Agendamento.class);

        fake.usuarioExclui(eventoId(ana.conexaoId(), ativo));
        fake.usuarioExclui(eventoId(ana.conexaoId(), realizado));
        notificar(ana.conexaoId(), "exists");

        aguardar("vínculo removido", () -> mongo.count(new Query(Criteria.where("conexaoId").is(ana.conexaoId())), GoogleCalendarEvento.class) == 0);
        esperarSemMudanca();
        assertEquals(StatusAgendamentoEnum.AGENDADO, agendamento(ativo).getStatus(), "excluir no Google não cancela a consulta no Bokka");
        assertEquals(StatusAgendamentoEnum.REALIZADO, agendamento(realizado).getStatus());
        assertFalse(fake.evento(eventoId(k.conexaoDono(), ativo)).cancelado, "a exclusão não se propaga para as outras agendas");
        assertEquals(1, mongo.count(new Query(Criteria.where("conexaoId").is(k.conexaoDono()).and("agendamentoId").is(ativo)), GoogleCalendarEvento.class));
    }

    @Test
    void agendamentoSemVinculoPorExclusaoNoGoogle_VoltaASincronizarNaProximaEdicaoDoBokka() throws Exception {
        Cenario k = cenario();
        String id = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(10, 0), h(10, 30), null);
        esperarEvento(k.conexaoDono(), id);
        String eid = eventoId(k.conexaoDono(), id);
        fake.usuarioExclui(eid);
        notificar(k.conexaoDono(), "exists");
        aguardar("vínculo removido", () -> mongo.count(new Query(Criteria.where("agendamentoId").is(id)), GoogleCalendarEvento.class) == 0);

        editarAgendamento(k.clinica(), id, k.paciente(), k.dentista(), h(10, 0), h(11, 0), "AGENDADO");

        FakeGoogleCalendarApi.Armazenado ev = esperarEvento(k.conexaoDono(), id);
        assertEquals(h(11, 0), ev.fim);
        assertEquals(1, mongo.count(new Query(Criteria.where("agendamentoId").is(id)), GoogleCalendarEvento.class));
    }

    @Test
    void inativarUsuario_ApagaOsEventosDoGoogleDeleEDesconecta_SemTocarNaAgendaDaClinica() throws Exception {
        Cenario k = cenario();
        Profissional ana = profissional(k.clinica(), "Dra. Ana");
        String a = criarAgendamento(k.clinica(), k.paciente(), ana.dentistaId(), h(9, 0), h(9, 30), null);
        String b = criarAgendamento(k.clinica(), k.paciente(), ana.dentistaId(), h(10, 0), h(10, 30), null);
        esperarEvento(ana.conexaoId(), a);
        esperarEvento(ana.conexaoId(), b);
        assertEquals(2, fake.eventosAtivosDa(ana.google()).size());

        MvcResult r = s.delete(k.clinica().donoToken(), "/api/usuarios/" + ana.usuario().id());

        assertEquals(204, r.getResponse().getStatus());
        assertEquals(0, fake.eventosAtivosDa(ana.google()).size(), "ex-funcionário não mantém nada da clínica no Google dele");
        assertNull(s.conexao(ana.conexaoId()));
        assertEquals(0, mongo.count(new Query(Criteria.where("conexaoId").is(ana.conexaoId())), GoogleCalendarEvento.class));
        assertFalse(userRepository.findById(ana.usuario().id()).orElseThrow().getAtivo());
        assertEquals(2, fake.eventosAtivosDa(k.googleDono()).size(), "a agenda geral da clínica continua");
        assertNotNull(agendamento(a));
    }

    @Test
    void inativarUsuario_FalhaNoGoogleNaoImpedeAInativacao() throws Exception {
        Cenario k = cenario();
        Profissional ana = profissional(k.clinica(), "Dra. Ana");
        String a = criarAgendamento(k.clinica(), k.paciente(), ana.dentistaId(), h(9, 0), h(9, 30), null);
        esperarEvento(ana.conexaoId(), a);
        fake.falharExclusaoDe(eventoId(ana.conexaoId(), a), new GoogleApiException(500, "backendError"));

        MvcResult r = s.delete(k.clinica().donoToken(), "/api/usuarios/" + ana.usuario().id());

        assertEquals(204, r.getResponse().getStatus());
        assertFalse(userRepository.findById(ana.usuario().id()).orElseThrow().getAtivo());
        assertNull(s.conexao(ana.conexaoId()), "a conexão sai mesmo assim (o evento que falhou foi registrado em log)");
    }

    @Test
    void inativarUsuarioSemConexaoGoogle_ContinuaFuncionando() throws Exception {
        Cenario k = cenario();
        Usuario sem = s.criarUsuario(k.clinica(), RoleEnum.DENTISTA, COM_GOOGLE, s.criarDentista(k.clinica(), "Dr. Sem", true));

        assertEquals(204, s.delete(k.clinica().donoToken(), "/api/usuarios/" + sem.id()).getResponse().getStatus());

        assertNotNull(s.conexao(k.conexaoDono()), "a conexão de outro usuário não é afetada");
    }

    @Test
    void eventoPessoalECopiaDeEvento_SaoIgnorados_NuncaCriamAgendamento() throws Exception {
        Cenario k = cenario();
        Profissional ana = profissional(k.clinica(), "Dra. Ana");
        String id = criarAgendamento(k.clinica(), k.paciente(), ana.dentistaId(), h(10, 0), h(10, 30), null);
        esperarEvento(ana.conexaoId(), id);
        long agendamentos = mongo.count(new Query(Criteria.where("clinicaId").is(k.clinica().id())), Agendamento.class);

        fake.usuarioCriaEventoPessoal(ana.google(), h(12, 0), h(13, 0));
        String copia = fake.usuarioCopiaEvento(eventoId(ana.conexaoId(), id), h(17, 0), h(17, 30));
        notificar(ana.conexaoId(), "exists");
        esperarSemMudanca();
        fake.usuarioExclui(copia);
        notificar(ana.conexaoId(), "exists");
        esperarSemMudanca();

        assertEquals(agendamentos, mongo.count(new Query(Criteria.where("clinicaId").is(k.clinica().id())), Agendamento.class));
        assertEquals(h(10, 0), agendamento(id).getDataHoraInicio(), "a cópia não altera o agendamento original");
        assertEquals(StatusAgendamentoEnum.AGENDADO, agendamento(id).getStatus(), "excluir a cópia não cancela nada");
    }

    @Test
    void horarioDoGoogleEmConflito_BokkaPrevaleceEORestauraOEvento() throws Exception {
        Cenario k = cenario();
        Profissional ana = profissional(k.clinica(), "Dra. Ana");
        String a = criarAgendamento(k.clinica(), k.paciente(), ana.dentistaId(), h(10, 0), h(10, 30), null);
        String b = criarAgendamento(k.clinica(), k.paciente(), ana.dentistaId(), h(11, 0), h(11, 30), null);
        esperarEvento(ana.conexaoId(), a);
        esperarEvento(ana.conexaoId(), b);
        String eid = eventoId(ana.conexaoId(), a);

        fake.usuarioEditaHorario(eid, h(11, 15), h(11, 45));
        notificar(ana.conexaoId(), "exists");

        aguardar("evento restaurado", () -> h(10, 0).equals(fake.evento(eid).inicio));
        assertEquals(h(10, 0), agendamento(a).getDataHoraInicio());
        assertEquals(h(10, 30), agendamento(a).getDataHoraFim());
        assertEquals(h(11, 0), agendamento(b).getDataHoraInicio());
    }

    @Test
    void avisoAtrasadoComOHorarioQueOBokkaJaEnviou_NaoDesfazUmaEdicaoRecenteDoBokka() throws Exception {
        // Corrida: o Bokka já gravou o novo horário, mas o push ainda não chegou ao Google. O aviso que chega
        // mostra o horário antigo, que é exatamente o último que o Bokka enviou (eco), e deve ser ignorado.
        Cenario k = cenario();
        String id = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(10, 0), h(10, 30), null);
        esperarEvento(k.conexaoDono(), id);
        mongo.updateFirst(new Query(Criteria.where("id").is(id)),
                new Update().set("dataHoraInicio", h(11, 0)).set("dataHoraFim", h(11, 30)), Agendamento.class);
        fake.usuarioEditaHorario(eventoId(k.conexaoDono(), id), h(10, 0), h(10, 30)); // gera mudança para o aviso

        notificar(k.conexaoDono(), "exists");
        esperarSemMudanca();

        assertEquals(h(11, 0), agendamento(id).getDataHoraInicio(), "o horário antigo vindo do Google é eco, não edição");
        assertEquals(h(11, 30), agendamento(id).getDataHoraFim());
    }

    @Test
    void horarioInvalidoVindoDoGoogle_Recusado() throws Exception {
        Cenario k = cenario();
        String id = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(10, 0), h(10, 30), null);
        esperarEvento(k.conexaoDono(), id);
        String eid = eventoId(k.conexaoDono(), id);

        fake.usuarioEditaHorario(eid, h(12, 0), h(11, 0));
        notificar(k.conexaoDono(), "exists");

        aguardar("evento restaurado", () -> h(10, 0).equals(fake.evento(eid).inicio));
        assertEquals(h(10, 0), agendamento(id).getDataHoraInicio());
    }

    @Test
    void reagendarNoGoogleParaOHorarioQueOProprioAgendamentoOcupava_NaoCountaComoConflitoConsigoMesmo() throws Exception {
        Cenario k = cenario();
        String id = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(10, 0), h(11, 0), null);
        esperarEvento(k.conexaoDono(), id);

        fake.usuarioEditaHorario(eventoId(k.conexaoDono(), id), h(10, 30), h(11, 30));
        notificar(k.conexaoDono(), "exists");

        aguardar("remarcado", () -> h(10, 30).equals(agendamento(id).getDataHoraInicio()));
        assertEquals(h(11, 30), agendamento(id).getDataHoraFim());
    }

    @Test
    void agendamentoJaCancelado_NaoAceitaHorarioDoGoogle() throws Exception {
        Cenario k = cenario();
        String id = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(10, 0), h(10, 30), null);
        esperarEvento(k.conexaoDono(), id);
        mongo.updateFirst(new Query(Criteria.where("id").is(id)), new Update().set("status", StatusAgendamentoEnum.FALTOU), Agendamento.class);

        fake.usuarioEditaHorario(eventoId(k.conexaoDono(), id), h(15, 0), h(15, 30));
        notificar(k.conexaoDono(), "exists");
        esperarSemMudanca();

        assertEquals(h(10, 0), agendamento(id).getDataHoraInicio());
    }

    @Test
    void cancelamentoVindoDoProprioBokka_NaoViraCancelamentoDuploNemMexeNoAgendamento() throws Exception {
        // Mudar o agendamento de dentista apaga o evento da agenda do dentista antigo. O aviso de "cancelado"
        // que volta é do Bokka e não pode cancelar o agendamento (marca "removendo" antes da exclusão).
        Cenario k = cenario();
        Profissional ana = profissional(k.clinica(), "Dra. Ana");
        Profissional beto = profissional(k.clinica(), "Dr. Beto");
        String id = criarAgendamento(k.clinica(), k.paciente(), ana.dentistaId(), h(10, 0), h(10, 30), null);
        esperarEvento(ana.conexaoId(), id);
        String eidAna = eventoId(ana.conexaoId(), id);

        // Simula a corrida: o aviso chega com o elo ainda presente e marcado como "removendo".
        mongo.updateFirst(new Query(Criteria.where("conexaoId").is(ana.conexaoId()).and("agendamentoId").is(id)),
                new Update().set("removendo", true), GoogleCalendarEvento.class);
        fake.usuarioExclui(eidAna);
        notificar(ana.conexaoId(), "exists");

        aguardar("elo consumido", () -> mongo.count(new Query(Criteria.where("conexaoId").is(ana.conexaoId())), GoogleCalendarEvento.class) == 0);
        assertEquals(StatusAgendamentoEnum.AGENDADO, agendamento(id).getStatus());
        assertNotNull(beto);
    }

    @Test
    void syncTokenVencido_RecomecaSemPerderAConexao() throws Exception {
        Cenario k = cenario();
        String id = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(10, 0), h(10, 30), null);
        esperarEvento(k.conexaoDono(), id);
        String tokenAntigo = s.conexao(k.conexaoDono()).getSyncToken();
        fake.expirarSyncTokens();

        notificar(k.conexaoDono(), "exists");

        aguardar("novo syncToken", () -> {
            String t = s.conexao(k.conexaoDono()).getSyncToken();
            return t != null && !t.equals(tokenAntigo);
        });
        assertEquals(SyncStatusGoogleEnum.ATIVO, s.conexao(k.conexaoDono()).getSyncStatus());

        fake.usuarioEditaHorario(eventoId(k.conexaoDono(), id), h(13, 0), h(13, 30));
        notificar(k.conexaoDono(), "exists");
        aguardar("volta a sincronizar", () -> h(13, 0).equals(agendamento(id).getDataHoraInicio()));
    }

    @Test
    void falhaTransitoriaNaBusca_NaoAvancaOSyncToken_EATentativaSeguinteRecupera() throws Exception {
        Cenario k = cenario();
        String id = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(10, 0), h(10, 30), null);
        esperarEvento(k.conexaoDono(), id);
        String token = s.conexao(k.conexaoDono()).getSyncToken();
        fake.usuarioEditaHorario(eventoId(k.conexaoDono(), id), h(13, 0), h(13, 30));

        fake.falharListagens(new GoogleApiException(503, "backendError"));
        notificar(k.conexaoDono(), "exists");
        esperarSemMudanca();
        assertEquals(token, s.conexao(k.conexaoDono()).getSyncToken(), "sem avançar, a mudança não se perde");
        assertEquals(h(10, 0), agendamento(id).getDataHoraInicio());

        fake.limpar();
        notificar(k.conexaoDono(), "exists");
        aguardar("recuperou", () -> h(13, 0).equals(agendamento(id).getDataHoraInicio()));
    }

    @Test
    void rajadaDeNotificacoes_ProcessaUmaVezSoSemDuplicarEfeitos() throws Exception {
        Cenario k = cenario();
        String id = criarAgendamento(k.clinica(), k.paciente(), k.dentista(), h(10, 0), h(10, 30), null);
        esperarEvento(k.conexaoDono(), id);
        fake.usuarioEditaHorario(eventoId(k.conexaoDono(), id), h(13, 0), h(13, 30));

        ExecutorService pool = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 20; i++) {
            pool.submit(() -> notificar(k.conexaoDono(), "exists"));
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS));

        aguardar("remarcado", () -> h(13, 0).equals(agendamento(id).getDataHoraInicio()));
        esperarSemMudanca();
        assertEquals(h(13, 30), agendamento(id).getDataHoraFim());
        assertEquals(1, mongo.count(new Query(Criteria.where("clinicaId").is(k.clinica().id())), Agendamento.class));
    }

    // ═════════════ renovação do canal ═════════════

    @Test
    void renovacaoDiaria_TrocaOsCanaisQueVencemEmBreve_EDeixaOsDemais() throws Exception {
        Cenario k = cenario();
        Profissional ana = profissional(k.clinica(), "Dra. Ana");
        GoogleCalendarConexao.WebhookWatch antigoGeral = s.conexao(k.conexaoDono()).getWebhookWatch();
        GoogleCalendarConexao.WebhookWatch antigoAna = s.conexao(ana.conexaoId()).getWebhookWatch();
        mongo.updateFirst(new Query(Criteria.where("id").is(k.conexaoDono())),
                new Update().set("webhookWatch.expiration", Instant.now().plus(Duration.ofHours(12))), GoogleCalendarConexao.class);

        canalService.renovarCanaisProximosDoVencimento();

        GoogleCalendarConexao.WebhookWatch novoGeral = s.conexao(k.conexaoDono()).getWebhookWatch();
        assertNotEquals(antigoGeral.getChannelId(), novoGeral.getChannelId(), "canal novo");
        assertTrue(novoGeral.getExpiration().isAfter(Instant.now().plus(Duration.ofDays(5))));
        assertTrue(fake.canalAberto(novoGeral.getChannelId()));
        assertFalse(fake.canalAberto(antigoGeral.getChannelId()), "o antigo é parado só depois de abrir o novo");
        assertEquals(antigoAna.getChannelId(), s.conexao(ana.conexaoId()).getWebhookWatch().getChannelId(), "longe de vencer: intocado");

        // O canal novo já recebe notificações; o antigo não existe mais no Bokka.
        assertEquals(200, notificar(k.conexaoDono(), "exists").getResponse().getStatus());
        int antigo = mvc.perform(MockMvcRequestBuilders.post("/api/public/webhooks/google-calendar")
                .header("X-Goog-Channel-ID", antigoGeral.getChannelId()).header("X-Goog-Resource-ID", antigoGeral.getResourceId())
                .header("X-Goog-Channel-Token", canalService.tokenDoCanal(antigoGeral.getChannelId()))).andReturn().getResponse().getStatus();
        assertEquals(404, antigo);
    }

    @Test
    void renovacaoDiaria_CriaCanalParaConexaoAtivaQueNaoTem_EIgnoraPausadas() throws Exception {
        Cenario k = cenario();
        Profissional ana = profissional(k.clinica(), "Dra. Ana");
        mongo.updateFirst(new Query(Criteria.where("id").is(k.conexaoDono())),
                new Update().unset("webhookWatch").unset("syncToken"), GoogleCalendarConexao.class);
        mongo.updateFirst(new Query(Criteria.where("id").is(ana.conexaoId())),
                new Update().unset("webhookWatch").set("syncStatus", SyncStatusGoogleEnum.PAUSADO_PELO_DONO), GoogleCalendarConexao.class);

        canalService.renovarCanaisProximosDoVencimento();

        GoogleCalendarConexao geral = s.conexao(k.conexaoDono());
        assertNotNull(geral.getWebhookWatch());
        assertNotNull(geral.getSyncToken());
        assertNull(s.conexao(ana.conexaoId()).getWebhookWatch(), "conexão pausada não ganha canal");
    }

    @Test
    void renovacaoDiaria_FalhaEmUmaConexaoNaoImpedeAsOutras() throws Exception {
        Cenario a = cenario();
        Cenario b = cenario();
        for (String conexao : List.of(a.conexaoDono(), b.conexaoDono())) {
            mongo.updateFirst(new Query(Criteria.where("id").is(conexao)),
                    new Update().set("webhookWatch.expiration", Instant.now().plus(Duration.ofHours(1))), GoogleCalendarConexao.class);
        }
        String antigoA = s.conexao(a.conexaoDono()).getWebhookWatch().getChannelId();
        String antigoB = s.conexao(b.conexaoDono()).getWebhookWatch().getChannelId();
        // A conta de A perdeu o acesso; a renovação dela falha e a de B segue.
        mongo.updateFirst(new Query(Criteria.where("id").is(a.conexaoDono())),
                new Update().set("tokens.expiresAt", Instant.now().minusSeconds(60)), GoogleCalendarConexao.class);
        fake.revogarConta(a.googleDono());

        canalService.renovarCanaisProximosDoVencimento();

        assertEquals(antigoA, s.conexao(a.conexaoDono()).getWebhookWatch().getChannelId());
        assertNotEquals(antigoB, s.conexao(b.conexaoDono()).getWebhookWatch().getChannelId());
        assertEquals(SyncStatusGoogleEnum.REVOGADO_PELO_GOOGLE, s.conexao(a.conexaoDono()).getSyncStatus());
    }
}

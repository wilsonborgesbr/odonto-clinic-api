package com.example.demo.googlecalendar;

import com.example.demo.email.EmailService;
import com.example.demo.enums.PermissaoEnum;
import com.example.demo.enums.RoleEnum;
import com.example.demo.enums.SyncStatusGoogleEnum;
import com.example.demo.enums.EscopoGoogleCalendarEnum;
import com.example.demo.googlecalendar.GcalTestSupport.Clinica;
import com.example.demo.googlecalendar.GcalTestSupport.Usuario;
import com.example.demo.model.GoogleCalendarConexao;
import com.example.demo.model.GoogleCalendarEvento;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.TokenService;
import org.bson.Document;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

import static com.example.demo.googlecalendar.GcalTestSupport.aguardar;
import static com.example.demo.googlecalendar.GcalTestSupport.json;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Integração dos endpoints, do modelo de dados e das permissões do Google Calendar (MockMvc + Mongo local em
 * banco temporário próprio, derrubado ao final; Google substituído por um duplo em memória). Ignorado sem
 * Mongo em localhost:27017.
 */
@EnabledIf("mongoLocalDisponivel")
@SpringBootTest(properties = {
        "spring.mongodb.uri=mongodb://localhost:27017/tmp_teste_google_calendar_conexao",
        "app.frontend-url=https://bokka.com.br",
        "google.calendar.client-id=test-client-id.apps.googleusercontent.com",
        "google.calendar.client-secret=test-secret",
        "google.calendar.redirect-uri=https://api.bokka.com.br/api/google-calendar/callback",
        "google.calendar.webhook-url=https://api.bokka.com.br/api/public/webhooks/google-calendar",
        "google.calendar.api-retry-wait=PT0S",
        // o MockMvc sempre vem do mesmo IP; o limite em si é testado à parte (GoogleCalendarControllerLimiteTest)
        "google.calendar.callback-max-per-ip=100000"})
@AutoConfigureMockMvc
class GoogleCalendarConexaoIntegracaoTest {

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
    EstadoOAuth estado;
    @Autowired
    GoogleCalendarIndices indices;
    @Autowired
    CriptografiaTokens criptografia;
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

    private String email() {
        return "dra-" + GcalTestSupport.unico() + "@gmail.com";
    }

    private String corpoUsuario(User u, RoleEnum role, Set<PermissaoEnum> permissoes) {
        String perms = permissoes == null ? "null"
                : "[" + String.join(",", permissoes.stream().map(p -> "\"" + p.name() + "\"").toList()) + "]";
        return "{\"name\":\"" + u.getName() + "\",\"email\":\"" + u.getEmail() + "\",\"role\":\"" + role + "\",\"permissoes\":" + perms + "}";
    }

    // ═════════════ permissão padrão por cargo ═════════════

    @Test
    void permissaoPadrao_SoProprietarioESocio() {
        assertTrue(RoleEnum.PROPRIETARIO.permissoesPadrao().contains(PermissaoEnum.INTEGRACAO_GOOGLE_CALENDAR));
        assertTrue(RoleEnum.SOCIO.permissoesPadrao().contains(PermissaoEnum.INTEGRACAO_GOOGLE_CALENDAR));
        for (RoleEnum r : List.of(RoleEnum.ADMINISTRADOR, RoleEnum.DENTISTA, RoleEnum.RECEPCIONISTA, RoleEnum.FINANCEIRO,
                RoleEnum.ESTOQUISTA, RoleEnum.AUXILIAR_CLINICO)) {
            assertFalse(r.permissoesPadrao().contains(PermissaoEnum.INTEGRACAO_GOOGLE_CALENDAR), r.name());
        }
    }

    // ═════════════ /status ═════════════

    @Test
    void status_DentistaSemPermissao_NaoPermitidoENadaParaConectar() throws Exception {
        Clinica c = s.registrar();
        Usuario dentista = s.criarUsuario(c, RoleEnum.DENTISTA, null, null);

        JsonNode st = json(s.get(dentista.token(), "/api/google-calendar/status"));

        assertFalse(st.get("permitido").asBoolean());
        assertFalse(st.get("conectado").asBoolean());
        assertFalse(st.get("podeConectar").asBoolean());
        assertEquals("SEM_PERMISSAO", st.get("motivoBloqueio").asString());
        assertTrue(st.get("conexao").isNull());
    }

    @Test
    void status_Dono_PodeConectarAPropriaAgenda_EEstaConfigurada() throws Exception {
        Clinica c = s.registrar();

        JsonNode st = json(s.get(c.donoToken(), "/api/google-calendar/status"));

        assertTrue(st.get("permitido").asBoolean());
        assertTrue(st.get("configurada").asBoolean());
        assertFalse(st.get("conectado").asBoolean());
        assertTrue(st.get("podeConectar").asBoolean());
        assertTrue(st.get("motivoBloqueio").isNull());
        assertEquals("SOMENTE_PROPRIO", st.get("escopo").asString());
    }

    @Test
    void status_DentistaComPermissaoEVinculo_SoPodeConectarAPropriaAgenda() throws Exception {
        Clinica c = s.registrar();
        Usuario dentista = s.criarUsuario(c, RoleEnum.DENTISTA, COM_GOOGLE, s.criarDentista(c, "Dra. Ana", true));

        JsonNode st = json(s.get(dentista.token(), "/api/google-calendar/status"));

        assertTrue(st.get("permitido").asBoolean());
        assertTrue(st.get("podeConectar").asBoolean());
        assertEquals("SOMENTE_PROPRIO", st.get("escopo").asString());
    }

    @Test
    void semJwt_StatusEAuthUrlSaoNegados() throws Exception {
        int status = s.get(null, "/api/google-calendar/status").getResponse().getStatus();
        int authUrl = s.get(null, "/api/google-calendar/auth-url").getResponse().getStatus();
        int desconectar = s.post(null, "/api/google-calendar/desconectar", null).getResponse().getStatus();

        assertTrue(status == 401 || status == 403, "status " + status);
        assertTrue(authUrl == 401 || authUrl == 403, "auth-url " + authUrl);
        assertTrue(desconectar == 401 || desconectar == 403, "desconectar " + desconectar);
    }

    // ═════════════ /auth-url ═════════════

    @Test
    void authUrl_SemPermissao_403() throws Exception {
        Clinica c = s.registrar();
        Usuario dentista = s.criarUsuario(c, RoleEnum.DENTISTA, null, s.criarDentista(c, "Dra. Ana", true));

        assertEquals(403, s.get(dentista.token(), "/api/google-calendar/auth-url?tipo=PROFISSIONAL_INDIVIDUAL").getResponse().getStatus());
    }

    @Test
    void authUrl_NaoRecebeEscopo_OParametroAntigoTipoEIgnorado() throws Exception {
        Clinica c = s.registrar();

        assertEquals(200, s.get(c.donoToken(), "/api/google-calendar/auth-url").getResponse().getStatus());
        MvcResult antigo = s.get(c.donoToken(), "/api/google-calendar/auth-url?tipo=CLINICA_GERAL");
        assertEquals(200, antigo.getResponse().getStatus(), "compatível com o Frontend antigo");
        assertEquals("SOMENTE_PROPRIO", json(antigo).get("escopo").asString(), "o escopo é do cadastro do usuário, não do pedido");
    }

    @Test
    void authUrl_Dono_DevolveUrlDoGoogleComStateAssinadoEEscopoMinimo() throws Exception {
        Clinica c = s.registrar();

        MvcResult r = s.get(c.donoToken(), "/api/google-calendar/auth-url");

        assertEquals(200, r.getResponse().getStatus());
        JsonNode corpo = json(r);
        String url = corpo.get("url").asString();
        assertTrue(url.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"), url);
        assertTrue(url.contains("client_id=test-client-id.apps.googleusercontent.com"));
        assertTrue(url.contains("response_type=code"));
        assertTrue(url.contains("access_type=offline"), "precisa pedir refresh token");
        assertTrue(url.contains("prompt=consent"), "sem isso o Google não devolve refresh token na reconexão");
        String decodificada = URLDecoder.decode(url, StandardCharsets.UTF_8);
        assertTrue(decodificada.contains("redirect_uri=https://api.bokka.com.br/api/google-calendar/callback"));
        assertTrue(decodificada.contains("https://www.googleapis.com/auth/calendar.events"));
        assertFalse(decodificada.contains("auth/calendar&") || decodificada.contains("calendar.readonly"),
                "escopo mínimo: só eventos");
        assertEquals("SOMENTE_PROPRIO", corpo.get("escopo").asString());
        assertNotNull(corpo.get("expiraEm"));

        var dados = estado.validar(GcalTestSupport.estadoDa(url));
        assertTrue(dados.isPresent(), "o state precisa ser assinado e válido");
        assertEquals(c.donoId(), dados.get().userId());
        assertEquals(c.id(), dados.get().clinicaId());
        assertEquals(EscopoGoogleCalendarEnum.SOMENTE_PROPRIO, dados.get().escopo());
    }

    @Test
    void authUrl_Regras() throws Exception {
        Clinica c = s.registrar();
        Usuario comVinculo = s.criarUsuario(c, RoleEnum.DENTISTA, COM_GOOGLE, s.criarDentista(c, "Dra. Ana", true));
        Usuario semVinculo = s.criarUsuario(c, RoleEnum.DENTISTA, COM_GOOGLE, null);
        Usuario dentistaInativo = s.criarUsuario(c, RoleEnum.DENTISTA, COM_GOOGLE, s.criarDentista(c, "Dr. Inativo", false));
        Usuario vinculoDeOutraClinica = s.criarUsuario(c, RoleEnum.DENTISTA, COM_GOOGLE, s.criarDentista(s.registrar(), "Dr. Outro", true));

        assertEquals(200, s.get(comVinculo.token(), "/api/google-calendar/auth-url?tipo=PROFISSIONAL_INDIVIDUAL").getResponse().getStatus());
        assertEquals(400, s.get(semVinculo.token(), "/api/google-calendar/auth-url?tipo=PROFISSIONAL_INDIVIDUAL").getResponse().getStatus());
        assertEquals(400, s.get(dentistaInativo.token(), "/api/google-calendar/auth-url?tipo=PROFISSIONAL_INDIVIDUAL").getResponse().getStatus());
        assertEquals(400, s.get(vinculoDeOutraClinica.token(), "/api/google-calendar/auth-url?tipo=PROFISSIONAL_INDIVIDUAL").getResponse().getStatus(),
                "dentista de outra clínica não vale");
        assertEquals(200, s.get(c.donoToken(), "/api/google-calendar/auth-url").getResponse().getStatus(), "o dono tem dentista vinculado");
        Usuario socioSemDentista = s.criarUsuario(c, RoleEnum.SOCIO, null, null);
        assertEquals(400, s.get(socioSemDentista.token(), "/api/google-calendar/auth-url").getResponse().getStatus(),
                "proprietário/sócio sem dentista vinculado não conecta: não existe mais agenda geral nem pessoal");
    }

    // ═════════════ /callback ═════════════

    @Test
    void callback_Sucesso_GuardaConexaoComTokensCifradosERedirecionaParaOFrontend() throws Exception {
        Clinica c = s.registrar();
        String dentistaId = s.criarDentista(c, "Dra. Ana", true);
        Usuario dentista = s.criarUsuario(c, RoleEnum.DENTISTA, COM_GOOGLE, dentistaId);
        String google = email();

        MvcResult url = s.get(dentista.token(), "/api/google-calendar/auth-url?tipo=PROFISSIONAL_INDIVIDUAL");
        String state = GcalTestSupport.estadoDa(json(url).get("url").asString());
        MvcResult cb = mvc.perform(get("/api/google-calendar/callback").param("code", "code:" + google).param("state", state)).andReturn();

        assertEquals(302, cb.getResponse().getStatus());
        assertEquals("https://bokka.com.br/configuracoes?googleCalendar=conectado", cb.getResponse().getHeader("Location"));
        assertEquals("no-store", cb.getResponse().getHeader("Cache-Control"));

        GoogleCalendarConexao conexao = s.conexaoDoUsuario(c, dentista);
        assertNotNull(conexao);
        assertEquals(c.id(), conexao.getClinicaId());
        assertEquals(EscopoGoogleCalendarEnum.SOMENTE_PROPRIO, conexao.getEscopo());
        assertEquals(dentistaId, conexao.getDentistaId());
        assertEquals(google, conexao.getGoogleEmail());
        assertEquals("primary", conexao.getGoogleCalendarId());
        assertEquals(SyncStatusGoogleEnum.ATIVO, conexao.getSyncStatus());
        assertNotNull(conexao.getTokens().getExpiresAt());
        assertTrue(conexao.getTokens().getScope().contains("calendar.events"));

        // O banco nunca guarda token em claro.
        Document bruto = mongo.getCollection("google_calendar_conexoes").find(new Document("_id", new org.bson.types.ObjectId(conexao.getId()))).first();
        String dump = bruto.toJson();
        assertFalse(dump.contains("acc:" + google), "access token em claro no banco");
        assertFalse(dump.contains("ref:" + google), "refresh token em claro no banco");
        assertTrue(conexao.getTokens().getAccessTokenEncrypted().startsWith("v1."));
        assertEquals("ref:" + google, criptografia.decifrar(conexao.getTokens().getRefreshTokenEncrypted(),
                CriptografiaTokens.contexto(c.id(), dentista.id(), "refresh_token")));
    }

    @Test
    void callback_ConexaoGuardaODentistaVinculadoAoUsuario() throws Exception {
        Clinica c = s.registrar();
        Usuario dono = new Usuario(c.donoId(), c.donoToken());

        GoogleCalendarConexao conexao = s.conexao(s.conectar(dono, c, email()));

        assertEquals(EscopoGoogleCalendarEnum.SOMENTE_PROPRIO, conexao.getEscopo());
        assertEquals(c.donoDentistaId(), conexao.getDentistaId());
    }

    @Test
    void callback_EstadoInvalido_NaoCriaNada() throws Exception {
        Clinica c = s.registrar();
        String state = GcalTestSupport.estadoDa(json(s.get(c.donoToken(), "/api/google-calendar/auth-url")).get("url").asString());
        long antes = fake.contar("trocarCodigo");

        for (String ruim : new String[]{"lixo", state + "x", state.substring(0, state.indexOf('.') + 1) + "AAAA", ""}) {
            MvcResult cb = mvc.perform(get("/api/google-calendar/callback").param("code", "code:" + email()).param("state", ruim)).andReturn();
            assertEquals(302, cb.getResponse().getStatus());
            assertTrue(cb.getResponse().getHeader("Location").contains("googleCalendar=erro&motivo=estado_invalido"),
                    cb.getResponse().getHeader("Location"));
        }
        MvcResult semState = mvc.perform(get("/api/google-calendar/callback").param("code", "code:" + email())).andReturn();
        assertTrue(semState.getResponse().getHeader("Location").contains("motivo=estado_invalido"));

        assertNull(s.conexaoDoUsuario(c, new Usuario(c.donoId(), null)));
        assertEquals(antes, fake.contar("trocarCodigo"), "nenhum código pode ser trocado com state inválido");
    }

    @Test
    void callback_StateDeOutroUsuario_NaoPermiteConectarComoOutro() throws Exception {
        // Um state forjado com o id da vítima não passa na assinatura: só o state emitido para o usuário vale.
        Clinica c = s.registrar();
        String forjado = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                (c.donoId() + "|" + c.id() + "|SOMENTE_PROPRIO|nonce|9999999999").getBytes()) + ".AAAA";

        MvcResult cb = mvc.perform(get("/api/google-calendar/callback").param("code", "code:" + email()).param("state", forjado)).andReturn();

        assertTrue(cb.getResponse().getHeader("Location").contains("motivo=estado_invalido"));
        assertNull(s.conexaoDoUsuario(c, new Usuario(c.donoId(), null)));
    }

    @Test
    void callback_UsuarioNegouAcessoNoGoogle() throws Exception {
        Clinica c = s.registrar();
        String state = GcalTestSupport.estadoDa(json(s.get(c.donoToken(), "/api/google-calendar/auth-url")).get("url").asString());
        long antes = fake.contar("trocarCodigo");

        MvcResult cb = mvc.perform(get("/api/google-calendar/callback").param("error", "access_denied").param("state", state)).andReturn();

        assertTrue(cb.getResponse().getHeader("Location").endsWith("?googleCalendar=erro&motivo=acesso_negado"),
                cb.getResponse().getHeader("Location"));
        assertEquals(antes, fake.contar("trocarCodigo"));
        assertNull(s.conexaoDoUsuario(c, new Usuario(c.donoId(), null)));
    }

    @Test
    void callback_SemRefreshTokenOuSemEscopoDeAgenda_NaoGravaNada() throws Exception {
        Clinica c = s.registrar();
        Usuario dono = new Usuario(c.donoId(), c.donoToken());
        for (String[] caso : new String[][]{{"semrefresh", "sem_refresh_token"}, {"semagenda", "escopo_insuficiente"}}) {
            String state = GcalTestSupport.estadoDa(json(s.get(c.donoToken(), "/api/google-calendar/auth-url")).get("url").asString());

            MvcResult cb = mvc.perform(get("/api/google-calendar/callback").param("code", "code:" + email() + ":" + caso[0]).param("state", state)).andReturn();

            assertTrue(cb.getResponse().getHeader("Location").endsWith("motivo=" + caso[1]), cb.getResponse().getHeader("Location"));
            assertNull(s.conexaoDoUsuario(c, dono), caso[0]);
        }
    }

    @Test
    void callback_CodigoRecusadoPeloGoogle_ErroSemVazarDetalhes() throws Exception {
        Clinica c = s.registrar();
        String state = GcalTestSupport.estadoDa(json(s.get(c.donoToken(), "/api/google-calendar/auth-url")).get("url").asString());

        MvcResult cb = mvc.perform(get("/api/google-calendar/callback").param("code", "codigo-invalido").param("state", state)).andReturn();

        assertTrue(cb.getResponse().getHeader("Location").endsWith("motivo=falha_google"), cb.getResponse().getHeader("Location"));
        assertNull(s.conexaoDoUsuario(c, new Usuario(c.donoId(), null)));
    }

    @Test
    void callback_PermissaoRetiradaDuranteOFluxo_Recusa() throws Exception {
        Clinica c = s.registrar();
        Usuario dentista = s.criarUsuario(c, RoleEnum.DENTISTA, COM_GOOGLE, s.criarDentista(c, "Dra. Ana", true));
        String state = GcalTestSupport.estadoDa(json(s.get(dentista.token(), "/api/google-calendar/auth-url?tipo=PROFISSIONAL_INDIVIDUAL")).get("url").asString());
        mongo.updateFirst(new Query(Criteria.where("id").is(dentista.id())), new Update().set("permissoes", EnumSet.of(PermissaoEnum.DASHBOARD)), User.class);

        MvcResult cb = mvc.perform(get("/api/google-calendar/callback").param("code", "code:" + email()).param("state", state)).andReturn();

        assertTrue(cb.getResponse().getHeader("Location").endsWith("motivo=sem_permissao"), cb.getResponse().getHeader("Location"));
        assertNull(s.conexaoDoUsuario(c, dentista));
    }

    @Test
    void callback_StateComTipoQueOUsuarioNaoPodeMaisUsar_Recusa() throws Exception {
        // Emitido como PROFISSIONAL_INDIVIDUAL; o vínculo com o dentista some antes do retorno.
        Clinica c = s.registrar();
        Usuario dentista = s.criarUsuario(c, RoleEnum.DENTISTA, COM_GOOGLE, s.criarDentista(c, "Dra. Ana", true));
        String state = GcalTestSupport.estadoDa(json(s.get(dentista.token(), "/api/google-calendar/auth-url?tipo=PROFISSIONAL_INDIVIDUAL")).get("url").asString());
        mongo.updateFirst(new Query(Criteria.where("id").is(dentista.id())), new Update().unset("dentistaId"), User.class);

        MvcResult cb = mvc.perform(get("/api/google-calendar/callback").param("code", "code:" + email()).param("state", state)).andReturn();

        assertTrue(cb.getResponse().getHeader("Location").endsWith("motivo=dados_invalidos"), cb.getResponse().getHeader("Location"));
        assertNull(s.conexaoDoUsuario(c, dentista));
    }

    // ═════════════ status depois de conectar / duplicidade / reconexão ═════════════

    @Test
    void status_AposConectar_MostraConexaoSemNenhumSegredo() throws Exception {
        Clinica c = s.registrar();
        Usuario dono = new Usuario(c.donoId(), c.donoToken());
        String google = email();
        s.conectar(dono, c, google);

        MvcResult r = s.get(c.donoToken(), "/api/google-calendar/status");
        JsonNode st = json(r);

        assertTrue(st.get("conectado").asBoolean());
        JsonNode con = st.get("conexao");
        assertEquals("SOMENTE_PROPRIO", con.get("escopo").asString());
        assertEquals(google, con.get("googleEmail").asString());
        assertEquals("ATIVO", con.get("syncStatus").asString());
        assertFalse(st.get("podeConectar").asBoolean(), "já conectado: nada novo para conectar");
        assertEquals("JA_CONECTADO", st.get("motivoBloqueio").asString());
        String bruto = r.getResponse().getContentAsString();
        assertFalse(bruto.contains("acc:") || bruto.contains("ref:") || bruto.contains("Encrypted") || bruto.contains("tokens"),
                "tokens não podem aparecer na API: " + bruto);
    }

    @Test
    void conexaoAtiva_BloqueiaOutraDoMesmoUsuario_409() throws Exception {
        Clinica c = s.registrar();
        Usuario dono = new Usuario(c.donoId(), c.donoToken());
        s.conectar(dono, c, email());

        assertEquals(409, s.get(c.donoToken(), "/api/google-calendar/auth-url").getResponse().getStatus());
        assertEquals(409, s.get(c.donoToken(), "/api/google-calendar/auth-url").getResponse().getStatus());
    }

    @Test
    void cadaUsuarioDaClinicaTemASuaConexao_UmNaoImpedeOOutro() throws Exception {
        Clinica c = s.registrar();
        Usuario dono = new Usuario(c.donoId(), c.donoToken());
        Usuario socio = s.criarUsuario(c, RoleEnum.SOCIO, null, s.criarDentista(c, "Dra. Sócia", true));
        String conexaoDono = s.conectar(dono, c, email());

        assertEquals(200, s.get(socio.token(), "/api/google-calendar/auth-url").getResponse().getStatus());
        String conexaoSocio = s.conectar(socio, c, email());

        assertNotEquals(conexaoDono, conexaoSocio);
        assertEquals(2, mongo.count(new Query(Criteria.where("clinicaId").is(c.id())), GoogleCalendarConexao.class));
        assertEquals(c.donoDentistaId(), s.conexao(conexaoDono).getDentistaId());
        assertNotEquals(s.conexao(conexaoDono).getDentistaId(), s.conexao(conexaoSocio).getDentistaId());
        Clinica outra = s.registrar();
        assertEquals(200, s.get(outra.donoToken(), "/api/google-calendar/auth-url").getResponse().getStatus());
    }

    @Test
    void indicesUnicos_ExistemNoBanco() throws Exception {
        Clinica c = s.registrar();
        Usuario socio = s.criarUsuario(c, RoleEnum.SOCIO, null, null);
        Usuario outroSocio = s.criarUsuario(c, RoleEnum.SOCIO, null, null);
        LocalDateTime agora = LocalDateTime.now();
        mongo.insert(GoogleCalendarConexao.builder().clinicaId(c.id()).userId(socio.id())
                .escopo(EscopoGoogleCalendarEnum.SOMENTE_PROPRIO)
                .syncStatus(SyncStatusGoogleEnum.ATIVO).createdAt(agora).updatedAt(agora).build());

        assertThrows(DuplicateKeyException.class, () -> mongo.insert(GoogleCalendarConexao.builder().clinicaId(c.id()).userId(socio.id())
                .escopo(EscopoGoogleCalendarEnum.SOMENTE_PROPRIO).syncStatus(SyncStatusGoogleEnum.ATIVO).build()),
                "um usuário, uma conexão por clínica");
        mongo.insert(GoogleCalendarConexao.builder().clinicaId(c.id()).userId(outroSocio.id())
                .escopo(EscopoGoogleCalendarEnum.SOMENTE_PROPRIO).syncStatus(SyncStatusGoogleEnum.ATIVO).build());
        assertTrue(mongo.indexOps(GoogleCalendarConexao.class).getIndexInfo().stream()
                        .noneMatch(i -> i.getName().equals("clinica_geral_unique")),
                "o índice único da antiga agenda geral não existe mais");

        mongo.insert(GoogleCalendarEvento.builder().clinicaId(c.id()).conexaoId("con1").agendamentoId("ag1").googleEventId("e1").build());
        assertThrows(DuplicateKeyException.class, () -> mongo.insert(
                GoogleCalendarEvento.builder().clinicaId(c.id()).conexaoId("con1").agendamentoId("ag1").googleEventId("e2").build()),
                "um evento por agendamento em cada conexão");
    }


    @Test
    void indiceLegadoDaAgendaGeral_ESubstituidoNaInicializacao() {
        var ops = mongo.indexOps(GoogleCalendarConexao.class);
        ops.createIndex(new org.springframework.data.mongodb.core.index.Index().named("clinica_geral_unique")
                .on("clinicaId", org.springframework.data.domain.Sort.Direction.ASC).unique()
                .partial(org.springframework.data.mongodb.core.index.PartialIndexFilter.of(
                        org.springframework.data.mongodb.core.query.Criteria.where("tipoConexao").is("CLINICA_GERAL"))));
        assertTrue(ops.getIndexInfo().stream().anyMatch(i -> i.getName().equals("clinica_geral_unique")));

        indices.criar();

        assertTrue(ops.getIndexInfo().stream().noneMatch(i -> i.getName().equals("clinica_geral_unique")));
        assertTrue(ops.getIndexInfo().stream().anyMatch(i -> i.getName().equals("clinica_usuario_unique")));
    }
    @Test
    void reconexaoAposRevogacaoDoGoogle_ReaproveitaOMesmoRegistro() throws Exception {
        Clinica c = s.registrar();
        Usuario dono = new Usuario(c.donoId(), c.donoToken());
        String id = s.conectar(dono, c, email());
        mongo.updateFirst(new Query(Criteria.where("id").is(id)),
                new Update().set("syncStatus", SyncStatusGoogleEnum.REVOGADO_PELO_GOOGLE), GoogleCalendarConexao.class);

        JsonNode st = json(s.get(c.donoToken(), "/api/google-calendar/status"));
        assertEquals("REVOGADO_PELO_GOOGLE", st.get("conexao").get("syncStatus").asString());
        assertTrue(st.get("podeConectar").asBoolean(), "pode reconectar");

        String novoGoogle = email();
        String idNovo = s.conectar(dono, c, novoGoogle);

        assertEquals(id, idNovo, "mesmo registro, não um segundo");
        GoogleCalendarConexao conexao = s.conexao(id);
        assertEquals(SyncStatusGoogleEnum.ATIVO, conexao.getSyncStatus());
        assertEquals(EscopoGoogleCalendarEnum.SOMENTE_PROPRIO, conexao.getEscopo());
        assertEquals(novoGoogle, conexao.getGoogleEmail());
        assertEquals(1, mongo.count(new Query(Criteria.where("clinicaId").is(c.id())), GoogleCalendarConexao.class));
    }

    // ═════════════ canal de notificação ao conectar ═════════════

    @Test
    void aoConectar_AbreOCanalEGuardaOSyncTokenInicial() throws Exception {
        Clinica c = s.registrar();
        Usuario dono = new Usuario(c.donoId(), c.donoToken());

        String id = s.conectar(dono, c, email());

        aguardar("canal aberto", () -> s.conexao(id).getWebhookWatch() != null);
        GoogleCalendarConexao conexao = s.conexao(id);
        assertNotNull(conexao.getSyncToken(), "syncToken inicial para a sincronização incremental");
        assertTrue(fake.canalAberto(conexao.getWebhookWatch().getChannelId()));
        assertEquals("res-" + conexao.getWebhookWatch().getChannelId(), conexao.getWebhookWatch().getResourceId());
        assertTrue(conexao.getWebhookWatch().getExpiration().isAfter(java.time.Instant.now().plus(java.time.Duration.ofDays(5))));
        JsonNode con = json(s.get(c.donoToken(), "/api/google-calendar/status")).get("conexao");
        assertTrue(con.get("webhookAtivo").asBoolean());
    }

    // ═════════════ /desconectar ═════════════

    @Test
    void desconectar_ApagaConexaoEElosEPedeAoGoogleParaPararERevogar() throws Exception {
        Clinica c = s.registrar();
        Usuario dono = new Usuario(c.donoId(), c.donoToken());
        String google = email();
        String id = s.conectar(dono, c, google);
        aguardar("canal aberto", () -> s.conexao(id).getWebhookWatch() != null);
        String canal = s.conexao(id).getWebhookWatch().getChannelId();
        mongo.insert(GoogleCalendarEvento.builder().clinicaId(c.id()).conexaoId(id).agendamentoId("ag1").googleEventId("e1").build());

        MvcResult r = s.post(c.donoToken(), "/api/google-calendar/desconectar", null);

        assertEquals(204, r.getResponse().getStatus());
        assertNull(s.conexao(id));
        assertEquals(0, mongo.count(new Query(Criteria.where("conexaoId").is(id)), GoogleCalendarEvento.class));
        aguardar("canal parado e token revogado", () -> !fake.canalAberto(canal) && fake.contar("revogar:ref:" + google) == 1);
        assertFalse(json(s.get(c.donoToken(), "/api/google-calendar/status")).get("conectado").asBoolean());
        assertEquals(404, s.post(c.donoToken(), "/api/google-calendar/desconectar", null).getResponse().getStatus());
    }

    @Test
    void desconectar_ContaGoogleCompartilhadaComOutraConexao_NaoRevogaOToken() throws Exception {
        Clinica c = s.registrar();
        Usuario dono = new Usuario(c.donoId(), c.donoToken());
        Usuario socio = s.criarUsuario(c, RoleEnum.SOCIO, null, s.criarDentista(c, "Dra. Sócia", true));
        String google = email();
        s.conectar(dono, c, google);
        s.conectar(socio, c, google);

        assertEquals(204, s.post(socio.token(), "/api/google-calendar/desconectar", null).getResponse().getStatus());
        GcalTestSupport.esperarSemMudanca();

        assertEquals(0, fake.contar("revogar:"), "revogar derrubaria a conexão da clínica, que usa a mesma conta");
        assertNotNull(s.conexaoDoUsuario(c, dono));
    }

    @Test
    void desconectar_FuncionaMesmoSemPermissaoERespeitaOUsuario() throws Exception {
        Clinica c = s.registrar();
        Usuario dentista = s.criarUsuario(c, RoleEnum.DENTISTA, COM_GOOGLE, s.criarDentista(c, "Dra. Ana", true));
        Usuario outro = s.criarUsuario(c, RoleEnum.DENTISTA, COM_GOOGLE, s.criarDentista(c, "Dr. Beto", true));
        s.conectar(dentista, c, email());
        s.conectar(outro, c, email());
        mongo.updateFirst(new Query(Criteria.where("id").is(dentista.id())), new Update().set("permissoes", EnumSet.of(PermissaoEnum.DASHBOARD)), User.class);

        assertEquals(204, s.post(dentista.token(), "/api/google-calendar/desconectar", null).getResponse().getStatus());

        assertNull(s.conexaoDoUsuario(c, dentista));
        assertNotNull(s.conexaoDoUsuario(c, outro), "só a conexão de quem pediu");
    }

    // ═════════════ /conexoes (visão do dono) ═════════════

    @Test
    void conexoes_SoQuemGerenciaUsuarios_SemTokens_SomenteDaPropriaClinica() throws Exception {
        Clinica a = s.registrar();
        Clinica b = s.registrar();
        Usuario dentistaA = s.criarUsuario(a, RoleEnum.DENTISTA, COM_GOOGLE, s.criarDentista(a, "Dra. Ana", true));
        s.conectar(new Usuario(a.donoId(), a.donoToken()), a, email());
        s.conectar(dentistaA, a, email());
        s.conectar(new Usuario(b.donoId(), b.donoToken()), b, email());

        MvcResult daA = s.get(a.donoToken(), "/api/google-calendar/conexoes");
        MvcResult daB = s.get(b.donoToken(), "/api/google-calendar/conexoes");

        assertEquals(200, daA.getResponse().getStatus());
        assertEquals(2, json(daA).size());
        assertEquals(1, json(daB).size());
        assertFalse(daA.getResponse().getContentAsString().contains("Encrypted"));
        assertTrue(StreamSupport.stream(json(daA).spliterator(), false).anyMatch(n -> n.get("userNome").asString().startsWith("Dono")));
        assertEquals(403, s.get(dentistaA.token(), "/api/google-calendar/conexoes").getResponse().getStatus());
    }

    // ═════════════ permissão: quem concede ═════════════

    @Test
    void permissao_AdministradorNaoConcede_DonoConcede() throws Exception {
        Clinica c = s.registrar();
        Usuario admin = s.criarUsuario(c, RoleEnum.ADMINISTRADOR, null, null);
        Usuario dentista = s.criarUsuario(c, RoleEnum.DENTISTA, null, null);
        User alvo = userRepository.findById(dentista.id()).orElseThrow();
        Set<PermissaoEnum> concedidas = EnumSet.of(PermissaoEnum.DASHBOARD, PermissaoEnum.INTEGRACAO_GOOGLE_CALENDAR);

        MvcResult recusado = s.put(admin.token(), "/api/usuarios/" + dentista.id(), corpoUsuario(alvo, RoleEnum.DENTISTA, concedidas));
        assertEquals(403, recusado.getResponse().getStatus(), recusado.getResponse().getContentAsString());
        assertFalse(userRepository.findById(dentista.id()).orElseThrow().permissoesEfetivas().contains(PermissaoEnum.INTEGRACAO_GOOGLE_CALENDAR));

        MvcResult ok = s.put(c.donoToken(), "/api/usuarios/" + dentista.id(), corpoUsuario(alvo, RoleEnum.DENTISTA, concedidas));
        assertEquals(200, ok.getResponse().getStatus(), ok.getResponse().getContentAsString());
        assertTrue(userRepository.findById(dentista.id()).orElseThrow().permissoesEfetivas().contains(PermissaoEnum.INTEGRACAO_GOOGLE_CALENDAR));
        assertTrue(StreamSupport.stream(json(ok).get("permissoes").spliterator(), false).anyMatch(n -> n.asString().equals("INTEGRACAO_GOOGLE_CALENDAR")));
    }

    @Test
    void permissao_SocioTambemConcede() throws Exception {
        Clinica c = s.registrar();
        Usuario socio = s.criarUsuario(c, RoleEnum.SOCIO, null, null);
        Usuario dentista = s.criarUsuario(c, RoleEnum.DENTISTA, null, null);
        User alvo = userRepository.findById(dentista.id()).orElseThrow();

        MvcResult r = s.put(socio.token(), "/api/usuarios/" + dentista.id(), corpoUsuario(alvo, RoleEnum.DENTISTA, COM_GOOGLE));

        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    }

    @Test
    void permissao_AdministradorNaoConsegueConcederPeloCargoNemNaCriacao() throws Exception {
        Clinica c = s.registrar();
        Usuario admin = s.criarUsuario(c, RoleEnum.ADMINISTRADOR, null, null);
        Usuario dentista = s.criarUsuario(c, RoleEnum.DENTISTA, null, null);
        User alvo = userRepository.findById(dentista.id()).orElseThrow();

        // virar SOCIO traz todas as permissões por padrão, inclusive a do Google
        assertEquals(403, s.put(admin.token(), "/api/usuarios/" + dentista.id(), corpoUsuario(alvo, RoleEnum.SOCIO, null)).getResponse().getStatus());
        String novoSocio = "{\"name\":\"Novo\",\"email\":\"novo-" + GcalTestSupport.unico() + "@teste.com\",\"password\":\"senha123\",\"role\":\"SOCIO\"}";
        assertEquals(403, s.post(admin.token(), "/api/usuarios", novoSocio).getResponse().getStatus());
        String comPermissao = "{\"name\":\"Novo\",\"email\":\"novo-" + GcalTestSupport.unico() + "@teste.com\",\"password\":\"senha123\",\"role\":\"DENTISTA\","
                + "\"permissoes\":[\"DASHBOARD\",\"INTEGRACAO_GOOGLE_CALENDAR\"]}";
        assertEquals(403, s.post(admin.token(), "/api/usuarios", comPermissao).getResponse().getStatus());

        assertEquals(201, s.post(c.donoToken(), "/api/usuarios", comPermissao).getResponse().getStatus());
        // sem a permissão nova, o administrador continua criando normalmente
        String comum = "{\"name\":\"Comum\",\"email\":\"comum-" + GcalTestSupport.unico() + "@teste.com\",\"password\":\"senha123\",\"role\":\"DENTISTA\"}";
        assertEquals(201, s.post(admin.token(), "/api/usuarios", comum).getResponse().getStatus());
    }

    @Test
    void permissao_AdministradorEditaOutrosCamposDeQuemJaTem_SemPoderRetirarNemConcederDeNovo() throws Exception {
        Clinica c = s.registrar();
        Usuario admin = s.criarUsuario(c, RoleEnum.ADMINISTRADOR, null, null);
        Usuario dentista = s.criarUsuario(c, RoleEnum.DENTISTA, COM_GOOGLE, null);
        User alvo = userRepository.findById(dentista.id()).orElseThrow();
        alvo.setName("Nome Novo");

        MvcResult r = s.put(admin.token(), "/api/usuarios/" + dentista.id(), corpoUsuario(alvo, RoleEnum.DENTISTA, COM_GOOGLE));

        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        assertEquals("Nome Novo", userRepository.findById(dentista.id()).orElseThrow().getName());
    }

    // ═════════════ permissão: revogação pausa a conexão ═════════════

    @Test
    void revogarPermissao_PausaAConexaoEParaOCanalNoGoogle() throws Exception {
        Clinica c = s.registrar();
        Usuario dentista = s.criarUsuario(c, RoleEnum.DENTISTA, COM_GOOGLE, s.criarDentista(c, "Dra. Ana", true));
        String id = s.conectar(dentista, c, email());
        aguardar("canal aberto", () -> s.conexao(id).getWebhookWatch() != null);
        String canal = s.conexao(id).getWebhookWatch().getChannelId();
        User alvo = userRepository.findById(dentista.id()).orElseThrow();

        MvcResult r = s.put(c.donoToken(), "/api/usuarios/" + dentista.id(), corpoUsuario(alvo, RoleEnum.DENTISTA, EnumSet.of(PermissaoEnum.DASHBOARD)));

        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        assertEquals(SyncStatusGoogleEnum.PAUSADO_PELO_DONO, s.conexao(id).getSyncStatus(), "a pausa é imediata, não depende do Google");
        aguardar("canal parado no Google", () -> !fake.canalAberto(canal));
        aguardar("registro do canal limpo", () -> s.conexao(id).getWebhookWatch() == null);
        assertNull(s.conexao(id).getSyncToken());
        assertEquals("PAUSADO_PELO_DONO", json(s.get(dentista.token(), "/api/google-calendar/status")).get("conexao").get("syncStatus").asString());
        assertNotNull(s.conexao(id), "pausar não apaga a conexão");
    }

    @Test
    void revogarPermissao_PelaMudancaDeCargo_TambemPausa() throws Exception {
        Clinica c = s.registrar();
        Usuario socio = s.criarUsuario(c, RoleEnum.SOCIO, null, s.criarDentista(c, "Dra. Sócia", true));
        String id = s.conectar(socio, c, email());
        User alvo = userRepository.findById(socio.id()).orElseThrow();

        assertEquals(200, s.put(c.donoToken(), "/api/usuarios/" + socio.id(), corpoUsuario(alvo, RoleEnum.DENTISTA, null)).getResponse().getStatus());

        assertEquals(SyncStatusGoogleEnum.PAUSADO_PELO_DONO, s.conexao(id).getSyncStatus());
    }

    @Test
    void editarSemTirarAPermissao_NaoPausa() throws Exception {
        Clinica c = s.registrar();
        Usuario dentista = s.criarUsuario(c, RoleEnum.DENTISTA, COM_GOOGLE, s.criarDentista(c, "Dra. Ana", true));
        String id = s.conectar(dentista, c, email());
        User alvo = userRepository.findById(dentista.id()).orElseThrow();
        alvo.setName("Outro Nome");

        assertEquals(200, s.put(c.donoToken(), "/api/usuarios/" + dentista.id(), corpoUsuario(alvo, RoleEnum.DENTISTA, COM_GOOGLE)).getResponse().getStatus());

        assertEquals(SyncStatusGoogleEnum.ATIVO, s.conexao(id).getSyncStatus());
    }

    @Test
    void inativarUsuario_DesconectaAConexaoDele() throws Exception {
        Clinica c = s.registrar();
        Usuario dentista = s.criarUsuario(c, RoleEnum.DENTISTA, COM_GOOGLE, s.criarDentista(c, "Dra. Ana", true));
        String id = s.conectar(dentista, c, email());

        assertEquals(204, s.delete(c.donoToken(), "/api/usuarios/" + dentista.id()).getResponse().getStatus());
        assertNull(s.conexao(id), "inativar remove a conexão (e apaga os eventos no Google)");
    }

    @Test
    void conexaoPausada_PodeSerRefeitaQuandoOUsuarioRecebeAPermissaoDeNovo() throws Exception {
        Clinica c = s.registrar();
        Usuario dentista = s.criarUsuario(c, RoleEnum.DENTISTA, COM_GOOGLE, s.criarDentista(c, "Dra. Ana", true));
        String id = s.conectar(dentista, c, email());
        User alvo = userRepository.findById(dentista.id()).orElseThrow();
        s.put(c.donoToken(), "/api/usuarios/" + dentista.id(), corpoUsuario(alvo, RoleEnum.DENTISTA, EnumSet.of(PermissaoEnum.DASHBOARD)));
        assertEquals(403, s.get(dentista.token(), "/api/google-calendar/auth-url?tipo=PROFISSIONAL_INDIVIDUAL").getResponse().getStatus());

        s.put(c.donoToken(), "/api/usuarios/" + dentista.id(), corpoUsuario(alvo, RoleEnum.DENTISTA, COM_GOOGLE));
        String novo = s.conectar(dentista, c, email());

        assertEquals(id, novo);
        assertEquals(SyncStatusGoogleEnum.ATIVO, s.conexao(id).getSyncStatus());
    }
}

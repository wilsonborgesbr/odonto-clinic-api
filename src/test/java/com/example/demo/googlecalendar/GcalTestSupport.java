package com.example.demo.googlecalendar;

import com.example.demo.enums.PermissaoEnum;
import com.example.demo.enums.RoleEnum;
import com.example.demo.model.GoogleCalendarConexao;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.TokenService;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** Montagem de cenários dos testes de integração do Google Calendar (clínicas, usuários, agenda). */
class GcalTestSupport {

    static final JsonMapper JSON = JsonMapper.builder().build();

    /** O dono já vem vinculado a um dentista ({@code donoDentistaId}): só quem tem dentista conecta o Google. */
    record Clinica(String id, String codigo, String nome, String donoId, String donoToken, String donoDentistaId) {}

    record Usuario(String id, String token) {}

    private final MockMvc mvc;
    final MongoTemplate mongo;
    private final TokenService tokenService;
    private final UserRepository userRepository;

    GcalTestSupport(MockMvc mvc, MongoTemplate mongo, TokenService tokenService, UserRepository userRepository) {
        this.mvc = mvc;
        this.mongo = mongo;
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }

    static String unico() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    // ───────────── cenário ─────────────

    Clinica registrar() throws Exception {
        String id = unico();
        String email = "gcal-" + id + "@teste.com";
        String body = "{\"clinicaCodigo\":\"gcal-" + id + "\",\"clinicaNome\":\"Clínica " + id + "\","
                + "\"adminNome\":\"Dono " + id + "\",\"adminEmail\":\"" + email + "\",\"adminPassword\":\"senha123\"}";
        MvcResult r = mvc.perform(MockMvcRequestBuilders.post("/auth/register-clinica").contentType(MediaType.APPLICATION_JSON).content(body)
                .with(req -> {
                    req.setRemoteAddr("10.70." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250));
                    return req;
                })).andReturn();
        assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        String token = JSON.readTree(r.getResponse().getContentAsString()).get("token").asString();
        Document clinica = mongo.getCollection("clinicas").find(new Document("codigo", "gcal-" + id)).first();
        Document dono = mongo.getCollection("users").find(new Document("email", email)).first();
        String clinicaId = clinica.getObjectId("_id").toHexString();
        Document dentistaDono = new Document("clinicaId", clinicaId).append("nomeCompleto", "Dr. Carlos Lima")
                .append("cro", "CRO-" + unico()).append("ativo", true);
        mongo.getCollection("dentistas").insertOne(dentistaDono);
        String dentistaDonoId = dentistaDono.getObjectId("_id").toHexString();
        mongo.getCollection("users").updateOne(new Document("_id", dono.getObjectId("_id")),
                new Document("$set", new Document("dentistaId", dentistaDonoId)));
        return new Clinica(clinicaId, "gcal-" + id, "Clínica " + id,
                dono.getObjectId("_id").toHexString(), token, dentistaDonoId);
    }

    Usuario criarUsuario(Clinica c, RoleEnum role, Set<PermissaoEnum> permissoes, String dentistaId) {
        String id = unico();
        User salvo = userRepository.save(User.builder()
                .name("Usuário " + id).email("u-" + id + "@teste.com").password("x").clinicaId(c.id())
                .role(role).permissoes(permissoes).dentistaId(dentistaId).ativo(true)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        return new Usuario(salvo.getId(), tokenService.generateToken(salvo, c.codigo(), c.nome()));
    }

    String criarDentista(Clinica c, String nome, boolean ativo) {
        Document doc = new Document("clinicaId", c.id()).append("nomeCompleto", nome).append("cro", "CRO-" + unico())
                .append("ativo", ativo);
        mongo.getCollection("dentistas").insertOne(doc);
        return doc.getObjectId("_id").toHexString();
    }

    String criarPaciente(Clinica c, String nome) {
        Document doc = new Document("clinicaId", c.id()).append("nomeCompleto", nome).append("cpf", "").append("ativo", true);
        mongo.getCollection("pacientes").insertOne(doc);
        return doc.getObjectId("_id").toHexString();
    }

    // ───────────── HTTP ─────────────

    MvcResult chamar(MockHttpServletRequestBuilder req, String token, String corpo) throws Exception {
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        if (corpo != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        return mvc.perform(req).andReturn();
    }

    MvcResult get(String token, String url) throws Exception {
        return chamar(MockMvcRequestBuilders.get(url), token, null);
    }

    MvcResult post(String token, String url, String corpo) throws Exception {
        return chamar(MockMvcRequestBuilders.post(url), token, corpo);
    }

    MvcResult put(String token, String url, String corpo) throws Exception {
        return chamar(MockMvcRequestBuilders.put(url), token, corpo);
    }

    MvcResult delete(String token, String url) throws Exception {
        return chamar(MockMvcRequestBuilders.delete(url), token, null);
    }

    static JsonNode json(MvcResult r) throws Exception {
        return JSON.readTree(r.getResponse().getContentAsString());
    }

    // ───────────── fluxo OAuth completo ─────────────

    /** Faz auth-url → (Google) → callback e devolve o id da conexão criada. */
    String conectar(Usuario u, Clinica c, String emailGoogle) throws Exception {
        MvcResult url = get(u.token(), "/api/google-calendar/auth-url");
        assertEquals(200, url.getResponse().getStatus(), url.getResponse().getContentAsString());
        String state = estadoDa(json(url).get("url").asString());
        MvcResult cb = mvc.perform(MockMvcRequestBuilders.get("/api/google-calendar/callback").param("code", "code:" + emailGoogle).param("state", state)).andReturn();
        assertEquals(302, cb.getResponse().getStatus());
        String destino = cb.getResponse().getHeader("Location");
        assertTrue(destino.contains("googleCalendar=conectado"), destino);
        return conexaoDoUsuario(c, u).getId();
    }

    static String estadoDa(String urlGoogle) {
        return UriComponentsBuilder.fromUriString(urlGoogle).build().getQueryParams().getFirst("state");
    }

    GoogleCalendarConexao conexaoDoUsuario(Clinica c, Usuario u) {
        return mongo.findOne(new Query(Criteria.where("clinicaId").is(c.id()).and("userId").is(u.id())), GoogleCalendarConexao.class);
    }

    GoogleCalendarConexao conexao(String id) {
        return mongo.findById(id, GoogleCalendarConexao.class);
    }

    // ───────────── espera ─────────────

    static void aguardar(String descricao, BooleanSupplier condicao) throws InterruptedException {
        long limite = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < limite) {
            if (condicao.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        fail("Tempo esgotado esperando: " + descricao);
    }

    /** Dá tempo para qualquer tarefa assíncrona indevida aparecer, para afirmar que NADA aconteceu. */
    static void esperarSemMudanca() throws InterruptedException {
        Thread.sleep(400);
    }
}

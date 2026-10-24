package com.example.demo.controller;

import com.example.demo.email.EmailService;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Integração do PATCH parcial de preferências (MockMvc + Mongo local em banco temporário próprio,
 * derrubado ao final; não toca em {@code sistema_clinica}). Ignorado sem Mongo em localhost:27017.
 */
@EnabledIf("mongoLocalDisponivel")
@SpringBootTest(properties = {
        "spring.mongodb.uri=mongodb://localhost:27017/tmp_teste_preferencias",
        "app.frontend-url=https://bokka.com.br"})
@AutoConfigureMockMvc
class UsuarioPreferenciasIntegracaoTest {

    static boolean mongoLocalDisponivel() {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("localhost", 27017), 500);
            return true;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PREFERENCES = "/api/usuarios/me/preferences";

    @Autowired
    MockMvc mvc;

    @Autowired
    MongoTemplate mongo;

    @MockitoBean
    EmailService emailService;

    @AfterAll
    static void derrubarBanco(@Autowired MongoTemplate mongo) {
        mongo.getDb().drop();
    }

    private record Conta(String email, String token) {}

    private Conta registrar() throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        String email = "pref-" + id + "@teste.com";
        String body = "{\"clinicaCodigo\":\"pref-" + id + "\",\"clinicaNome\":\"Clínica " + id + "\","
                + "\"adminNome\":\"Dono " + id + "\",\"adminEmail\":\"" + email + "\",\"adminPassword\":\"senha123\"}";
        MvcResult r = mvc.perform(post("/auth/register-clinica").contentType(MediaType.APPLICATION_JSON).content(body)
                .with(req -> {
                    req.setRemoteAddr("10.30." + (int) (Math.random() * 200) + "." + (int) (Math.random() * 200));
                    return req;
                })).andReturn();
        assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        return new Conta(email, JSON.readTree(r.getResponse().getContentAsString()).get("token").asString());
    }

    private MvcResult patchar(Conta c, String corpo) throws Exception {
        MockHttpServletRequestBuilder req = patch(PREFERENCES).contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + c.token());
        if (corpo != null) {
            req.content(corpo);
        }
        return mvc.perform(req).andReturn();
    }

    private JsonNode json(MvcResult r) throws Exception {
        return JSON.readTree(r.getResponse().getContentAsString());
    }

    private JsonNode me(Conta c) throws Exception {
        MvcResult r = mvc.perform(get("/api/usuarios/me").header("Authorization", "Bearer " + c.token())).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        return json(r);
    }

    @Test
    void usuarioNovo_MeDevolveThemeLightEHideFinancialInfoFalse() throws Exception {
        JsonNode prefs = me(registrar()).get("preferences");

        assertEquals("light", prefs.get("theme").asString());
        assertFalse(prefs.get("hideFinancialInfo").asBoolean());
    }

    @Test
    void parcialSoTheme_AtualizaEPersisteSemMexerNoHide() throws Exception {
        Conta c = registrar();
        assertEquals(200, patchar(c, "{\"hideFinancialInfo\":true}").getResponse().getStatus());

        MvcResult r = patchar(c, "{\"theme\":\"dark\"}");

        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        assertEquals("dark", json(r).get("preferences").get("theme").asString());
        assertTrue(json(r).get("preferences").get("hideFinancialInfo").asBoolean());
        JsonNode prefs = me(c).get("preferences");
        assertEquals("dark", prefs.get("theme").asString());
        assertTrue(prefs.get("hideFinancialInfo").asBoolean());
    }

    @Test
    void parcialSoHideFinancialInfo_ComoOFrontendAtualEnviaNaoMudaOTema() throws Exception {
        Conta c = registrar();
        assertEquals(200, patchar(c, "{\"theme\":\"system\"}").getResponse().getStatus());

        MvcResult r = patchar(c, "{\"hideFinancialInfo\":true}");

        assertEquals(200, r.getResponse().getStatus());
        assertTrue(json(r).get("preferences").get("hideFinancialInfo").asBoolean());
        assertEquals("system", me(c).get("preferences").get("theme").asString());
    }

    @Test
    void ambosCampos_AtualizamOsDois() throws Exception {
        Conta c = registrar();

        MvcResult r = patchar(c, "{\"hideFinancialInfo\":true,\"theme\":\"dark\"}");

        assertEquals(200, r.getResponse().getStatus());
        JsonNode prefs = me(c).get("preferences");
        assertTrue(prefs.get("hideFinancialInfo").asBoolean());
        assertEquals("dark", prefs.get("theme").asString());
    }

    @Test
    void temaInvalido_Retorna400ENaoAlteraNada() throws Exception {
        Conta c = registrar();
        assertEquals(200, patchar(c, "{\"theme\":\"dark\"}").getResponse().getStatus());

        for (String invalido : new String[]{"\"azul\"", "\"DARK\"", "\"\"", "123"}) {
            MvcResult r = patchar(c, "{\"theme\":" + invalido + "}");
            assertEquals(400, r.getResponse().getStatus(), invalido);
        }
        MvcResult comMensagem = patchar(c, "{\"theme\":\"azul\"}");
        assertEquals("theme deve ser light, dark ou system", json(comMensagem).get("mensagem").get("theme").asString());
        assertEquals("dark", me(c).get("preferences").get("theme").asString());
    }

    @Test
    void temaInvalidoJuntoComHideFinancialInfo_Retorna400SemAplicarOHide() throws Exception {
        Conta c = registrar();

        assertEquals(400, patchar(c, "{\"hideFinancialInfo\":true,\"theme\":\"azul\"}").getResponse().getStatus());

        assertFalse(me(c).get("preferences").get("hideFinancialInfo").asBoolean());
    }

    @Test
    void corpoVazio_Retorna400() throws Exception {
        Conta c = registrar();

        assertEquals(400, patchar(c, "{}").getResponse().getStatus());
        assertEquals(400, patchar(c, "{\"theme\":null,\"hideFinancialInfo\":null}").getResponse().getStatus());
        assertEquals(400, patchar(c, "{\"campoDesconhecido\":1}").getResponse().getStatus());
        assertEquals(400, patchar(c, null).getResponse().getStatus());
    }

    @Test
    void semJwt_NaoAutorizado() throws Exception {
        int status = mvc.perform(patch(PREFERENCES).contentType(MediaType.APPLICATION_JSON).content("{\"theme\":\"dark\"}"))
                .andReturn().getResponse().getStatus();

        assertTrue(status == 401 || status == 403, "status " + status);
    }

    @Test
    void usuarioAntigoSemOCampoTheme_DevolveLightSemMigracaoEAceitaPatch() throws Exception {
        Conta c = registrar();
        assertEquals(200, patchar(c, "{\"hideFinancialInfo\":true}").getResponse().getStatus());
        mongo.getCollection("users").updateOne(new Document("email", c.email()),
                new Document("$unset", new Document("preferences.theme", "")));
        Document bruto = mongo.getCollection("users").find(new Document("email", c.email())).first();
        assertFalse(((Document) bruto.get("preferences")).containsKey("theme"), "o documento antigo não tem theme");

        JsonNode prefs = me(c).get("preferences");

        assertEquals("light", prefs.get("theme").asString());
        assertTrue(prefs.get("hideFinancialInfo").asBoolean());
        assertEquals(200, patchar(c, "{\"hideFinancialInfo\":false}").getResponse().getStatus());
        assertEquals("light", me(c).get("preferences").get("theme").asString());
    }

    @Test
    void usuarioAntigoSemNenhumaPreferencia_DevolveLightEHideFalse() throws Exception {
        Conta c = registrar();
        mongo.getCollection("users").updateOne(new Document("email", c.email()),
                new Document("$unset", new Document("preferences", "")));

        JsonNode prefs = me(c).get("preferences");

        assertEquals("light", prefs.get("theme").asString());
        assertFalse(prefs.get("hideFinancialInfo").asBoolean());
        assertEquals(200, patchar(c, "{\"theme\":\"system\"}").getResponse().getStatus());
        assertEquals("system", me(c).get("preferences").get("theme").asString());
    }

    @Test
    void setPorCampoEmSubdocumentoInexistente_CriaSoOCampoEnviadoSemApagarOutro() throws Exception {
        Conta c = registrar();
        mongo.getCollection("users").updateOne(new Document("email", c.email()),
                new Document("$unset", new Document("preferences", "")));

        assertEquals(200, patchar(c, "{\"theme\":\"dark\"}").getResponse().getStatus());

        Document bruto = mongo.getCollection("users").find(new Document("email", c.email())).first();
        Document prefs = (Document) bruto.get("preferences");
        assertEquals("dark", prefs.get("theme"));
        assertFalse(prefs.containsKey("hideFinancialInfo"), "só o campo enviado é gravado");
        assertEquals(200, patchar(c, "{\"hideFinancialInfo\":true}").getResponse().getStatus());
        prefs = (Document) mongo.getCollection("users").find(new Document("email", c.email())).first().get("preferences");
        assertEquals("dark", prefs.get("theme"));
        assertEquals(true, prefs.get("hideFinancialInfo"));
    }

    @Test
    void usuarioInativo_Retorna403ENaoGrava() throws Exception {
        Conta c = registrar();
        mongo.getCollection("users").updateOne(new Document("email", c.email()),
                new Document("$set", new Document("ativo", false)));

        assertEquals(403, patchar(c, "{\"theme\":\"dark\"}").getResponse().getStatus());

        Document bruto = mongo.getCollection("users").find(new Document("email", c.email())).first();
        Object prefs = bruto.get("preferences");
        assertTrue(prefs == null || !((Document) prefs).containsKey("theme"));
    }

    @Test
    void patchesSimultaneosDeCamposDiferentes_NaoSobrescrevemUmAoOutro() throws Exception {
        Conta c = registrar();
        mongo.getCollection("users").updateOne(new Document("email", c.email()),
                new Document("$unset", new Document("preferences", "")));
        final int rodadas = 50;
        String[] temas = {"dark", "system", "light"};
        java.util.concurrent.CyclicBarrier largada = new java.util.concurrent.CyclicBarrier(2);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Future<String> comTema = pool.submit(() -> {
                String ultimo = null;
                for (int i = 0; i < rodadas; i++) {
                    largada.await();
                    ultimo = temas[i % temas.length];
                    assertEquals(200, patchar(c, "{\"theme\":\"" + ultimo + "\"}").getResponse().getStatus());
                }
                return ultimo;
            });
            java.util.concurrent.Future<Boolean> comHide = pool.submit(() -> {
                boolean ultimo = false;
                for (int i = 0; i < rodadas; i++) {
                    largada.await();
                    ultimo = i % 2 == 0;
                    assertEquals(200, patchar(c, "{\"hideFinancialInfo\":" + ultimo + "}").getResponse().getStatus());
                }
                return ultimo;
            });

            String temaFinal = comTema.get(60, java.util.concurrent.TimeUnit.SECONDS);
            boolean hideFinal = comHide.get(60, java.util.concurrent.TimeUnit.SECONDS);

            JsonNode prefs = me(c).get("preferences");
            assertEquals(temaFinal, prefs.get("theme").asString(), "o último theme enviado não pode ser perdido");
            assertEquals(hideFinal, prefs.get("hideFinancialInfo").asBoolean(), "o último hideFinancialInfo enviado não pode ser perdido");
        } finally {
            pool.shutdownNow();
        }
    }
}

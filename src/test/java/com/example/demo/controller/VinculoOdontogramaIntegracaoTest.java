package com.example.demo.controller;

import com.example.demo.email.EmailService;
import com.example.demo.model.Procedimento;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Integração do vínculo procedimento ↔ odontograma (MockMvc + Mongo local em banco temporário próprio,
 * derrubado ao final; não toca em {@code sistema_clinica}). Ignorado sem Mongo em localhost:27017.
 */
@EnabledIf("mongoLocalDisponivel")
@SpringBootTest(properties = {
        "spring.mongodb.uri=mongodb://localhost:27017/tmp_teste_vinculo_odontograma",
        "app.frontend-url=https://bokka.com.br"})
@AutoConfigureMockMvc
class VinculoOdontogramaIntegracaoTest {

    static boolean mongoLocalDisponivel() {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("localhost", 27017), 500);
            return true;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    MockMvc mvc;
    @MockitoSpyBean
    MongoTemplate mongo;

    @MockitoBean
    EmailService emailService;

    @AfterAll
    static void derrubarBanco(@Autowired MongoTemplate mongo) {
        mongo.getDb().drop();
    }

    // ───────────── helpers ─────────────

    private String unico() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Clinica(String id, String token) {}

    private Clinica registrar() throws Exception {
        String id = unico();
        String body = "{\"clinicaCodigo\":\"vin-" + id + "\",\"clinicaNome\":\"Clínica " + id + "\","
                + "\"adminNome\":\"Dono " + id + "\",\"adminEmail\":\"vin-" + id + "@teste.com\",\"adminPassword\":\"senha123\"}";
        MvcResult r = mvc.perform(post("/auth/register-clinica").contentType(MediaType.APPLICATION_JSON).content(body)
                .with(req -> {
                    req.setRemoteAddr("10.60." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250));
                    return req;
                })).andReturn();
        assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        String token = JSON.readTree(r.getResponse().getContentAsString()).get("token").asString();
        Document clinica = mongo.getCollection("clinicas").find(new Document("codigo", "vin-" + id)).first();
        return new Clinica(clinica.getObjectId("_id").toHexString(), token);
    }

    private String criarPaciente(Clinica c) {
        Document doc = new Document("clinicaId", c.id()).append("nomeCompleto", "Paciente " + unico())
                .append("cpf", "").append("ativo", true);
        mongo.getCollection("pacientes").insertOne(doc);
        return doc.getObjectId("_id").toHexString();
    }

    private MockHttpServletRequestBuilder comJwt(MockHttpServletRequestBuilder req, Clinica c, String corpo) {
        req.header("Authorization", "Bearer " + c.token());
        if (corpo != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        return req;
    }

    private MvcResult criarProcedimento(Clinica c, String pacienteId, String extra) throws Exception {
        String corpo = "{\"pacienteId\":\"" + pacienteId + "\",\"nomeProcedimento\":\"RESTAURACAO_RESINA\""
                + (extra == null ? "" : "," + extra) + "}";
        return mvc.perform(comJwt(post("/api/procedimentos"), c, corpo)).andReturn();
    }

    private String procedimento(Clinica c, String pacienteId) throws Exception {
        MvcResult r = criarProcedimento(c, pacienteId, null);
        assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        return JSON.readTree(r.getResponse().getContentAsString()).get("id").asString();
    }

    private String odontograma(Clinica c, String pacienteId, String dentesJson) throws Exception {
        String corpo = "{\"pacienteId\":\"" + pacienteId + "\",\"dentes\":" + dentesJson + "}";
        MvcResult r = mvc.perform(comJwt(post("/api/odontogramas"), c, corpo)).andReturn();
        assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        return JSON.readTree(r.getResponse().getContentAsString()).get("id").asString();
    }

    private MvcResult vincular(Clinica c, String procId, String corpo) throws Exception {
        return mvc.perform(comJwt(post("/api/procedimentos/" + procId + "/vincular-odontograma"), c, corpo)).andReturn();
    }

    private JsonNode json(MvcResult r) throws Exception {
        return JSON.readTree(r.getResponse().getContentAsString());
    }

    private Document odontogramaNoBanco(String id) {
        return mongo.getCollection("odontogramas").find(new Document("_id", new ObjectId(id))).first();
    }

    private Document procedimentoNoBanco(String id) {
        return mongo.getCollection("procedimentos").find(new Document("_id", new ObjectId(id))).first();
    }

    @SuppressWarnings("unchecked")
    private List<Document> dentes(String odontogramaId) {
        Object dentes = odontogramaNoBanco(odontogramaId).get("dentes");
        return dentes == null ? List.of() : (List<Document>) dentes;
    }

    private Document dente(String odontogramaId, String numero) {
        List<Document> achados = dentes(odontogramaId).stream().filter(d -> numero.equals(d.getString("numeroDente"))).toList();
        assertTrue(achados.size() <= 1, "dente " + numero + " duplicado: " + achados);
        return achados.isEmpty() ? null : achados.get(0);
    }

    // ───────────── campo faces e validação FDI no CRUD ─────────────

    @Test
    void criarProcedimentoComFaces_GravaEDevolveSemRepeticao_DocumentoAntigoDevolveListaVazia() throws Exception {
        Clinica c = registrar();
        String paciente = criarPaciente(c);

        MvcResult r = criarProcedimento(c, paciente, "\"dente\":\"16\",\"faces\":[\"OCLUSAL\",\"MESIAL\",\"OCLUSAL\"]");

        assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        assertEquals(List.of("OCLUSAL", "MESIAL"), json(r).get("faces").valueStream().map(JsonNode::asString).toList());

        Document legado = new Document("clinicaId", c.id()).append("pacienteId", paciente).append("dente", "dente 16");
        mongo.getCollection("procedimentos").insertOne(legado);
        MvcResult lido = mvc.perform(comJwt(get("/api/procedimentos/" + legado.getObjectId("_id").toHexString()), c, null)).andReturn();
        assertEquals(200, lido.getResponse().getStatus());
        assertTrue(json(lido).get("faces").isArray());
        assertEquals(0, json(lido).get("faces").size(), "documento antigo sem 'faces' vale como lista vazia");
    }

    @Test
    void denteForaDoFdi_Retorna400NoCriarENoAtualizar_VazioContinuaAceito() throws Exception {
        Clinica c = registrar();
        String paciente = criarPaciente(c);

        MvcResult invalido = criarProcedimento(c, paciente, "\"dente\":\"99\"");
        assertEquals(400, invalido.getResponse().getStatus());
        assertTrue(json(invalido).get("mensagem").get("dente").asString().contains("FDI"));
        assertEquals(201, criarProcedimento(c, paciente, "\"dente\":\"\"").getResponse().getStatus());
        assertEquals(201, criarProcedimento(c, paciente, "\"dente\":\"85\"").getResponse().getStatus());

        String id = procedimento(c, paciente);
        String corpo = "{\"pacienteId\":\"" + paciente + "\",\"nomeProcedimento\":\"RESTAURACAO_RESINA\",\"dente\":\"19\"}";
        assertEquals(400, mvc.perform(comJwt(put("/api/procedimentos/" + id), c, corpo)).andReturn().getResponse().getStatus());
    }

    @Test
    void putSemFaces_MantemAsFacesJaGravadas() throws Exception {
        Clinica c = registrar();
        String paciente = criarPaciente(c);
        String id = JSON.readTree(criarProcedimento(c, paciente, "\"dente\":\"16\",\"faces\":[\"OCLUSAL\"]")
                .getResponse().getContentAsString()).get("id").asString();

        String corpo = "{\"pacienteId\":\"" + paciente + "\",\"nomeProcedimento\":\"RESTAURACAO_RESINA\",\"dente\":\"16\",\"descricao\":\"editado\"}";
        assertEquals(200, mvc.perform(comJwt(put("/api/procedimentos/" + id), c, corpo)).andReturn().getResponse().getStatus());

        assertEquals(List.of("OCLUSAL"), procedimentoNoBanco(id).getList("faces", String.class));
    }

    @Test
    void putSemOdontogramaId_MantemOVinculoExistente() throws Exception {
        Clinica c = registrar();
        String paciente = criarPaciente(c);
        String proc = procedimento(c, paciente);
        String odonto = json(vincular(c, proc, "{\"dente\":\"16\",\"faces\":[\"OCLUSAL\"],\"condicao\":\"CARIE\"}"))
                .get("odontograma").get("id").asString();

        String corpo = "{\"pacienteId\":\"" + paciente + "\",\"nomeProcedimento\":\"RESTAURACAO_RESINA\",\"dente\":\"16\",\"descricao\":\"editado\"}";
        MvcResult r = mvc.perform(comJwt(put("/api/procedimentos/" + proc), c, corpo)).andReturn();

        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        assertEquals(odonto, json(r).get("odontogramaId").asString());
        Document p = procedimentoNoBanco(proc);
        assertEquals(odonto, p.getString("odontogramaId"), "editar sem reenviar o vínculo não o desfaz");
        assertEquals("editado", p.getString("descricao"));
        assertEquals(List.of("OCLUSAL"), p.getList("faces", String.class));
    }

    @Test
    void putComOdontogramaId_TrocaDesfazOuRejeitaConformeOValorEnviado() throws Exception {
        Clinica c = registrar();
        String paciente = criarPaciente(c);
        String primeiro = odontograma(c, paciente, "[]");
        String proc = procedimento(c, paciente);
        assertEquals(200, vincular(c, proc, "{\"odontogramaId\":\"" + primeiro + "\",\"dente\":\"16\"}").getResponse().getStatus());
        Thread.sleep(20);
        String segundo = odontograma(c, paciente, "[]");
        String base = "{\"pacienteId\":\"" + paciente + "\",\"nomeProcedimento\":\"RESTAURACAO_RESINA\",\"dente\":\"16\",";

        assertEquals(200, mvc.perform(comJwt(put("/api/procedimentos/" + proc), c, base + "\"odontogramaId\":\"" + segundo + "\"}"))
                .andReturn().getResponse().getStatus());
        assertEquals(segundo, procedimentoNoBanco(proc).getString("odontogramaId"), "id informado troca o vínculo");

        assertEquals(400, mvc.perform(comJwt(put("/api/procedimentos/" + proc), c, base + "\"odontogramaId\":\"000000000000000000000000\"}"))
                .andReturn().getResponse().getStatus());
        assertEquals(segundo, procedimentoNoBanco(proc).getString("odontogramaId"), "id inválido não altera nada");

        assertEquals(200, mvc.perform(comJwt(put("/api/procedimentos/" + proc), c, base + "\"odontogramaId\":\"\"}"))
                .andReturn().getResponse().getStatus());
        assertNull(procedimentoNoBanco(proc).getString("odontogramaId"), "texto vazio desfaz o vínculo de propósito");
    }

    // ───────────── (a) odontograma informado ─────────────

    @Test
    void vincularComOdontogramaInformado_AtualizaODenteEOProcedimento() throws Exception {
        Clinica c = registrar();
        String paciente = criarPaciente(c);
        String odonto = odontograma(c, paciente, "[{\"numeroDente\":\"16\",\"condicao\":\"CARIE\",\"faces\":[\"OCLUSAL\"]},"
                + "{\"numeroDente\":\"21\",\"condicao\":\"HIGIDO\"}]");
        String proc = procedimento(c, paciente);

        MvcResult r = vincular(c, proc, "{\"odontogramaId\":\"" + odonto + "\",\"dente\":\"16\",\"faces\":[\"MESIAL\"],\"condicao\":\"CARIE\"}");

        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        JsonNode corpo = json(r);
        assertFalse(corpo.get("odontogramaCriado").asBoolean());
        assertEquals(odonto, corpo.get("odontograma").get("id").asString());
        assertEquals(odonto, corpo.get("procedimento").get("odontogramaId").asString());
        assertEquals("16", corpo.get("procedimento").get("dente").asString());
        assertEquals("16", corpo.get("dente").get("numeroDente").asString());

        Document d16 = dente(odonto, "16");
        assertEquals("CARIE", d16.getString("condicao"));
        assertEquals(List.of("OCLUSAL", "MESIAL"), d16.getList("faces", String.class), "soma faces sem apagar as existentes");
        assertEquals("HIGIDO", dente(odonto, "21").getString("condicao"), "os outros dentes ficam intactos");
        assertEquals(2, dentes(odonto).size());
        Document p = procedimentoNoBanco(proc);
        assertEquals(odonto, p.getString("odontogramaId"));
        assertEquals("16", p.getString("dente"));
        assertEquals(List.of("MESIAL"), p.getList("faces", String.class));
    }

    @Test
    void vincularComOdontogramaDeOutroPacienteOuClinica_Retorna400() throws Exception {
        Clinica c = registrar();
        String paciente = criarPaciente(c);
        String outroPaciente = criarPaciente(c);
        String doOutro = odontograma(c, outroPaciente, "[]");
        Clinica alheia = registrar();
        String pacienteAlheio = criarPaciente(alheia);
        String odontoAlheio = odontograma(alheia, pacienteAlheio, "[]");
        String proc = procedimento(c, paciente);

        for (String id : new String[]{doOutro, odontoAlheio}) {
            MvcResult r = vincular(c, proc, "{\"odontogramaId\":\"" + id + "\",\"dente\":\"16\"}");
            assertEquals(400, r.getResponse().getStatus());
            assertEquals("Odontograma vinculado inválido para este paciente.", json(r).get("mensagem").asString());
        }
        assertNull(procedimentoNoBanco(proc).getString("odontogramaId"));
        assertTrue(dentes(doOutro).isEmpty());
        assertTrue(dentes(odontoAlheio).isEmpty());
    }

    // ───────────── (b) sem odontogramaId, paciente já tem ─────────────

    @Test
    void vincularSemOdontogramaId_UsaOMaisRecenteDoPaciente() throws Exception {
        Clinica c = registrar();
        String paciente = criarPaciente(c);
        String antigo = odontograma(c, paciente, "[]");
        Thread.sleep(20);
        String recente = odontograma(c, paciente, "[{\"numeroDente\":\"11\",\"condicao\":\"HIGIDO\"}]");
        String proc = procedimento(c, paciente);

        MvcResult r = vincular(c, proc, "{\"dente\":\"36\",\"faces\":[\"OCLUSAL\"],\"condicao\":\"CARIE\"}");

        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        assertEquals(recente, json(r).get("odontograma").get("id").asString());
        assertFalse(json(r).get("odontogramaCriado").asBoolean());
        Document d36 = dente(recente, "36");
        assertEquals("CARIE", d36.getString("condicao"));
        assertEquals(List.of("OCLUSAL"), d36.getList("faces", String.class));
        assertEquals("HIGIDO", dente(recente, "11").getString("condicao"));
        assertTrue(dentes(antigo).isEmpty(), "o histórico antigo não é alterado");
    }

    // ───────────── (c) paciente sem nenhum odontograma ─────────────

    @Test
    void vincularSemOdontogramaId_PacienteSemNenhum_CriaOdontogramaMinimoNaMesmaOperacao() throws Exception {
        Clinica c = registrar();
        String paciente = criarPaciente(c);
        String proc = procedimento(c, paciente);
        assertEquals(0, mongo.getCollection("odontogramas").countDocuments(new Document("pacienteId", paciente)));

        MvcResult r = vincular(c, proc, "{\"dente\":\"46\",\"faces\":[\"OCLUSAL\",\"DISTAL\"],\"condicao\":\"CARIE\"}");

        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        assertTrue(json(r).get("odontogramaCriado").asBoolean());
        String novo = json(r).get("odontograma").get("id").asString();
        assertEquals(1, mongo.getCollection("odontogramas").countDocuments(new Document("pacienteId", paciente)));
        Document doc = odontogramaNoBanco(novo);
        assertEquals(paciente, doc.getString("pacienteId"));
        assertNotNull(doc.getString("clinicaId"));
        assertNotNull(doc.get("dataAvaliacao"));
        assertEquals(1, dentes(novo).size());
        assertEquals("CARIE", dente(novo, "46").getString("condicao"));
        assertEquals(novo, procedimentoNoBanco(proc).getString("odontogramaId"));
    }

    @Test
    void vincularSemCondicaoEmDenteNovo_MarcaSemDiagnosticoENaoInventaCondicao() throws Exception {
        Clinica c = registrar();
        String paciente = criarPaciente(c);
        String proc = procedimento(c, paciente);

        MvcResult r = vincular(c, proc, "{\"dente\":\"24\"}");

        assertEquals(200, r.getResponse().getStatus());
        String odonto = json(r).get("odontograma").get("id").asString();
        Document d24 = dente(odonto, "24");
        assertNotNull(d24);
        assertNull(d24.get("condicao"), "a condição só entra quando informada");
    }

    // ───────────── (d) FDI e coerência ─────────────

    @Test
    void vincularComDenteForaDoFdi_Retorna400SemCriarNada() throws Exception {
        Clinica c = registrar();
        String paciente = criarPaciente(c);
        String proc = procedimento(c, paciente);

        for (String dente : new String[]{"99", "19", "56", "1", "A1", ""}) {
            MvcResult r = vincular(c, proc, "{\"dente\":\"" + dente + "\"}");
            assertEquals(400, r.getResponse().getStatus(), dente);
        }
        assertEquals(400, vincular(c, proc, "{\"faces\":[\"OCLUSAL\"]}").getResponse().getStatus(), "dente obrigatório");
        assertEquals(400, vincular(c, proc, "{\"dente\":\"16\",\"faces\":[\"TOPO\"]}").getResponse().getStatus(), "face inexistente");
        assertEquals(400, vincular(c, proc, "{\"dente\":\"16\",\"condicao\":\"INVENTADA\"}").getResponse().getStatus());
        assertEquals(0, mongo.getCollection("odontogramas").countDocuments(new Document("pacienteId", paciente)));
        assertNull(procedimentoNoBanco(proc).getString("odontogramaId"));
    }

    @Test
    void vincularFacesComCondicaoQueNaoAdmite_Retorna400() throws Exception {
        Clinica c = registrar();
        String paciente = criarPaciente(c);
        String proc = procedimento(c, paciente);

        MvcResult r = vincular(c, proc, "{\"dente\":\"16\",\"faces\":[\"OCLUSAL\"],\"condicao\":\"AUSENTE\"}");

        assertEquals(400, r.getResponse().getStatus());
        assertEquals(0, mongo.getCollection("odontogramas").countDocuments(new Document("pacienteId", paciente)),
                "a validação acontece antes de criar o odontograma mínimo");
    }

    @Test
    void semCondicaoInformada_PreservaACondicaoExistente_ComCondicaoInformadaAtualiza() throws Exception {
        Clinica c = registrar();
        String paciente = criarPaciente(c);
        String odonto = odontograma(c, paciente, "[{\"numeroDente\":\"16\",\"condicao\":\"CARIE\"}]");
        String proc = procedimento(c, paciente);

        assertEquals(200, vincular(c, proc, "{\"dente\":\"16\",\"faces\":[\"OCLUSAL\"]}").getResponse().getStatus());
        assertEquals("CARIE", dente(odonto, "16").getString("condicao"));

        assertEquals(200, vincular(c, proc, "{\"dente\":\"16\",\"faces\":[\"OCLUSAL\"],\"condicao\":\"RESTAURACAO_SATISFATORIA\"}")
                .getResponse().getStatus());
        assertEquals("RESTAURACAO_SATISFATORIA", dente(odonto, "16").getString("condicao"));
    }

    // ───────────── escopo e autorização ─────────────

    @Test
    void procedimentoDeOutraClinica_Retorna404ESemJwtNaoPassa() throws Exception {
        Clinica c = registrar();
        Clinica alheia = registrar();
        String proc = procedimento(c, criarPaciente(c));

        assertEquals(404, vincular(alheia, proc, "{\"dente\":\"16\"}").getResponse().getStatus());
        int semJwt = mvc.perform(post("/api/procedimentos/" + proc + "/vincular-odontograma")
                .contentType(MediaType.APPLICATION_JSON).content("{\"dente\":\"16\"}")).andReturn().getResponse().getStatus();
        assertTrue(semJwt == 401 || semJwt == 403, "status " + semJwt);
        assertEquals(404, vincular(c, "000000000000000000000000", "{\"dente\":\"16\"}").getResponse().getStatus());
        assertNull(procedimentoNoBanco(proc).getString("odontogramaId"));
    }

    // ───────────── (e) idempotência, concorrência e falha no meio ─────────────

    @Test
    void repetirAMesmaChamada_ConvergeParaOMesmoEstadoSemDuplicar() throws Exception {
        Clinica c = registrar();
        String paciente = criarPaciente(c);
        String proc = procedimento(c, paciente);
        String corpo = "{\"dente\":\"26\",\"faces\":[\"OCLUSAL\",\"MESIAL\"],\"condicao\":\"CARIE\"}";

        MvcResult primeira = vincular(c, proc, corpo);
        String odonto = json(primeira).get("odontograma").get("id").asString();
        assertTrue(json(primeira).get("odontogramaCriado").asBoolean());
        MvcResult segunda = vincular(c, proc, corpo);
        MvcResult terceira = vincular(c, proc, corpo);

        for (MvcResult r : List.of(segunda, terceira)) {
            assertEquals(200, r.getResponse().getStatus());
            assertFalse(json(r).get("odontogramaCriado").asBoolean(), "a repetição reaproveita o odontograma criado");
            assertEquals(odonto, json(r).get("odontograma").get("id").asString());
        }
        assertEquals(1, mongo.getCollection("odontogramas").countDocuments(new Document("pacienteId", paciente)));
        assertEquals(1, dentes(odonto).size());
        assertEquals(List.of("MESIAL", "OCLUSAL"), dente(odonto, "26").getList("faces", String.class), "ordem canônica do enum");
        assertEquals(List.of("MESIAL", "OCLUSAL"), procedimentoNoBanco(proc).getList("faces", String.class));
    }

    @Test
    void doisProcedimentosNoMesmoDente_SomamAsFacesNoOdontograma() throws Exception {
        Clinica c = registrar();
        String paciente = criarPaciente(c);
        String odonto = odontograma(c, paciente, "[]");
        String p1 = procedimento(c, paciente);
        String p2 = procedimento(c, paciente);

        assertEquals(200, vincular(c, p1, "{\"odontogramaId\":\"" + odonto + "\",\"dente\":\"16\",\"faces\":[\"OCLUSAL\"],\"condicao\":\"CARIE\"}")
                .getResponse().getStatus());
        assertEquals(200, vincular(c, p2, "{\"odontogramaId\":\"" + odonto + "\",\"dente\":\"16\",\"faces\":[\"MESIAL\"]}")
                .getResponse().getStatus());

        assertEquals(1, dentes(odonto).size());
        assertEquals(List.of("OCLUSAL", "MESIAL"), dente(odonto, "16").getList("faces", String.class));
        assertEquals(List.of("OCLUSAL"), procedimentoNoBanco(p1).getList("faces", String.class));
        assertEquals(List.of("MESIAL"), procedimentoNoBanco(p2).getList("faces", String.class));
    }

    @Test
    void vinculosSimultaneos_NaoDuplicamODenteNemPerdemEscritas() throws Exception {
        for (int rodada = 0; rodada < 5; rodada++) {
            Clinica c = registrar();
            String paciente = criarPaciente(c);
            String odonto = odontograma(c, paciente, "[]");
            String[] faces = {"OCLUSAL", "MESIAL", "DISTAL", "VESTIBULAR"};
            String[] outrosDentes = {"11", "12", "13", "14"};
            List<String> procs = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                procs.add(procedimento(c, paciente));
            }

            ExecutorService pool = Executors.newFixedThreadPool(8);
            CountDownLatch largada = new CountDownLatch(1);
            List<Future<Integer>> futuros = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                final int n = i;
                futuros.add(pool.submit(() -> {
                    largada.await();
                    String dente = n < 4 ? "16" : outrosDentes[n - 4];
                    String corpo = "{\"odontogramaId\":\"" + odonto + "\",\"dente\":\"" + dente + "\",\"faces\":[\""
                            + faces[n % 4] + "\"],\"condicao\":\"CARIE\"}";
                    return vincular(c, procs.get(n), corpo).getResponse().getStatus();
                }));
            }
            largada.countDown();
            try {
                for (Future<Integer> f : futuros) {
                    assertEquals(200, f.get(60, TimeUnit.SECONDS));
                }
            } finally {
                pool.shutdownNow();
            }

            assertEquals(5, dentes(odonto).size(), "rodada " + rodada + ": 16 só uma vez e mais 4 dentes distintos");
            List<String> faces16 = dente(odonto, "16").getList("faces", String.class);
            assertEquals(4, faces16.size(), "rodada " + rodada + ": as 4 faces somadas, nenhuma perdida: " + faces16);
            assertTrue(faces16.containsAll(List.of(faces)));
            for (String outro : outrosDentes) {
                assertNotNull(dente(odonto, outro), "rodada " + rodada + ": dente " + outro + " perdido");
            }
            for (String proc : procs) {
                assertEquals(odonto, procedimentoNoBanco(proc).getString("odontogramaId"));
            }
        }
    }

    @Test
    void falhaAoGravarOProcedimento_DesfazOOdontogramaCriado_EAChamadaSeguinteFunciona() throws Exception {
        Clinica c = registrar();
        String paciente = criarPaciente(c);
        String proc = procedimento(c, paciente);
        doThrow(new RuntimeException("falha simulada")).when(mongo)
                .findAndModify(any(Query.class), any(org.springframework.data.mongodb.core.query.UpdateDefinition.class),
                        any(org.springframework.data.mongodb.core.FindAndModifyOptions.class), eq(Procedimento.class));

        MvcResult falhou = vincular(c, proc, "{\"dente\":\"16\",\"faces\":[\"OCLUSAL\"],\"condicao\":\"CARIE\"}");

        assertNotEquals(200, falhou.getResponse().getStatus());
        assertEquals(0, mongo.getCollection("odontogramas").countDocuments(new Document("pacienteId", paciente)),
                "nenhum odontograma órfão sobra");
        assertNull(procedimentoNoBanco(proc).getString("odontogramaId"), "o procedimento continua como estava");

        org.mockito.Mockito.reset(mongo);
        MvcResult tentativa = vincular(c, proc, "{\"dente\":\"16\",\"faces\":[\"OCLUSAL\"],\"condicao\":\"CARIE\"}");
        assertEquals(200, tentativa.getResponse().getStatus(), tentativa.getResponse().getContentAsString());
        assertEquals(1, mongo.getCollection("odontogramas").countDocuments(new Document("pacienteId", paciente)));
    }

    @Test
    void falhaAoGravarOProcedimento_RestauraODenteQueJaExistia() throws Exception {
        Clinica c = registrar();
        String paciente = criarPaciente(c);
        String odonto = odontograma(c, paciente, "[{\"numeroDente\":\"16\",\"condicao\":\"CARIE\",\"faces\":[\"OCLUSAL\"],\"observacao\":\"antes\"}]");
        String proc = procedimento(c, paciente);
        doThrow(new RuntimeException("falha simulada")).when(mongo)
                .findAndModify(any(Query.class), any(org.springframework.data.mongodb.core.query.UpdateDefinition.class),
                        any(org.springframework.data.mongodb.core.FindAndModifyOptions.class), eq(Procedimento.class));

        MvcResult falhou = vincular(c, proc, "{\"odontogramaId\":\"" + odonto + "\",\"dente\":\"16\",\"faces\":[\"MESIAL\"],\"condicao\":\"RESTAURACAO_SATISFATORIA\"}");

        assertNotEquals(200, falhou.getResponse().getStatus());
        Document d16 = dente(odonto, "16");
        assertEquals("CARIE", d16.getString("condicao"), "a condição volta ao que era");
        assertEquals(List.of("OCLUSAL"), d16.getList("faces", String.class), "as faces voltam ao que eram");
        assertEquals("antes", d16.getString("observacao"));
        assertNull(procedimentoNoBanco(proc).getString("odontogramaId"));
    }

    @Test
    void falhaAoGravarOProcedimento_RemoveODenteAcrescentadoEmOdontogramaExistente() throws Exception {
        Clinica c = registrar();
        String paciente = criarPaciente(c);
        String odonto = odontograma(c, paciente, "[{\"numeroDente\":\"11\",\"condicao\":\"HIGIDO\"}]");
        String proc = procedimento(c, paciente);
        doThrow(new RuntimeException("falha simulada")).when(mongo)
                .findAndModify(any(Query.class), any(org.springframework.data.mongodb.core.query.UpdateDefinition.class),
                        any(org.springframework.data.mongodb.core.FindAndModifyOptions.class), eq(Procedimento.class));

        assertNotEquals(200, vincular(c, proc, "{\"odontogramaId\":\"" + odonto + "\",\"dente\":\"16\",\"condicao\":\"CARIE\"}")
                .getResponse().getStatus());

        assertNull(dente(odonto, "16"), "o dente acrescentado é removido");
        assertEquals(1, dentes(odonto).size());
        assertEquals("HIGIDO", dente(odonto, "11").getString("condicao"));
    }
}

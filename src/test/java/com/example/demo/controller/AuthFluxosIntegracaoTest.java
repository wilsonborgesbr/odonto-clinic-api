package com.example.demo.controller;

import com.example.demo.email.EmailService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mockito.invocation.Invocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Integração ponta a ponta (MockMvc + Mongo local em banco temporário próprio, derrubado ao final).
 * O envio de e-mail é substituído por um mock; os tokens são lidos dos links dos e-mails capturados.
 * Ignorado quando não há Mongo em localhost:27017.
 */
@EnabledIf("mongoLocalDisponivel")
@SpringBootTest(properties = {
        "spring.mongodb.uri=mongodb://localhost:27017/tmp_teste_auth_fluxos",
        "app.frontend-url=https://bokka.com.br"})
@AutoConfigureMockMvc
class AuthFluxosIntegracaoTest {

    static boolean mongoLocalDisponivel() {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("localhost", 27017), 500);
            return true;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SENHA = "senha123";

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

    // ───────────── helpers ─────────────

    private String unico() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private MvcResult postJson(String caminho, String corpo, String ip, String token) throws Exception {
        MockHttpServletRequestBuilder req = post(caminho).contentType(MediaType.APPLICATION_JSON).content(corpo)
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                });
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(req).andReturn();
    }

    private JsonNode corpo(MvcResult r) throws Exception {
        return JSON.readTree(r.getResponse().getContentAsString());
    }

    private record Clinica(String codigo, String email, String token) {}

    private Clinica registrar(String codigo, String email, String ip) throws Exception {
        String body = "{\"clinicaCodigo\":\"" + codigo + "\",\"clinicaNome\":\"Clínica " + codigo + "\","
                + "\"adminNome\":\"Dono " + codigo + "\",\"adminEmail\":\"" + email + "\",\"adminPassword\":\"" + SENHA + "\"}";
        MvcResult r = postJson("/auth/register-clinica", body, ip, null);
        assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        assertEquals("Clínica " + codigo, com.auth0.jwt.JWT.decode(corpo(r).get("token").asString()).getClaim("clinicaNome").asString(), "register-clinica");
        return new Clinica(codigo, email, corpo(r).get("token").asString());
    }

    private String loginBody(String codigoOuNull, String email, String senha) {
        String c = codigoOuNull == null ? "" : "\"clinicaCodigo\":\"" + codigoOuNull + "\",";
        return "{" + c + "\"email\":\"" + email + "\",\"password\":\"" + senha + "\"}";
    }

    private String htmlDoEmail(String destino, int n) throws Exception {
        long limite = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < limite) {
            List<Invocation> envios = org.mockito.Mockito.mockingDetails(emailService).getInvocations().stream()
                    .filter(i -> i.getArguments().length == 5 && destino.equals(i.getArgument(0))).toList();
            if (envios.size() >= n) {
                return envios.get(n - 1).getArgument(2);
            }
            Thread.sleep(50);
        }
        fail("e-mail #" + n + " para " + destino + " não foi enviado a tempo");
        return null;
    }

    private String assuntoDoEmail(String destino, int n) {
        return org.mockito.Mockito.mockingDetails(emailService).getInvocations().stream()
                .filter(i -> i.getArguments().length == 5 && destino.equals(i.getArgument(0)))
                .toList().get(n - 1).getArgument(1);
    }

    private int totalDeEmails(String destino) {
        return (int) org.mockito.Mockito.mockingDetails(emailService).getInvocations().stream()
                .filter(i -> i.getArguments().length == 5 && destino.equals(i.getArgument(0))).count();
    }

    private String tokenDoLink(String html, String rota) {
        Matcher m = Pattern.compile(Pattern.quote("https://bokka.com.br/" + rota) + "\\?token=([A-Za-z0-9_-]+)").matcher(html);
        assertTrue(m.find(), "link de " + rota + " não encontrado no e-mail: " + html);
        return m.group(1);
    }

    private JsonNode me(String jwt) throws Exception {
        MvcResult r = mvc.perform(get("/api/usuarios/me").header("Authorization", "Bearer " + jwt)).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        return corpo(r);
    }

    // ───────────── confirmação de e-mail do dono ─────────────

    @Test
    void registerClinica_DonoRecebeJwtComEmailNaoConfirmadoEConfirmaPeloLink() throws Exception {
        String id = unico();
        String email = "dono-" + id + "@teste.com";
        Clinica c = registrar("conf-" + id, email, "10.10.0.1");

        assertNotNull(c.token(), "o dono continua recebendo o JWT sem confirmar");
        assertFalse(me(c.token()).get("emailConfirmado").asBoolean());

        String link = htmlDoEmail(email, 1);
        assertEquals("Confirme seu e-mail - Bokka", assuntoDoEmail(email, 1));
        String token = tokenDoLink(link, "confirmar-email");
        assertFalse(link.contains("localhost"), "links só via app.frontend-url");

        MvcResult confirmado = postJson("/auth/confirmar-email", "{\"token\":\"" + token + "\"}", "10.10.0.1", null);
        assertEquals(200, confirmado.getResponse().getStatus());
        assertFalse(corpo(confirmado).get("jaConfirmado").asBoolean());
        assertTrue(me(c.token()).get("emailConfirmado").asBoolean(), "o JWT não é reemitido: o estado vem do /me");

        MvcResult reuso = postJson("/auth/confirmar-email", "{\"token\":\"" + token + "\"}", "10.10.0.1", null);
        assertEquals(400, reuso.getResponse().getStatus());
        assertEquals(AuthTokenServiceMensagem.PADRAO, corpo(reuso).get("mensagem").asString());
    }

    @Test
    void reenviarConfirmacao_GeraNovoLinkInvalidaOAnteriorEJaConfirmadoNaoReenvia() throws Exception {
        String id = unico();
        String email = "reenvio-" + id + "@teste.com";
        Clinica c = registrar("reen-" + id, email, "10.10.1.1");
        String primeiro = tokenDoLink(htmlDoEmail(email, 1), "confirmar-email");

        MvcResult reenvio = postJson("/auth/reenviar-confirmacao", "{}", "10.10.1.1", c.token());
        assertEquals(200, reenvio.getResponse().getStatus());
        String segundo = tokenDoLink(htmlDoEmail(email, 2), "confirmar-email");
        assertNotEquals(primeiro, segundo);

        assertEquals(400, postJson("/auth/confirmar-email", "{\"token\":\"" + primeiro + "\"}", "10.10.1.1", null)
                .getResponse().getStatus());
        assertEquals(200, postJson("/auth/confirmar-email", "{\"token\":\"" + segundo + "\"}", "10.10.1.1", null)
                .getResponse().getStatus());

        MvcResult jaConfirmado = postJson("/auth/reenviar-confirmacao", "{}", "10.10.1.1", c.token());
        assertEquals(200, jaConfirmado.getResponse().getStatus());
        Thread.sleep(300);
        assertEquals(2, totalDeEmails(email), "já confirmado não dispara novo e-mail");
    }

    @Test
    void reenviarConfirmacao_ExigeJwtEAplicaLimiteDeTresPedidos() throws Exception {
        String id = unico();
        String email = "limite-" + id + "@teste.com";
        Clinica c = registrar("lim-" + id, email, "10.10.2.1");

        int semJwt = postJson("/auth/reenviar-confirmacao", "{}", "10.10.2.2", null).getResponse().getStatus();
        assertTrue(semJwt == 401 || semJwt == 403, "sem JWT deve ser barrado, veio " + semJwt);

        for (int i = 0; i < 3; i++) {
            assertEquals(200, postJson("/auth/reenviar-confirmacao", "{}", "10.10.2." + (10 + i), c.token()).getResponse().getStatus());
        }
        MvcResult limitado = postJson("/auth/reenviar-confirmacao", "{}", "10.10.2.99", c.token());
        assertEquals(429, limitado.getResponse().getStatus());
    }

    @Test
    void confirmarEmail_TokenDeOutroTipoOuInventadoRetorna400() throws Exception {
        String id = unico();
        String email = "tipo-" + id + "@teste.com";
        registrar("tipo-" + id, email, "10.10.3.1");
        postJson("/auth/recuperar-senha", "{\"email\":\"" + email + "\",\"clinicaCodigo\":\"tipo-" + id + "\"}", "10.10.3.1", null);
        String tokenReset = tokenDoLink(htmlDoEmail(email, 2), "redefinir-senha");

        MvcResult r = postJson("/auth/confirmar-email", "{\"token\":\"" + tokenReset + "\"}", "10.10.3.1", null);
        assertEquals(400, r.getResponse().getStatus());
        assertEquals(400, postJson("/auth/confirmar-email", "{\"token\":\"inventado\"}", "10.10.3.1", null).getResponse().getStatus());

        MvcResult aindaValido = postJson("/auth/validar-token",
                "{\"token\":\"" + tokenReset + "\",\"tipo\":\"RESET_SENHA\"}", "10.10.3.1", null);
        assertEquals(200, aindaValido.getResponse().getStatus(), "tentar usar no endpoint errado não queima o token");
    }

    // ───────────── recuperação de senha ─────────────

    @Test
    void recuperarSenha_FluxoCompletoRedefineEBloqueiaReuso() throws Exception {
        String id = unico();
        String codigo = "rec-" + id;
        String email = "rec-" + id + "@teste.com";
        registrar(codigo, email, "10.20.0.1");

        MvcResult pedido = postJson("/auth/recuperar-senha", "{\"email\":\"" + email + "\",\"clinicaCodigo\":\"" + codigo + "\"}", "10.20.0.1", null);
        assertEquals(200, pedido.getResponse().getStatus());
        String html = htmlDoEmail(email, 2);
        assertEquals("Redefinir sua senha no Bokka", assuntoDoEmail(email, 2));
        assertTrue(html.contains("c=" + codigo));
        String token = tokenDoLink(html, "redefinir-senha");

        JsonNode validacao = corpo(postJson("/auth/validar-token", "{\"token\":\"" + token + "\",\"tipo\":\"RESET_SENHA\"}", "10.20.0.1", null));
        assertTrue(validacao.get("valido").asBoolean());
        assertEquals("Dono " + codigo, validacao.get("nomeUsuario").asString());
        assertEquals("Clínica " + codigo, validacao.get("clinicaNome").asString());
        assertEquals(codigo, validacao.get("clinicaCodigo").asString());

        MvcResult fraca = postJson("/auth/redefinir-senha", "{\"token\":\"" + token + "\",\"novaSenha\":\"123\"}", "10.20.0.1", null);
        assertEquals(400, fraca.getResponse().getStatus());
        assertTrue(corpo(fraca).get("mensagem").has("novaSenha"), "senha recusada vem por campo novaSenha");

        MvcResult ok = postJson("/auth/redefinir-senha", "{\"token\":\"" + token + "\",\"novaSenha\":\"NovaSenha#77\"}", "10.20.0.1", null);
        assertEquals(200, ok.getResponse().getStatus(), "senha fraca não pode ter queimado o token");
        String alerta = htmlDoEmail(email, 3);
        assertEquals("Sua senha foi alterada - Bokka", assuntoDoEmail(email, 3));
        assertTrue(alerta.contains("https://bokka.com.br/recuperar-senha?c=" + codigo));
        assertFalse(alerta.contains("NovaSenha#77"));

        MvcResult reuso = postJson("/auth/redefinir-senha", "{\"token\":\"" + token + "\",\"novaSenha\":\"OutraSenha#88\"}", "10.20.0.1", null);
        assertEquals(400, reuso.getResponse().getStatus());
        assertEquals(AuthTokenServiceMensagem.PADRAO, corpo(reuso).get("mensagem").asString(), "link usado: mensagem padrão sem campo");
        assertEquals(400, postJson("/auth/validar-token", "{\"token\":\"" + token + "\",\"tipo\":\"RESET_SENHA\"}", "10.20.0.1", null)
                .getResponse().getStatus());

        assertEquals(401, postJson("/auth/login", loginBody(codigo, email, SENHA), "10.20.0.2", null).getResponse().getStatus());
        assertEquals(200, postJson("/auth/login", loginBody(codigo, email, "NovaSenha#77"), "10.20.0.2", null).getResponse().getStatus());
    }

    @Test
    void recuperarSenha_RespostaIdenticaExistaOuNaoAConta() throws Exception {
        String id = unico();
        String codigo = "neutro-" + id;
        String email = "neutro-" + id + "@teste.com";
        registrar(codigo, email, "10.20.1.1");

        MvcResult existe = postJson("/auth/recuperar-senha", "{\"email\":\"" + email + "\",\"clinicaCodigo\":\"" + codigo + "\"}", "10.20.1.2", null);
        MvcResult naoExiste = postJson("/auth/recuperar-senha", "{\"email\":\"fantasma-" + id + "@teste.com\",\"clinicaCodigo\":\"" + codigo + "\"}", "10.20.1.3", null);
        MvcResult clinicaFalsa = postJson("/auth/recuperar-senha", "{\"email\":\"" + email + "\",\"clinicaCodigo\":\"nao-existe-" + id + "\"}", "10.20.1.4", null);
        MvcResult semCodigo = postJson("/auth/recuperar-senha", "{\"email\":\"fantasma2-" + id + "@teste.com\"}", "10.20.1.5", null);

        for (MvcResult r : List.of(existe, naoExiste, clinicaFalsa, semCodigo)) {
            assertEquals(200, r.getResponse().getStatus());
        }
        String esperado = existe.getResponse().getContentAsString();
        assertEquals(esperado, naoExiste.getResponse().getContentAsString());
        assertEquals(esperado, clinicaFalsa.getResponse().getContentAsString());
        assertEquals(esperado, semCodigo.getResponse().getContentAsString());
        assertTrue(esperado.contains("Se os dados informados estiverem corretos"));
        Thread.sleep(400);
        assertEquals(0, totalDeEmails("fantasma-" + id + "@teste.com"));
    }

    @Test
    void recuperarSenha_QuartoPedidoNaoEnviaMasContinuaResponding200() throws Exception {
        String id = unico();
        String codigo = "rl-" + id;
        String email = "rl-" + id + "@teste.com";
        registrar(codigo, email, "10.20.2.1");
        htmlDoEmail(email, 1);
        int antes = totalDeEmails(email);

        for (int i = 0; i < 6; i++) {
            MvcResult r = postJson("/auth/recuperar-senha", "{\"email\":\"" + email + "\",\"clinicaCodigo\":\"" + codigo + "\"}", "10.20.2." + (10 + i), null);
            assertEquals(200, r.getResponse().getStatus());
        }

        htmlDoEmail(email, antes + 3);
        Thread.sleep(600);
        assertEquals(antes + 3, totalDeEmails(email), "só 3 pedidos por e-mail em 15 min geram e-mail");
    }

    @Test
    void validarToken_TipoDesconhecidoEBodyIncompletoRetornam400() throws Exception {
        assertEquals(400, postJson("/auth/validar-token", "{\"token\":\"x\",\"tipo\":\"NADA\"}", "10.20.3.1", null).getResponse().getStatus());
        assertEquals(400, postJson("/auth/validar-token", "{\"token\":\"x\"}", "10.20.3.1", null).getResponse().getStatus());
    }

    // ───────────── login com e sem código ─────────────

    @Test
    void login_ComCodigoContinuaIdenticoEDevolveEmailConfirmado() throws Exception {
        String id = unico();
        String codigo = "lc-" + id;
        String email = "lc-" + id + "@teste.com";
        registrar(codigo, email, "10.30.0.1");

        MvcResult ok = postJson("/auth/login", loginBody(codigo.toUpperCase(), email.toUpperCase(), SENHA), "10.30.0.2", null);
        assertEquals(200, ok.getResponse().getStatus());
        assertNotNull(corpo(ok).get("token"));
        assertFalse(corpo(ok).get("emailConfirmado").asBoolean());
        assertEquals("Clínica " + codigo, com.auth0.jwt.JWT.decode(corpo(ok).get("token").asString()).getClaim("clinicaNome").asString(), "login com código");

        MvcResult erradaSenha = postJson("/auth/login", loginBody(codigo, email, "errada"), "10.30.0.3", null);
        assertEquals(401, erradaSenha.getResponse().getStatus());
        assertEquals("Credenciais inválidas.", corpo(erradaSenha).get("mensagem").asString());
        MvcResult clinicaFalsa = postJson("/auth/login", loginBody("nao-existe-" + id, email, SENHA), "10.30.0.4", null);
        assertEquals(401, clinicaFalsa.getResponse().getStatus());
        assertEquals("Clínica não encontrada.", corpo(clinicaFalsa).get("mensagem").asString());
    }

    @Test
    void login_SemCodigoOmitidoNuloOuVazioFuncionamIgual() throws Exception {
        String id = unico();
        String email = "sc-" + id + "@teste.com";
        registrar("sc-" + id, email, "10.30.1.1");

        String omitido = "{\"email\":\"" + email + "\",\"password\":\"" + SENHA + "\"}";
        String nulo = "{\"clinicaCodigo\":null,\"email\":\"" + email + "\",\"password\":\"" + SENHA + "\"}";
        String vazio = "{\"clinicaCodigo\":\"\",\"email\":\"" + email + "\",\"password\":\"" + SENHA + "\"}";
        int n = 0;
        for (String body : List.of(omitido, nulo, vazio)) {
            MvcResult r = postJson("/auth/login", body, "10.30.1." + (10 + n++), null);
            assertEquals(200, r.getResponse().getStatus(), body);
            assertNotNull(corpo(r).get("token"), body);
            assertEquals("Clínica sc-" + id, com.auth0.jwt.JWT.decode(corpo(r).get("token").asString()).getClaim("clinicaNome").asString(), "login sem código: " + body);
        }
    }

    @Test
    void login_SemCodigoNaoRevelaNadaAntesDaSenhaCasar() throws Exception {
        String id = unico();
        String email = "rev-" + id + "@teste.com";
        registrar("rev-a-" + id, email, "10.30.2.1");
        registrar("rev-b-" + id, email, "10.30.2.1");

        MvcResult erradaSenha = postJson("/auth/login", loginBody(null, email, "errada"), "10.30.2.2", null);
        MvcResult semConta = postJson("/auth/login", loginBody(null, "fantasma-" + id + "@teste.com", "errada"), "10.30.2.3", null);

        assertEquals(401, erradaSenha.getResponse().getStatus());
        assertEquals(401, semConta.getResponse().getStatus());
        JsonNode a = corpo(erradaSenha);
        JsonNode b = corpo(semConta);
        assertEquals(a.get("mensagem").asString(), b.get("mensagem").asString());
        assertFalse(erradaSenha.getResponse().getContentAsString().contains("rev-a-" + id));
        assertFalse(erradaSenha.getResponse().getContentAsString().contains("ticket"));
    }

    @Test
    void login_MesmaSenhaEmDuasClinicasPedeSelecaoEOTicketEmiteOJwtDaEscolhida() throws Exception {
        String id = unico();
        String email = "multi-" + id + "@teste.com";
        registrar("multi-a-" + id, email, "10.30.3.1");
        registrar("multi-b-" + id, email, "10.30.3.1");

        MvcResult r = postJson("/auth/login", loginBody(null, email, SENHA), "10.30.3.2", null);
        assertEquals(200, r.getResponse().getStatus());
        JsonNode selecao = corpo(r);
        assertEquals("SELECAO_CLINICA", selecao.get("status").asString());
        assertEquals(2, selecao.get("clinicas").size());
        assertFalse(selecao.has("token"));
        String ticket = selecao.get("ticket").asString();
        JsonNode escolhida = selecao.get("clinicas").get(1);
        assertEquals("multi-b-" + id, escolhida.get("codigo").asString());
        assertNotNull(escolhida.get("id"));
        assertEquals("Clínica multi-b-" + id, escolhida.get("nome").asString());

        MvcResult sel = postJson("/auth/login/selecionar-clinica",
                "{\"ticket\":\"" + ticket + "\",\"clinicaId\":\"" + escolhida.get("id").asString() + "\"}", "10.30.3.2", null);
        assertEquals(200, sel.getResponse().getStatus());
        String jwt = corpo(sel).get("token").asString();
        assertEquals("multi-b-" + id, com.auth0.jwt.JWT.decode(jwt).getClaim("clinicaCodigo").asString());
        assertEquals("Clínica multi-b-" + id, com.auth0.jwt.JWT.decode(jwt).getClaim("clinicaNome").asString(), "selecionar-clinica");
        assertEquals(200, mvc.perform(get("/api/usuarios/me").header("Authorization", "Bearer " + jwt)).andReturn().getResponse().getStatus());
    }

    @Test
    void selecionarClinica_TicketInvalidoClinicaAlheiaETicketComoBearerSaoBarrados() throws Exception {
        String id = unico();
        String email = "tk-" + id + "@teste.com";
        registrar("tk-a-" + id, email, "10.30.4.1");
        registrar("tk-b-" + id, email, "10.30.4.1");
        String outroEmail = "tk-outro-" + id + "@teste.com";
        Clinica alheia = registrar("tk-c-" + id, outroEmail, "10.30.4.1");

        JsonNode selecao = corpo(postJson("/auth/login", loginBody(null, email, SENHA), "10.30.4.2", null));
        String ticket = selecao.get("ticket").asString();
        String idAlheia = mongo.findOne(org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("codigo").is(alheia.codigo())),
                com.example.demo.model.Clinica.class).getId();

        int invalido = postJson("/auth/login/selecionar-clinica", "{\"ticket\":\"lixo\",\"clinicaId\":\"x\"}", "10.30.4.3", null).getResponse().getStatus();
        assertTrue(invalido == 400 || invalido == 401 || invalido == 410, "ticket inválido deve ser 400/401/410, veio " + invalido);
        int alheiaStatus = postJson("/auth/login/selecionar-clinica",
                "{\"ticket\":\"" + ticket + "\",\"clinicaId\":\"" + idAlheia + "\"}", "10.30.4.3", null).getResponse().getStatus();
        assertEquals(401, alheiaStatus, "o ticket só vale para as clínicas em que a senha casou");

        int comoBearer = mvc.perform(get("/api/usuarios/me").header("Authorization", "Bearer " + ticket)).andReturn().getResponse().getStatus();
        assertTrue(comoBearer == 401 || comoBearer == 403, "ticket não pode ser usado como token de acesso, veio " + comoBearer);
    }

    @Test
    void login_DezSenhasErradasBloqueiamComMensagemNeutra() throws Exception {
        String id = unico();
        String email = "bf-" + id + "@teste.com";
        registrar("bf-" + id, email, "10.30.5.1");

        for (int i = 0; i < 10; i++) {
            assertEquals(401, postJson("/auth/login", loginBody(null, email, "errada" + i), "10.30.5." + (10 + i), null).getResponse().getStatus());
        }
        MvcResult bloqueado = postJson("/auth/login", loginBody(null, email, SENHA), "10.30.5.99", null);

        assertEquals(429, bloqueado.getResponse().getStatus());
        assertTrue(corpo(bloqueado).get("mensagem").asString().startsWith("Muitas tentativas"));
        assertFalse(bloqueado.getResponse().getContentAsString().contains("bf-" + id));
    }

    // ───────────── legado ─────────────

    @Test
    void usuarioLegadoSemOsCamposNovosContinuaEntrandoEContaComoConfirmado() throws Exception {
        String id = unico();
        String codigo = "leg-" + id;
        String email = "legado-" + id + "@teste.com";
        registrar(codigo, "dono-leg-" + id + "@teste.com", "10.40.0.1");
        String clinicaId = mongo.findOne(org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("codigo").is(codigo)),
                com.example.demo.model.Clinica.class).getId();
        mongo.getCollection("users").insertOne(new org.bson.Document("email", email).append("name", "Legado")
                .append("password", new BCryptPasswordEncoder().encode(SENHA)).append("clinicaId", clinicaId)
                .append("role", "RECEPCIONISTA").append("ativo", true));

        MvcResult comCodigo = postJson("/auth/login", loginBody(codigo, email, SENHA), "10.40.0.2", null);
        MvcResult semCodigo = postJson("/auth/login", loginBody(null, email, SENHA), "10.40.0.3", null);

        for (MvcResult r : List.of(comCodigo, semCodigo)) {
            assertEquals(200, r.getResponse().getStatus());
            assertTrue(corpo(r).get("emailConfirmado").asBoolean());
        }
        assertTrue(me(corpo(comCodigo).get("token").asString()).get("emailConfirmado").asBoolean());
    }

    @Test
    void endpointsPublicosNaoExigemJwtEOsProtegidosContinuamProtegidos() throws Exception {
        for (String caminho : List.of("/auth/recuperar-senha", "/auth/validar-token", "/auth/redefinir-senha",
                "/auth/confirmar-email", "/auth/login/selecionar-clinica")) {
            int status = postJson(caminho, "{}", "10.50.0.1", null).getResponse().getStatus();
            assertEquals(400, status, caminho + " deve chegar ao controller (400 de validação), não ser barrado");
        }
        int pacientes = mvc.perform(get("/api/pacientes")).andReturn().getResponse().getStatus();
        assertTrue(pacientes == 401 || pacientes == 403);
    }

    private static final class AuthTokenServiceMensagem {
        static final String PADRAO = "Link inválido ou expirado. Solicite um novo.";
    }
}

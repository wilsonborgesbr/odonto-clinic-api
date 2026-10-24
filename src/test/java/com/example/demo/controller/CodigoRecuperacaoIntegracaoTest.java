package com.example.demo.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.demo.email.EmailService;
import com.example.demo.enums.TipoTokenAuthEnum;
import com.example.demo.service.AuthTokenService;
import com.example.demo.service.RateLimitService;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mockito.invocation.Invocation;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Integração do código de 6 dígitos na recuperação de senha (MockMvc + Mongo local em banco temporário
 * próprio, derrubado ao final; não toca em {@code sistema_clinica}). O envio de e-mail é um mock: o código e
 * o link são lidos dos argumentos capturados. Ignorado sem Mongo em localhost:27017.
 */
@EnabledIf("mongoLocalDisponivel")
@SpringBootTest(properties = {
        "spring.mongodb.uri=mongodb://localhost:27017/tmp_teste_codigo_recuperacao",
        "app.frontend-url=https://bokka.com.br"})
@AutoConfigureMockMvc
class CodigoRecuperacaoIntegracaoTest {

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
    private static final String MSG_INVALIDO = "Código inválido ou expirado. Verifique os dados ou solicite um novo.";
    private static final String MSG_LIMITE = "Muitas tentativas. Aguarde alguns minutos e tente novamente.";
    private static final Pattern CODIGO = Pattern.compile("(?m)^(\\d{3}) (\\d{3})$");

    @Autowired
    MockMvc mvc;
    @Autowired
    MongoTemplate mongo;
    @Autowired
    AuthTokenService authTokenService;
    @Autowired
    RateLimitService rateLimitService;

    @MockitoBean
    EmailService emailService;

    private final Logger raiz = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void capturarLogs() {
        logs = new ListAppender<>();
        logs.start();
        raiz.addAppender(logs);
    }

    @AfterEach
    void liberarLogs() {
        raiz.detachAppender(logs);
    }

    @AfterAll
    static void derrubarBanco(@Autowired MongoTemplate mongo) {
        mongo.getDb().drop();
    }

    // ───────────── helpers ─────────────

    private String unico() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String ipUnico() {
        return "10.50." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
    }

    private MvcResult postJson(String caminho, String corpo, String ip) throws Exception {
        return mvc.perform(post(caminho).contentType(MediaType.APPLICATION_JSON).content(corpo)
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                })).andReturn();
    }

    private JsonNode corpo(MvcResult r) throws Exception {
        return JSON.readTree(r.getResponse().getContentAsString());
    }

    private record Conta(String codigo, String email, String nome, String clinicaNome) {}

    private Conta registrar() throws Exception {
        String id = unico();
        Conta c = new Conta("cod-" + id, "cod-" + id + "@teste.com", "Dono " + id, "Clínica " + id);
        String body = "{\"clinicaCodigo\":\"" + c.codigo() + "\",\"clinicaNome\":\"" + c.clinicaNome() + "\","
                + "\"adminNome\":\"" + c.nome() + "\",\"adminEmail\":\"" + c.email() + "\",\"adminPassword\":\"" + SENHA + "\"}";
        assertEquals(201, postJson("/auth/register-clinica", body, ipUnico()).getResponse().getStatus());
        return c;
    }

    private record EmailReset(String html, String texto, String codigo, String token) {}

    private List<Invocation> emailsDeReset(String destino) {
        return org.mockito.Mockito.mockingDetails(emailService).getInvocations().stream()
                .filter(i -> i.getArguments().length == 5 && destino.equals(i.getArgument(0))
                        && "reset-senha".equals(i.getArgument(4)))
                .toList();
    }

    private EmailReset emailDeReset(String destino, int n) throws Exception {
        long limite = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < limite) {
            List<Invocation> envios = emailsDeReset(destino);
            if (envios.size() >= n) {
                String html = envios.get(n - 1).getArgument(2);
                String texto = envios.get(n - 1).getArgument(3);
                Matcher codigo = CODIGO.matcher(texto);
                assertTrue(codigo.find(), "código formatado no texto: " + texto);
                Matcher token = Pattern.compile("redefinir-senha\\?token=([A-Za-z0-9_-]+)").matcher(texto);
                assertTrue(token.find());
                return new EmailReset(html, texto, codigo.group(1) + codigo.group(2), token.group(1));
            }
            Thread.sleep(50);
        }
        fail("e-mail de reset #" + n + " para " + destino + " não foi enviado a tempo");
        return null;
    }

    /** Pede a recuperação (IP próprio para não bater no limite) e devolve o n-ésimo e-mail. */
    private EmailReset pedirRecuperacao(Conta c, int n) throws Exception {
        MvcResult r = postJson("/auth/recuperar-senha",
                "{\"email\":\"" + c.email() + "\",\"clinicaCodigo\":\"" + c.codigo() + "\"}", ipUnico());
        assertEquals(200, r.getResponse().getStatus());
        return emailDeReset(c.email(), n);
    }

    private MvcResult validarCodigo(Conta c, String codigo, String ip) throws Exception {
        return postJson("/auth/validar-codigo-recuperacao",
                "{\"email\":\"" + c.email() + "\",\"clinicaCodigo\":\"" + c.codigo() + "\",\"codigo\":\"" + codigo + "\"}", ip);
    }

    private String errado(String certo) {
        return certo.equals("000000") ? "000001" : "000000";
    }

    private void liberarLimiteDoEmail(Conta c) {
        rateLimitService.esquecer("validar-codigo:email:" + c.email());
    }

    private Document tokenNoBanco(Conta c) {
        Document user = mongo.getCollection("users").find(new Document("email", c.email())).first();
        return mongo.getCollection("auth_tokens").find(new Document("userId", user.getObjectId("_id").toHexString())
                .append("tipo", "RESET_SENHA")).sort(new Document("criadoEm", -1)).first();
    }

    private void assertSemSegredoNoLog(String... segredos) {
        for (ILoggingEvent e : logs.list) {
            String texto = e.getFormattedMessage() + (e.getThrowableProxy() != null ? e.getThrowableProxy().getMessage() : "");
            for (String segredo : segredos) {
                assertFalse(texto.contains(segredo), "segredo no log: " + e.getLoggerName() + " " + texto);
            }
        }
    }

    // ───────────── fluxo feliz e invalidações ─────────────

    @Test
    void codigoCorreto_DevolveTicketQueRedefineASenhaEQueSoServeUmaVez() throws Exception {
        Conta c = registrar();
        EmailReset email = pedirRecuperacao(c, 1);

        MvcResult r = validarCodigo(c, email.codigo(), ipUnico());

        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        JsonNode json = corpo(r);
        assertTrue(json.get("valido").asBoolean());
        assertEquals(c.nome(), json.get("nomeUsuario").asString());
        assertEquals(c.clinicaNome(), json.get("clinicaNome").asString());
        assertEquals(c.codigo(), json.get("clinicaCodigo").asString());
        String ticket = json.get("ticket").asString();
        assertTrue(ticket.matches("[A-Za-z0-9_-]{43}"));
        assertNotEquals(email.token(), ticket);

        MvcResult redefinir = postJson("/auth/redefinir-senha", "{\"token\":\"" + ticket + "\",\"novaSenha\":\"NovaSenha!9\"}", ipUnico());
        assertEquals(200, redefinir.getResponse().getStatus(), redefinir.getResponse().getContentAsString());
        assertEquals(400, postJson("/auth/redefinir-senha", "{\"token\":\"" + ticket + "\",\"novaSenha\":\"Outra!123\"}", ipUnico())
                .getResponse().getStatus(), "o ticket é de uso único");
        assertEquals(200, postJson("/auth/login", "{\"clinicaCodigo\":\"" + c.codigo() + "\",\"email\":\"" + c.email()
                + "\",\"password\":\"NovaSenha!9\"}", ipUnico()).getResponse().getStatus());
    }

    @Test
    void ticketTemCincoMinutosDeValidade() throws Exception {
        Conta c = registrar();
        EmailReset email = pedirRecuperacao(c, 1);

        String ticket = corpo(validarCodigo(c, email.codigo(), ipUnico())).get("ticket").asString();

        Document doc = mongo.getCollection("auth_tokens").find(new Document("tokenHash", AuthTokenServiceHashes.hash(ticket))).first();
        assertNotNull(doc);
        Instant criado = ((Date) doc.get("criadoEm")).toInstant();
        Instant expira = ((Date) doc.get("expiraEm")).toInstant();
        assertEquals(Duration.ofMinutes(5), Duration.between(criado, expira));
        assertNull(doc.get("codigoHash"), "o ticket não carrega código");
    }

    @Test
    void codigoUsado_NaoServeDeNovo() throws Exception {
        Conta c = registrar();
        EmailReset email = pedirRecuperacao(c, 1);
        assertEquals(200, validarCodigo(c, email.codigo(), ipUnico()).getResponse().getStatus());

        MvcResult reuso = validarCodigo(c, email.codigo(), ipUnico());

        assertEquals(400, reuso.getResponse().getStatus());
        assertEquals(MSG_INVALIDO, corpo(reuso).get("mensagem").asString());
    }

    @Test
    void codigoValidado_MataOLinkDoMesmoEmail() throws Exception {
        Conta c = registrar();
        EmailReset email = pedirRecuperacao(c, 1);
        assertEquals(200, postJson("/auth/validar-token", "{\"token\":\"" + email.token() + "\",\"tipo\":\"RESET_SENHA\"}", ipUnico())
                .getResponse().getStatus());
        assertEquals(200, validarCodigo(c, email.codigo(), ipUnico()).getResponse().getStatus());

        assertEquals(400, postJson("/auth/validar-token", "{\"token\":\"" + email.token() + "\",\"tipo\":\"RESET_SENHA\"}", ipUnico())
                .getResponse().getStatus());
        assertEquals(400, postJson("/auth/redefinir-senha", "{\"token\":\"" + email.token() + "\",\"novaSenha\":\"NovaSenha!9\"}", ipUnico())
                .getResponse().getStatus());
    }

    @Test
    void linkUsado_MataOCodigoDoMesmoEmail() throws Exception {
        Conta c = registrar();
        EmailReset email = pedirRecuperacao(c, 1);
        assertEquals(200, postJson("/auth/redefinir-senha", "{\"token\":\"" + email.token() + "\",\"novaSenha\":\"NovaSenha!9\"}", ipUnico())
                .getResponse().getStatus(), "o fluxo por link continua igual");

        MvcResult r = validarCodigo(c, email.codigo(), ipUnico());

        assertEquals(400, r.getResponse().getStatus());
        assertEquals(MSG_INVALIDO, corpo(r).get("mensagem").asString());
    }

    @Test
    void novoPedido_InvalidaCodigoELinkAnterioresEOMaisRecenteFunciona() throws Exception {
        Conta c = registrar();
        EmailReset primeiro = pedirRecuperacao(c, 1);
        EmailReset segundo = pedirRecuperacao(c, 2);
        assertNotEquals(primeiro.token(), segundo.token());

        assertEquals(400, validarCodigo(c, primeiro.codigo(), ipUnico()).getResponse().getStatus());
        assertEquals(400, postJson("/auth/validar-token", "{\"token\":\"" + primeiro.token() + "\",\"tipo\":\"RESET_SENHA\"}", ipUnico())
                .getResponse().getStatus());
        liberarLimiteDoEmail(c);
        assertEquals(200, validarCodigo(c, segundo.codigo(), ipUnico()).getResponse().getStatus());
    }

    @Test
    void linkContinuaIgual_ValidarTokenERedefinirSemUsarOCodigo() throws Exception {
        Conta c = registrar();
        EmailReset email = pedirRecuperacao(c, 1);

        MvcResult validacao = postJson("/auth/validar-token", "{\"token\":\"" + email.token() + "\",\"tipo\":\"RESET_SENHA\"}", ipUnico());

        assertEquals(200, validacao.getResponse().getStatus());
        assertEquals(c.nome(), corpo(validacao).get("nomeUsuario").asString());
        assertTrue(email.html().contains("https://bokka.com.br/redefinir-senha?token=" + email.token() + "&amp;c=" + c.codigo()));
    }

    // ───────────── erros, bloqueio e expiração ─────────────

    @Test
    void codigoErrado_Retorna400Padronizado() throws Exception {
        Conta c = registrar();
        EmailReset email = pedirRecuperacao(c, 1);

        MvcResult r = validarCodigo(c, errado(email.codigo()), ipUnico());

        assertEquals(400, r.getResponse().getStatus());
        assertEquals(MSG_INVALIDO, corpo(r).get("mensagem").asString());
        assertEquals(1, tokenNoBanco(c).getInteger("codigoTentativas"));
    }

    @Test
    void quintoErroBloqueiaOCodigoMasOLinkContinuaValendo() throws Exception {
        Conta c = registrar();
        EmailReset email = pedirRecuperacao(c, 1);

        for (int i = 1; i <= 5; i++) {
            assertEquals(400, validarCodigo(c, errado(email.codigo()), ipUnico()).getResponse().getStatus());
            assertEquals(i, tokenNoBanco(c).getInteger("codigoTentativas"));
        }
        assertEquals(true, tokenNoBanco(c).getBoolean("codigoBloqueado"));

        liberarLimiteDoEmail(c);
        MvcResult comCodigoCerto = validarCodigo(c, email.codigo(), ipUnico());
        assertEquals(400, comCodigoCerto.getResponse().getStatus(), "o código certo na 6ª tentativa é recusado");
        assertEquals(MSG_INVALIDO, corpo(comCodigoCerto).get("mensagem").asString());
        assertEquals(5, tokenNoBanco(c).getInteger("codigoTentativas"), "depois de bloqueado não conta mais");
        assertEquals(200, postJson("/auth/validar-token", "{\"token\":\"" + email.token() + "\",\"tipo\":\"RESET_SENHA\"}", ipUnico())
                .getResponse().getStatus(), "o link não sofreu tentativas e segue válido");
    }

    @Test
    void quatroErrosNaoBloqueiamEOCodigoCertoAindaFunciona() throws Exception {
        Conta c = registrar();
        EmailReset email = pedirRecuperacao(c, 1);

        for (int i = 0; i < 4; i++) {
            assertEquals(400, validarCodigo(c, errado(email.codigo()), ipUnico()).getResponse().getStatus());
        }
        assertEquals(4, tokenNoBanco(c).getInteger("codigoTentativas"));
        assertNotEquals(true, tokenNoBanco(c).getBoolean("codigoBloqueado"));

        liberarLimiteDoEmail(c);
        assertEquals(200, validarCodigo(c, email.codigo(), ipUnico()).getResponse().getStatus());
    }

    @Test
    void codigoExpirado_ApesarDoLinkAindaValido() throws Exception {
        Conta c = registrar();
        EmailReset email = pedirRecuperacao(c, 1);
        Document doc = tokenNoBanco(c);
        mongo.getCollection("auth_tokens").updateOne(new Document("_id", doc.get("_id")),
                new Document("$set", new Document("codigoExpiraEm", Date.from(Instant.now().minusSeconds(60)))));

        MvcResult r = validarCodigo(c, email.codigo(), ipUnico());

        assertEquals(400, r.getResponse().getStatus());
        assertEquals(MSG_INVALIDO, corpo(r).get("mensagem").asString());
        assertEquals(200, postJson("/auth/validar-token", "{\"token\":\"" + email.token() + "\",\"tipo\":\"RESET_SENHA\"}", ipUnico())
                .getResponse().getStatus(), "o link vale 30 minutos, o código 15");
    }

    @Test
    void codigoVence15MinutosELinkVence30() throws Exception {
        Conta c = registrar();
        pedirRecuperacao(c, 1);

        Document doc = tokenNoBanco(c);
        Instant criado = ((Date) doc.get("criadoEm")).toInstant();
        assertEquals(Duration.ofMinutes(15), Duration.between(criado, ((Date) doc.get("codigoExpiraEm")).toInstant()));
        assertEquals(Duration.ofMinutes(30), Duration.between(criado, ((Date) doc.get("expiraEm")).toInstant()));
    }

    // ───────────── anti-enumeração, tempo e rate limit ─────────────

    @Test
    void todasAsFalhasTemCorpoIdentico_ContaInexistenteClinicaInexistenteCodigoErrado() throws Exception {
        Conta c = registrar();
        EmailReset email = pedirRecuperacao(c, 1);

        MvcResult codigoErrado = validarCodigo(c, errado(email.codigo()), ipUnico());
        MvcResult emailInexistente = postJson("/auth/validar-codigo-recuperacao",
                "{\"email\":\"fantasma-" + unico() + "@teste.com\",\"clinicaCodigo\":\"" + c.codigo() + "\",\"codigo\":\"123456\"}", ipUnico());
        MvcResult clinicaInexistente = postJson("/auth/validar-codigo-recuperacao",
                "{\"email\":\"" + c.email() + "\",\"clinicaCodigo\":\"nao-existe-" + unico() + "\",\"codigo\":\"123456\"}", ipUnico());
        MvcResult semClinicaSemConta = postJson("/auth/validar-codigo-recuperacao",
                "{\"email\":\"fantasma-" + unico() + "@teste.com\",\"codigo\":\"123456\"}", ipUnico());

        for (MvcResult r : List.of(codigoErrado, emailInexistente, clinicaInexistente, semClinicaSemConta)) {
            assertEquals(400, r.getResponse().getStatus());
            JsonNode json = corpo(r);
            assertEquals(MSG_INVALIDO, json.get("mensagem").asString());
            assertEquals("Bad Request", json.get("erro").asString());
            assertEquals(codigoErrado.getResponse().getContentAsString().replaceAll("\"timestamp\":\"[^\"]*\"", ""),
                    r.getResponse().getContentAsString().replaceAll("\"timestamp\":\"[^\"]*\"", ""));
        }
    }

    @Test
    void contaInativa_TemARespostaDeCodigoInvalido() throws Exception {
        Conta c = registrar();
        EmailReset email = pedirRecuperacao(c, 1);
        mongo.getCollection("users").updateOne(new Document("email", c.email()),
                new Document("$set", new Document("ativo", false)));

        MvcResult r = validarCodigo(c, email.codigo(), ipUnico());

        assertEquals(400, r.getResponse().getStatus());
        assertEquals(MSG_INVALIDO, corpo(r).get("mensagem").asString());
    }

    @Test
    void tempoDeRespostaEquivalente_ContaInexistenteVsCodigoErradoDeContaReal() throws Exception {
        Conta c = registrar();
        EmailReset email = pedirRecuperacao(c, 1);
        List<Long> reais = new ArrayList<>();
        List<Long> fantasmas = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            long t0 = System.nanoTime();
            assertEquals(400, validarCodigo(c, errado(email.codigo()), ipUnico()).getResponse().getStatus());
            reais.add((System.nanoTime() - t0) / 1_000_000);
            liberarLimiteDoEmail(c);
            t0 = System.nanoTime();
            assertEquals(400, postJson("/auth/validar-codigo-recuperacao",
                    "{\"email\":\"fantasma-" + unico() + "@teste.com\",\"clinicaCodigo\":\"" + c.codigo() + "\",\"codigo\":\"123456\"}",
                    ipUnico()).getResponse().getStatus());
            fantasmas.add((System.nanoTime() - t0) / 1_000_000);
        }

        long medianaReal = mediana(reais);
        long medianaFantasma = mediana(fantasmas);
        assertTrue(medianaReal >= 240 && medianaFantasma >= 240, "piso de tempo: " + medianaReal + " / " + medianaFantasma);
        assertTrue(Math.abs(medianaReal - medianaFantasma) < 100,
                "tempos devem ser equivalentes: real=" + reais + " fantasma=" + fantasmas);
    }

    private long mediana(List<Long> valores) {
        List<Long> ordenados = new ArrayList<>(valores);
        ordenados.sort(Long::compare);
        return ordenados.get(ordenados.size() / 2);
    }

    @Test
    void rateLimitPorIp_DezRequisicoesEADecimaPrimeiraRecebe429() throws Exception {
        String ip = ipUnico();
        for (int i = 0; i < 10; i++) {
            assertEquals(400, postJson("/auth/validar-codigo-recuperacao",
                    "{\"email\":\"ip-" + unico() + "@teste.com\",\"codigo\":\"123456\"}", ip).getResponse().getStatus());
        }

        MvcResult r = postJson("/auth/validar-codigo-recuperacao",
                "{\"email\":\"ip-" + unico() + "@teste.com\",\"codigo\":\"123456\"}", ip);

        assertEquals(429, r.getResponse().getStatus());
        assertEquals(MSG_LIMITE, corpo(r).get("mensagem").asString());
    }

    @Test
    void rateLimitPorEmail_CincoRequisicoesEASextaRecebe429EvitaForcaBrutaDistribuida() throws Exception {
        String alvo = "alvo-" + unico() + "@teste.com";
        for (int i = 0; i < 5; i++) {
            assertEquals(400, postJson("/auth/validar-codigo-recuperacao",
                    "{\"email\":\"" + alvo + "\",\"codigo\":\"123456\"}", ipUnico()).getResponse().getStatus());
        }

        MvcResult r = postJson("/auth/validar-codigo-recuperacao", "{\"email\":\"" + alvo + "\",\"codigo\":\"123456\"}", ipUnico());

        assertEquals(429, r.getResponse().getStatus());
        assertEquals(MSG_LIMITE, corpo(r).get("mensagem").asString());
    }

    @Test
    void entradaMalFormada_Retorna400DeValidacaoSemTocarNoBanco() throws Exception {
        for (String corpo : new String[]{
                "{\"email\":\"a@b.com\",\"codigo\":\"12345\"}",
                "{\"email\":\"a@b.com\",\"codigo\":\"12345a\"}",
                "{\"email\":\"a@b.com\",\"codigo\":\"123 456\"}",
                "{\"email\":\"a@b.com\"}",
                "{\"email\":\"nao-e-email\",\"codigo\":\"123456\"}",
                "{\"codigo\":\"123456\"}"}) {
            assertEquals(400, postJson("/auth/validar-codigo-recuperacao", corpo, ipUnico()).getResponse().getStatus(), corpo);
        }
    }

    @Test
    void rotaEPublica_SemJwt() throws Exception {
        int status = postJson("/auth/validar-codigo-recuperacao", "{\"email\":\"a@b.com\",\"codigo\":\"123456\"}", ipUnico())
                .getResponse().getStatus();

        assertEquals(400, status, "não é 401/403: a rota está liberada no SecurityConfig");
    }

    // ───────────── nada em claro ─────────────

    @Test
    void codigoNuncaApareceEmClaroNoBancoNemNoLogNemNasRespostas() throws Exception {
        Conta c = registrar();
        EmailReset email = pedirRecuperacao(c, 1);
        String formatado = email.texto().substring(email.texto().indexOf("\n" + email.codigo().substring(0, 3) + " ") + 1,
                email.texto().indexOf("\n" + email.codigo().substring(0, 3) + " ") + 8);

        MvcResult errada = validarCodigo(c, errado(email.codigo()), ipUnico());
        MvcResult certa = validarCodigo(c, email.codigo(), ipUnico());
        String ticket = corpo(certa).get("ticket").asString();
        postJson("/auth/redefinir-senha", "{\"token\":\"" + ticket + "\",\"novaSenha\":\"NovaSenha!9\"}", ipUnico());

        for (Document doc : mongo.getCollection("auth_tokens").find()) {
            for (Object valor : doc.values()) {
                if (valor instanceof String s) {
                    assertNotEquals(email.codigo(), s, "código em claro no banco");
                    assertNotEquals(formatado, s, "código formatado em claro no banco");
                }
            }
        }
        assertFalse(errada.getResponse().getContentAsString().contains(email.codigo()));
        assertFalse(certa.getResponse().getContentAsString().contains(email.codigo()));
        assertSemSegredoNoLog(email.codigo(), formatado, ticket, email.token());
    }

    // ───────────── concorrência ─────────────

    private int tentativasNoBanco(String userId) {
        Document doc = mongo.getCollection("auth_tokens").find(new Document("userId", userId)
                .append("tipo", "RESET_SENHA")).first();
        return doc.getInteger("codigoTentativas");
    }

    private boolean bloqueadoNoBanco(String userId) {
        Document doc = mongo.getCollection("auth_tokens").find(new Document("userId", userId)
                .append("tipo", "RESET_SENHA")).first();
        return Boolean.TRUE.equals(doc.getBoolean("codigoBloqueado"));
    }

    private List<Boolean> dispararEmParalelo(String userId, String codigo, int threads, int porThread) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<List<Boolean>>> futuros = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futuros.add(pool.submit(() -> {
                List<Boolean> acertos = new ArrayList<>();
                largada.await();
                for (int i = 0; i < porThread; i++) {
                    acertos.add(authTokenService.consumirPorCodigo(userId, TipoTokenAuthEnum.RESET_SENHA, codigo).isPresent());
                }
                return acertos;
            }));
        }
        largada.countDown();
        List<Boolean> todos = new ArrayList<>();
        try {
            for (Future<List<Boolean>> f : futuros) {
                todos.addAll(f.get(60, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        return todos;
    }

    @Test
    void duasThreadsErrando_ContamTodasAsTentativasSemLostUpdate() {
        for (int rodada = 0; rodada < 20; rodada++) {
            String userId = "conc-" + unico();
            String codigo = authTokenService.emitirComCodigo(userId, "c1", TipoTokenAuthEnum.RESET_SENHA,
                    Duration.ofMinutes(30), Duration.ofMinutes(15), "127.0.0.1").codigo();
            String errado = errado(codigo);
            try {
                List<Boolean> r = dispararEmParalelo(userId, errado, 2, 2);
                assertTrue(r.stream().noneMatch(b -> b));
            } catch (Exception e) {
                fail(e);
            }
            assertEquals(4, tentativasNoBanco(userId), "rodada " + rodada + ": 2 threads x 2 erros = 4 tentativas");
            assertFalse(bloqueadoNoBanco(userId));
        }
    }

    @Test
    void duasThreadsErrandoAlemDoLimite_ParamExatamenteEmCincoEBloqueiam() throws Exception {
        for (int rodada = 0; rodada < 10; rodada++) {
            String userId = "conc-" + unico();
            String codigo = authTokenService.emitirComCodigo(userId, "c1", TipoTokenAuthEnum.RESET_SENHA,
                    Duration.ofMinutes(30), Duration.ofMinutes(15), "127.0.0.1").codigo();

            dispararEmParalelo(userId, errado(codigo), 2, 10);

            assertEquals(5, tentativasNoBanco(userId), "rodada " + rodada + ": nunca passa de 5 nem fica abaixo");
            assertTrue(bloqueadoNoBanco(userId));
            assertTrue(authTokenService.consumirPorCodigo(userId, TipoTokenAuthEnum.RESET_SENHA, codigo).isEmpty(),
                    "bloqueado: nem o código certo passa");
        }
    }

    @Test
    void duasThreadsComOCodigoCerto_SoUmaPassa() throws Exception {
        for (int rodada = 0; rodada < 10; rodada++) {
            String userId = "conc-" + unico();
            String codigo = authTokenService.emitirComCodigo(userId, "c1", TipoTokenAuthEnum.RESET_SENHA,
                    Duration.ofMinutes(30), Duration.ofMinutes(15), "127.0.0.1").codigo();

            List<Boolean> r = dispararEmParalelo(userId, codigo, 2, 1);

            assertEquals(1, r.stream().filter(b -> b).count(), "rodada " + rodada + ": só uma consome o código");
        }
    }

    /** Pequeno apoio para calcular o hash SHA-256 do token como o serviço faz (método é package-private). */
    private static final class AuthTokenServiceHashes {
        static String hash(String token) throws Exception {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        }
    }
}

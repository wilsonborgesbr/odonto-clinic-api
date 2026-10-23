package com.example.demo.email;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ResendEmailSenderTest {

    private static final String CHAVE_FALSA = "chave-falsa-apenas-para-teste";
    private static final String REMETENTE = "Bokka <notificacoes@bokka.com.br>";
    private static final String RESPONDER = "suporte@bokka.com.br";
    private static final String LOCAL = "http://localhost:5173";
    private static final String PUBLICA = "https://bokka.com.br";

    private final Logger logger = (Logger) LoggerFactory.getLogger(ResendEmailSender.class);
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void capturarLogs() {
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void liberarLogs() {
        logger.detachAppender(logs);
    }

    private String saidaDoLog() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
    }

    @Test
    void semChave_ModoLogImprimeOEmailSemEnviarNemLancarExcecao() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        ResendEmailSender sender = new ResendEmailSender(builder, "", REMETENTE, RESPONDER, LOCAL);

        assertTrue(sender.modoLog());
        assertDoesNotThrow(() -> sender.enviar("maria@x.com", "Assunto de teste", "<p>Olá Maria</p>"));

        servidor.verify();
        String saida = saidaDoLog();
        assertTrue(saida.contains("MODO LOG"));
        assertTrue(saida.contains("maria@x.com"));
        assertTrue(saida.contains("Assunto de teste"));
        assertTrue(saida.contains("<p>Olá Maria</p>"));
    }

    private static final String HTML_COM_TOKEN = "<a href=\"https://bokka.com.br/redefinir-senha?token=SEGREDO123&c=tainah\">Redefinir</a>";

    @Test
    void semChaveEmUrlPublica_NaoImprimeCorpoNemLinkApenasDestinatarioEAssuntoEmWarn() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        ResendEmailSender sender = new ResendEmailSender(builder, "", REMETENTE, RESPONDER, PUBLICA);

        assertDoesNotThrow(() -> sender.enviar("maria@x.com", "Recuperação de senha", HTML_COM_TOKEN));

        servidor.verify();
        List<ILoggingEvent> envio = logs.list.stream().filter(e -> e.getFormattedMessage().contains("maria@x.com")).toList();
        assertEquals(1, envio.size());
        assertEquals(Level.WARN, envio.get(0).getLevel());
        assertEquals("[E-MAIL NÃO ENVIADO - CHAVE DO RESEND AUSENTE] Para: maria@x.com, Assunto: Recuperação de senha",
                envio.get(0).getFormattedMessage());
        String tudo = saidaDoLog();
        assertFalse(tudo.contains("SEGREDO123"));
        assertFalse(tudo.contains("redefinir-senha"));
        assertFalse(tudo.contains("<a href"));
    }

    @Test
    void semChaveEmLocalhost_MantemCorpoCompletoNoLog() {
        for (String local : new String[]{"http://localhost:5173", "http://127.0.0.1:5173", "http://[::1]:5173", "http://LOCALHOST"}) {
            logs.list.clear();
            RestClient.Builder builder = RestClient.builder();
            MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
            ResendEmailSender sender = new ResendEmailSender(builder, "", REMETENTE, RESPONDER, local);

            sender.enviar("maria@x.com", "Assunto", HTML_COM_TOKEN);

            servidor.verify();
            assertTrue(saidaDoLog().contains("SEGREDO123"), local);
            assertTrue(saidaDoLog().contains("MODO LOG"), local);
        }
    }

    @Test
    void urlLocal_SoLocalhostELoopbackContamComoDesenvolvimento() {
        assertTrue(ResendEmailSender.urlLocal("http://localhost:5173"));
        assertTrue(ResendEmailSender.urlLocal("http://127.0.0.1:5173/"));
        assertTrue(ResendEmailSender.urlLocal("http://[::1]:5173"));
        assertFalse(ResendEmailSender.urlLocal("https://bokka.com.br"));
        assertFalse(ResendEmailSender.urlLocal("https://app.bokka.com.br"));
        assertFalse(ResendEmailSender.urlLocal("https://localhost.bokka.com.br"));
        assertFalse(ResendEmailSender.urlLocal("http://192.168.0.10:5173"));
        assertFalse(ResendEmailSender.urlLocal("https://127.0.0.1.evil.com"));
        assertFalse(ResendEmailSender.urlLocal(""), "ilegível = público");
        assertFalse(ResendEmailSender.urlLocal("localhost:5173"), "sem esquema não é URL confiável");
        assertFalse(ResendEmailSender.urlLocal("http://exemplo .com"));
    }

    @Test
    void comChaveEmUrlPublicaEnviaNormalmenteSemLogarOCorpo() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo("https://api.resend.com/emails")).andRespond(withSuccess());
        ResendEmailSender sender = new ResendEmailSender(builder, CHAVE_FALSA, REMETENTE, RESPONDER, PUBLICA);

        sender.enviar("maria@x.com", "Assunto", HTML_COM_TOKEN);

        servidor.verify();
        assertFalse(saidaDoLog().contains("SEGREDO123"));
    }

    @Test
    void chaveNulaOuSoEspacosTambemEModoLog() {
        for (String chave : new String[]{null, "   "}) {
            RestClient.Builder builder = RestClient.builder();
            MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
            ResendEmailSender sender = new ResendEmailSender(builder, chave, REMETENTE, RESPONDER, LOCAL);

            assertTrue(sender.modoLog());
            assertDoesNotThrow(() -> sender.enviar("a@x.com", "s", "<p>x</p>"));
            servidor.verify();
        }
    }

    @Test
    void comChave_EnviaParaOResendComAutorizacaoEPayloadCorretos() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + CHAVE_FALSA))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.from").value(REMETENTE))
                .andExpect(jsonPath("$.to[0]").value("maria@x.com"))
                .andExpect(jsonPath("$.subject").value("Assunto"))
                .andExpect(jsonPath("$.html").value("<p>Oi</p>"))
                .andExpect(jsonPath("$.reply_to").value(RESPONDER))
                .andRespond(withSuccess("{\"id\":\"abc\"}", MediaType.APPLICATION_JSON));
        ResendEmailSender sender = new ResendEmailSender(builder, CHAVE_FALSA, REMETENTE, RESPONDER, LOCAL);

        assertFalse(sender.modoLog());
        sender.enviar("maria@x.com", "Assunto", "<p>Oi</p>");

        servidor.verify();
        assertFalse(saidaDoLog().contains("<p>Oi</p>"), "com chave o corpo do e-mail não deve ir para o log");
    }

    @Test
    void comChave_FalhaDoResendNaoLancaExcecaoENaoVazaAChaveNoLog() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo("https://api.resend.com/emails")).andRespond(withServerError());
        ResendEmailSender sender = new ResendEmailSender(builder, CHAVE_FALSA, REMETENTE, RESPONDER, LOCAL);

        assertDoesNotThrow(() -> sender.enviar("maria@x.com", "Assunto", "<p>Oi</p>"));

        servidor.verify();
        List<ILoggingEvent> erros = logs.list.stream().filter(e -> e.getLevel() == Level.ERROR).toList();
        assertEquals(1, erros.size());
        assertFalse(saidaDoLog().contains(CHAVE_FALSA));
    }

    @Test
    void timeoutDeCincoSegundos() {
        assertEquals(Duration.ofSeconds(5), ResendEmailSender.TIMEOUT);
    }

    @Test
    void construtorDoSpringMontaOClienteComTimeoutSemExigirRede() {
        assertDoesNotThrow(() -> new ResendEmailSender("", REMETENTE, RESPONDER, LOCAL));
    }
}

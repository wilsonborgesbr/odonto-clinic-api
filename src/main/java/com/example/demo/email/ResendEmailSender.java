package com.example.demo.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ResendEmailSender implements EmailService {

    static final String URL_API = "https://api.resend.com/emails";
    static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);

    private final RestClient restClient;
    private final String apiKey;
    private final String remetente;
    private final String responderPara;
    private final boolean ambienteLocal;

    @Autowired
    public ResendEmailSender(
            @Value("${resend.api.key:}") String apiKey,
            @Value("${resend.from-email}") String remetente,
            @Value("${resend.reply-to}") String responderPara,
            @Value("${app.frontend-url}") String frontendUrl) {
        this(RestClient.builder().requestFactory(requestFactory()), apiKey, remetente, responderPara, frontendUrl);
    }

    ResendEmailSender(RestClient.Builder builder, String apiKey, String remetente, String responderPara,
                      String frontendUrl) {
        this.restClient = builder.baseUrl(URL_API).build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.remetente = remetente;
        this.responderPara = responderPara;
        this.ambienteLocal = urlLocal(frontendUrl);
        if (modoLog()) {
            log.warn(ambienteLocal
                    ? "resend.api.key vazia: e-mails NÃO serão enviados, apenas impressos no log (modo log)."
                    : "resend.api.key vazia em ambiente público: e-mails NÃO serão enviados e o conteúdo NÃO é impresso no log.");
        }
    }

    /**
     * Só localhost/127.0.0.1/[::1] contam como desenvolvimento. Qualquer outra URL (ou uma URL ilegível)
     * é tratada como pública, para o link com token nunca ir para o log de produção.
     */
    static boolean urlLocal(String frontendUrl) {
        try {
            String host = URI.create(frontendUrl.trim()).getHost();
            if (host == null) {
                return false;
            }
            String h = host.toLowerCase(Locale.ROOT);
            return h.equals("localhost") || h.equals("127.0.0.1") || h.equals("[::1]") || h.equals("::1");
        } catch (RuntimeException e) {
            return false;
        }
    }

    boolean modoLog() {
        return apiKey.isEmpty();
    }

    @Override
    public void enviar(String destinatario, String assunto, String html) {
        if (modoLog() && !ambienteLocal) {
            log.warn("[E-MAIL NÃO ENVIADO - CHAVE DO RESEND AUSENTE] Para: {}, Assunto: {}", destinatario, assunto);
            return;
        }
        if (modoLog()) {
            log.info("[E-MAIL - MODO LOG, não enviado]\nDe: {}\nResponder para: {}\nPara: {}\nAssunto: {}\n{}",
                    remetente, responderPara, destinatario, assunto, html);
            return;
        }
        try {
            restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(Map.of(
                            "from", remetente,
                            "to", List.of(destinatario),
                            "subject", assunto,
                            "html", html,
                            "reply_to", responderPara))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Falha ao enviar e-mail pelo Resend (assunto \"{}\"): {}", assunto, e.getMessage());
        }
    }

    private static JdkClientHttpRequestFactory requestFactory() {
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        fabrica.setReadTimeout(TIMEOUT);
        return fabrica;
    }
}

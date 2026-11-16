package com.example.demo.googlecalendar;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuração da integração. Os valores padrão são placeholders: servem para subir e testar localmente sem
 * credenciais do Google Cloud Console. Em produção vêm só do ambiente.
 */
@Component
public class GoogleCalendarProperties {

    public static final String PLACEHOLDER = "placeholder";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String frontendUrl;
    private final String caminhoRetornoFrontend;
    private final String webhookUrl;
    private final String chaveCriptografia;
    private final Duration margemRenovacaoToken;
    private final Duration antecedenciaRenovacaoCanal;
    private final Duration duracaoCanal;
    private final int tentativasApi;
    private final Duration esperaEntreTentativas;

    public GoogleCalendarProperties(
            @Value("${google.calendar.client-id:placeholder-client-id}") String clientId,
            @Value("${google.calendar.client-secret:placeholder-client-secret}") String clientSecret,
            @Value("${google.calendar.redirect-uri:http://localhost:8080/api/google-calendar/callback}") String redirectUri,
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${google.calendar.frontend-return-path:/configuracoes}") String caminhoRetornoFrontend,
            @Value("${google.calendar.webhook-url:}") String webhookUrl,
            @Value("${google.calendar.encryption-key:}") String chaveCriptografia,
            @Value("${google.calendar.token-refresh-margin:PT5M}") Duration margemRenovacaoToken,
            @Value("${google.calendar.channel-renew-before:PT48H}") Duration antecedenciaRenovacaoCanal,
            @Value("${google.calendar.channel-duration:P6D}") Duration duracaoCanal,
            @Value("${google.calendar.api-attempts:3}") int tentativasApi,
            @Value("${google.calendar.api-retry-wait:PT1S}") Duration esperaEntreTentativas) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.frontendUrl = frontendUrl;
        this.caminhoRetornoFrontend = caminhoRetornoFrontend;
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.chaveCriptografia = chaveCriptografia == null ? "" : chaveCriptografia.trim();
        this.margemRenovacaoToken = margemRenovacaoToken;
        this.antecedenciaRenovacaoCanal = antecedenciaRenovacaoCanal;
        this.duracaoCanal = duracaoCanal;
        this.tentativasApi = Math.max(1, tentativasApi);
        this.esperaEntreTentativas = esperaEntreTentativas;
    }

    public String clientId() { return clientId; }
    public String clientSecret() { return clientSecret; }
    public String redirectUri() { return redirectUri; }
    public String frontendUrl() { return frontendUrl; }
    public String caminhoRetornoFrontend() { return caminhoRetornoFrontend; }
    public String webhookUrl() { return webhookUrl; }
    public String chaveCriptografia() { return chaveCriptografia; }
    public Duration margemRenovacaoToken() { return margemRenovacaoToken; }
    public Duration antecedenciaRenovacaoCanal() { return antecedenciaRenovacaoCanal; }
    public Duration duracaoCanal() { return duracaoCanal; }
    public int tentativasApi() { return tentativasApi; }
    public Duration esperaEntreTentativas() { return esperaEntreTentativas; }

    /** O Google só entrega notificações em HTTPS público; sem isso o canal (watch) simplesmente não é criado. */
    public boolean webhookConfigurado() {
        return webhookUrl.toLowerCase(java.util.Locale.ROOT).startsWith("https://");
    }

    public boolean credenciaisPlaceholder() {
        return clientId.startsWith(PLACEHOLDER) || clientSecret.startsWith(PLACEHOLDER);
    }
}

package com.example.demo.googlecalendar;

import com.example.demo.util.FusoBrasil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Implementação sobre HTTP puro (sem SDK do Google), no mesmo estilo do envio de e-mail pelo Resend. */
@Service
public class GoogleCalendarRestApi implements GoogleCalendarApi {

    static final String URL_TOKEN = "https://oauth2.googleapis.com/token";
    static final String URL_REVOGAR = "https://oauth2.googleapis.com/revoke";
    static final String URL_USERINFO = "https://openidconnect.googleapis.com/v1/userinfo";
    static final String URL_CALENDAR = "https://www.googleapis.com/calendar/v3";
    static final Duration TIMEOUT = Duration.ofSeconds(10);
    static final int POR_PAGINA = 250;

    private final RestClient http;
    private final GoogleCalendarProperties props;
    private final Clock relogio;

    @Autowired
    public GoogleCalendarRestApi(GoogleCalendarProperties props) {
        this(RestClient.builder().requestFactory(requestFactory()), props, Clock.systemUTC());
    }

    GoogleCalendarRestApi(RestClient.Builder builder, GoogleCalendarProperties props, Clock relogio) {
        this.http = builder.build();
        this.props = props;
        this.relogio = relogio;
    }

    private static JdkClientHttpRequestFactory requestFactory() {
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        fabrica.setReadTimeout(TIMEOUT);
        return fabrica;
    }

    // ───────────── OAuth ─────────────

    @Override
    public Tokens trocarCodigo(String codigo) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", codigo);
        form.add("client_id", props.clientId());
        form.add("client_secret", props.clientSecret());
        form.add("redirect_uri", props.redirectUri());
        form.add("grant_type", "authorization_code");
        return lerTokens(postForm(URL_TOKEN, form));
    }

    @Override
    public Tokens renovar(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("refresh_token", refreshToken);
        form.add("client_id", props.clientId());
        form.add("client_secret", props.clientSecret());
        form.add("grant_type", "refresh_token");
        return lerTokens(postForm(URL_TOKEN, form));
    }

    @Override
    public void revogar(String token) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", token);
        postForm(URL_REVOGAR, form);
    }

    @Override
    public String emailDaConta(String accessToken) {
        JsonNode corpo = executar(() -> http.get().uri(URL_USERINFO)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve().onStatus(s -> s.isError(), this::falhar).body(JsonNode.class));
        return texto(corpo, "email");
    }

    private JsonNode postForm(String url, MultiValueMap<String, String> form) {
        return executar(() -> http.post().uri(url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve().onStatus(s -> s.isError(), this::falhar).body(JsonNode.class));
    }

    private Tokens lerTokens(JsonNode corpo) {
        String access = texto(corpo, "access_token");
        if (access == null) {
            throw new GoogleApiException("Resposta do Google sem access_token.", null);
        }
        long segundos = corpo.hasNonNull("expires_in") ? corpo.get("expires_in").asLong(3600) : 3600;
        return new Tokens(access, texto(corpo, "refresh_token"), relogio.instant().plusSeconds(segundos),
                texto(corpo, "scope"));
    }

    // ───────────── Eventos ─────────────

    @Override
    public void inserirEvento(String accessToken, String calendarId, Evento evento) {
        executar(() -> http.post().uri(URL_CALENDAR + "/calendars/{c}/events", calendarId)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .body(corpoEvento(evento, true))
                .retrieve().onStatus(s -> s.isError(), this::falhar).toBodilessEntity());
    }

    @Override
    public void atualizarEvento(String accessToken, String calendarId, Evento evento) {
        executar(() -> http.put().uri(URL_CALENDAR + "/calendars/{c}/events/{e}", calendarId, evento.id())
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .body(corpoEvento(evento, false))
                .retrieve().onStatus(s -> s.isError(), this::falhar).toBodilessEntity());
    }

    @Override
    public void excluirEvento(String accessToken, String calendarId, String eventId) {
        try {
            executar(() -> http.delete().uri(URL_CALENDAR + "/calendars/{c}/events/{e}", calendarId, eventId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve().onStatus(s -> s.isError(), this::falhar).toBodilessEntity());
        } catch (GoogleApiException e) {
            if (!e.naoEncontrado() && !e.desaparecido()) {
                throw e;
            }
        }
    }

    /** O Google exige RFC 3339 com segundos; {@code LocalDateTime.toString()} omite ":00" e a API responde 400. */
    static String rfc3339(LocalDateTime t) {
        return t.withNano(0).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
    }

    private Map<String, Object> corpoEvento(Evento e, boolean comId) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        if (comId) {
            corpo.put("id", e.id());
        }
        corpo.put("status", "confirmed");
        corpo.put("summary", e.titulo());
        corpo.put("description", e.descricao());
        corpo.put("start", Map.of("dateTime", rfc3339(e.inicio()), "timeZone", FusoBrasil.ZONA.getId()));
        corpo.put("end", Map.of("dateTime", rfc3339(e.fim()), "timeZone", FusoBrasil.ZONA.getId()));
        corpo.put("extendedProperties", Map.of("private", e.propriedadesPrivadas()));
        return corpo;
    }

    @Override
    public PaginaEventos listarEventos(String accessToken, String calendarId, String syncToken, String pagina,
                                       Instant aPartirDe) {
        JsonNode corpo = executar(() -> http.get().uri(uri -> {
                    var u = uri.path("/calendars/{c}/events").queryParam("showDeleted", "true")
                            .queryParam("maxResults", POR_PAGINA);
                    if (syncToken != null) {
                        u.queryParam("syncToken", syncToken);
                    } else if (aPartirDe != null) {
                        u.queryParam("timeMin", aPartirDe.toString());
                    }
                    if (pagina != null) {
                        u.queryParam("pageToken", pagina);
                    }
                    return u.build(calendarId);
                })
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve().onStatus(s -> s.isError(), this::falhar).body(JsonNode.class));

        List<EventoRemoto> eventos = new ArrayList<>();
        JsonNode itens = corpo == null ? null : corpo.get("items");
        if (itens != null && itens.isArray()) {
            for (JsonNode item : itens) {
                eventos.add(lerEvento(item));
            }
        }
        return new PaginaEventos(eventos, texto(corpo, "nextPageToken"), texto(corpo, "nextSyncToken"));
    }

    private static EventoRemoto lerEvento(JsonNode item) {
        Map<String, String> privadas = new LinkedHashMap<>();
        JsonNode props = item.path("extendedProperties").path("private");
        if (props.isObject()) {
            props.properties().forEach(en -> privadas.put(en.getKey(), en.getValue().asString("")));
        }
        return new EventoRemoto(texto(item, "id"), "cancelled".equals(texto(item, "status")),
                horario(item.path("start")), horario(item.path("end")), privadas, texto(item, "summary"));
    }

    /** Só eventos com hora marcada; dia inteiro (campo {@code date}) não tem como virar horário de consulta. */
    static LocalDateTime horario(JsonNode no) {
        String valor = texto(no, "dateTime");
        if (valor == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(valor).atZoneSameInstant(FusoBrasil.ZONA).toLocalDateTime();
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(valor);
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
    }

    // ───────────── Canais (webhook) ─────────────

    @Override
    public Canal criarCanal(String accessToken, String calendarId, String channelId, String endereco,
                            String tokenCanal, Instant expiracao) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("id", channelId);
        corpo.put("type", "web_hook");
        corpo.put("address", endereco);
        corpo.put("token", tokenCanal);
        corpo.put("expiration", expiracao.toEpochMilli());
        JsonNode resposta = executar(() -> http.post().uri(URL_CALENDAR + "/calendars/{c}/events/watch", calendarId)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .body(corpo)
                .retrieve().onStatus(s -> s.isError(), this::falhar).body(JsonNode.class));
        String resourceId = texto(resposta, "resourceId");
        if (resourceId == null) {
            throw new GoogleApiException("Resposta do Google sem resourceId do canal.", null);
        }
        Instant expira = resposta.hasNonNull("expiration")
                ? Instant.ofEpochMilli(Long.parseLong(resposta.get("expiration").asString()))
                : expiracao;
        return new Canal(texto(resposta, "id") == null ? channelId : texto(resposta, "id"), resourceId, expira);
    }

    @Override
    public void pararCanal(String accessToken, String channelId, String resourceId) {
        try {
            executar(() -> http.post().uri(URL_CALENDAR + "/channels/stop")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .body(Map.of("id", channelId, "resourceId", resourceId))
                    .retrieve().onStatus(s -> s.isError(), this::falhar).toBodilessEntity());
        } catch (GoogleApiException e) {
            if (!e.naoEncontrado()) {
                throw e;
            }
        }
    }

    // ───────────── infraestrutura ─────────────

    private interface Chamada<T> {
        T executar();
    }

    private <T> T executar(Chamada<T> chamada) {
        try {
            return chamada.executar();
        } catch (GoogleApiException e) {
            throw e;
        } catch (RestClientException e) {
            throw new GoogleApiException("Falha de comunicação com o Google.", e);
        }
    }

    private void falhar(org.springframework.http.HttpRequest req,
                        org.springframework.http.client.ClientHttpResponse res) throws IOException {
        String motivo = null;
        String detalhe = null;
        try {
            byte[] bytes = res.getBody().readNBytes(16 * 1024);
            if (bytes.length > 0) {
                JsonNode corpoErro = new tools.jackson.databind.json.JsonMapper().readTree(bytes);
                motivo = motivo(corpoErro);
                detalhe = texto(corpoErro.path("error"), "message");
            }
        } catch (RuntimeException ignorado) {
            // corpo ilegível: segue só com o status
        }
        throw new GoogleApiException(res.getStatusCode().value(), motivo, detalhe);
    }

    /** OAuth: {@code {"error":"invalid_grant"}}; Calendar: {@code {"error":{"errors":[{"reason":"..."}]}}}. */
    static String motivo(JsonNode corpo) {
        JsonNode erro = corpo.get("error");
        if (erro == null) {
            return null;
        }
        if (erro.isString()) {
            return erro.asString();
        }
        JsonNode primeiro = erro.path("errors").path(0);
        String razao = texto(primeiro, "reason");
        return razao != null ? razao : texto(erro, "status");
    }

    private static String texto(JsonNode no, String campo) {
        if (no == null) {
            return null;
        }
        JsonNode valor = no.get(campo);
        return valor == null || valor.isNull() ? null : valor.asString();
    }
}

package com.example.demo.googlecalendar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** O cliente HTTP do Google contra um servidor simulado: formato dos pedidos e leitura das respostas. */
class GoogleCalendarRestApiTest {

    private static final Instant AGORA = Instant.parse("2026-11-09T12:00:00Z");

    private MockRestServiceServer servidor;
    private GoogleCalendarRestApi api;

    @BeforeEach
    void montar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        var props = new GoogleCalendarProperties("cid-123", "segredo-456", "https://api.bokka.test/api/google-calendar/callback",
                "https://app.bokka.test", "/configuracoes", "", "", java.time.Duration.ofMinutes(5),
                java.time.Duration.ofHours(48), java.time.Duration.ofDays(6), 3, java.time.Duration.ZERO);
        api = new GoogleCalendarRestApi(builder, props, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    void trocarCodigo_EnviaFormularioEInterpretaOsTokens() {
        servidor.expect(requestTo(GoogleCalendarRestApi.URL_TOKEN))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(containsString("code=abc")))
                .andExpect(content().string(containsString("client_id=cid-123")))
                .andExpect(content().string(containsString("client_secret=segredo-456")))
                .andExpect(content().string(containsString("grant_type=authorization_code")))
                .andExpect(content().string(containsString("redirect_uri=")))
                .andRespond(withSuccess("{\"access_token\":\"acc\",\"refresh_token\":\"ref\",\"expires_in\":3599,"
                        + "\"scope\":\"openid calendar.events\",\"token_type\":\"Bearer\"}", MediaType.APPLICATION_JSON));

        GoogleCalendarApi.Tokens t = api.trocarCodigo("abc");

        assertEquals("acc", t.accessToken());
        assertEquals("ref", t.refreshToken());
        assertEquals(AGORA.plusSeconds(3599), t.expiraEm());
        assertEquals("openid calendar.events", t.scope());
        servidor.verify();
    }

    @Test
    void renovar_SemNovoRefreshToken_DevolveRefreshNulo() {
        servidor.expect(requestTo(GoogleCalendarRestApi.URL_TOKEN))
                .andExpect(content().string(containsString("grant_type=refresh_token")))
                .andExpect(content().string(containsString("refresh_token=ref-antigo")))
                .andRespond(withSuccess("{\"access_token\":\"novo\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));

        GoogleCalendarApi.Tokens t = api.renovar("ref-antigo");

        assertEquals("novo", t.accessToken());
        assertNull(t.refreshToken());
        servidor.verify();
    }

    @Test
    void invalidGrant_VirouExcecaoDeConcessaoInvalida_SemVazarCorpo() {
        servidor.expect(requestTo(GoogleCalendarRestApi.URL_TOKEN))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_grant\",\"error_description\":\"Token has been expired or revoked.\"}"));

        GoogleApiException e = assertThrows(GoogleApiException.class, () -> api.renovar("ref"));

        assertTrue(e.concessaoInvalida());
        assertEquals(400, e.status());
        assertFalse(e.getMessage().contains("revoked"), "a mensagem não deve repassar o corpo do Google");
        assertFalse(e.transitorio());
    }

    @Test
    void erroDoCalendar_UsaOReasonDoPrimeiroErro() {
        servidor.expect(requestTo(GoogleCalendarRestApi.URL_USERINFO))
                .andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":403,\"errors\":[{\"reason\":\"rateLimitExceeded\"}]}}"));

        GoogleApiException e = assertThrows(GoogleApiException.class, () -> api.emailDaConta("acc"));

        assertEquals("rateLimitExceeded", e.motivo());
        assertTrue(e.proibido());
    }

    @Test
    void falhaDeServidorECorpoIlegivel_ContamComoTransitorias() {
        servidor.expect(requestTo(GoogleCalendarRestApi.URL_USERINFO))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_GATEWAY).body("<html>oops</html>"));
        servidor.expect(requestTo(GoogleCalendarRestApi.URL_USERINFO))
                .andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS));

        assertTrue(assertThrows(GoogleApiException.class, () -> api.emailDaConta("acc")).transitorio());
        assertTrue(assertThrows(GoogleApiException.class, () -> api.emailDaConta("acc")).transitorio());
    }

    @Test
    void emailDaConta_UsaBearerELeOCampoEmail() {
        servidor.expect(requestTo(GoogleCalendarRestApi.URL_USERINFO))
                .andExpect(header("Authorization", "Bearer acc-1"))
                .andRespond(withSuccess("{\"sub\":\"1\",\"email\":\"dra@exemplo.com\"}", MediaType.APPLICATION_JSON));

        assertEquals("dra@exemplo.com", api.emailDaConta("acc-1"));
    }

    @Test
    void inserirEvento_Manda_IdFusoPropriedadesPrivadasEStatusConfirmado() {
        Map<String, String> privadas = new LinkedHashMap<>();
        privadas.put("bokkaAgendamentoId", "ag-1");
        servidor.expect(requestTo(GoogleCalendarRestApi.URL_CALENDAR + "/calendars/primary/events"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tok"))
                .andExpect(jsonPath("$.id").value("bkabc"))
                .andExpect(jsonPath("$.status").value("confirmed"))
                .andExpect(jsonPath("$.summary").value("Maria Souza — Profilaxia"))
                .andExpect(jsonPath("$.start.dateTime").value("2030-01-10T14:00:00"))
                .andExpect(jsonPath("$.start.timeZone").value("America/Sao_Paulo"))
                .andExpect(jsonPath("$.end.dateTime").value("2030-01-10T14:30:00"))
                .andExpect(jsonPath("$.extendedProperties.private.bokkaAgendamentoId").value("ag-1"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        api.inserirEvento("tok", "primary", new GoogleCalendarApi.Evento("bkabc", "Maria Souza — Profilaxia", "desc",
                LocalDateTime.of(2030, 1, 10, 14, 0), LocalDateTime.of(2030, 1, 10, 14, 30), privadas));

        servidor.verify();
    }

    @Test
    void atualizarEvento_UsaPutNoIdDoEvento_SemReenviarOId() {
        servidor.expect(requestTo(GoogleCalendarRestApi.URL_CALENDAR + "/calendars/primary/events/bkabc"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.status").value("confirmed"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        api.atualizarEvento("tok", "primary", new GoogleCalendarApi.Evento("bkabc", "t", "d",
                LocalDateTime.of(2030, 1, 10, 14, 0), LocalDateTime.of(2030, 1, 10, 14, 30), Map.of("a", "b")));

        servidor.verify();
    }

    @Test
    void excluirEvento_404E410ContamComoSucesso_OutrosErrosPropagam() {
        String url = GoogleCalendarRestApi.URL_CALENDAR + "/calendars/primary/events/bkabc";
        servidor.expect(requestTo(url)).andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NO_CONTENT));
        servidor.expect(requestTo(url)).andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));
        servidor.expect(requestTo(url)).andRespond(withStatus(org.springframework.http.HttpStatus.GONE));
        servidor.expect(requestTo(url)).andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN));

        api.excluirEvento("tok", "primary", "bkabc");
        api.excluirEvento("tok", "primary", "bkabc");
        api.excluirEvento("tok", "primary", "bkabc");
        assertThrows(GoogleApiException.class, () -> api.excluirEvento("tok", "primary", "bkabc"));
        servidor.verify();
    }

    @Test
    void listarEventos_CargaInicial_UsaTimeMinESemSyncToken_ELeProximoToken() {
        servidor.expect(requestTo(org.hamcrest.Matchers.allOf(startsWith(GoogleCalendarRestApi.URL_CALENDAR + "/calendars/primary/events?"),
                        containsString("timeMin=2026-11-09T12:00:00Z"), containsString("showDeleted=true"),
                        not(containsString("syncToken")))))
                .andRespond(withSuccess("{\"items\":[],\"nextSyncToken\":\"tok-1\"}", MediaType.APPLICATION_JSON));

        var pagina = api.listarEventos("tok", "primary", null, null, AGORA);

        assertEquals("tok-1", pagina.proximoSyncToken());
        assertNull(pagina.proximaPagina());
    }

    @Test
    void listarEventos_Incremental_UsaSyncTokenENaoTimeMin() {
        servidor.expect(requestTo(org.hamcrest.Matchers.allOf(startsWith(GoogleCalendarRestApi.URL_CALENDAR + "/calendars/primary/events?"), containsString("syncToken=tok-1"), not(containsString("timeMin")))))
                .andRespond(withSuccess("{\"items\":[],\"nextSyncToken\":\"tok-2\"}", MediaType.APPLICATION_JSON));

        assertEquals("tok-2", api.listarEventos("tok", "primary", "tok-1", null, AGORA).proximoSyncToken());
    }

    @Test
    void listarEventos_InterpretaNormalCanceladoDiaInteiroEOffset() {
        servidor.expect(requestTo(startsWith(GoogleCalendarRestApi.URL_CALENDAR + "/calendars/primary/events?")))
                .andRespond(withSuccess("""
                        {"items":[
                          {"id":"e1","status":"confirmed","summary":"Maria — Limpeza",
                           "start":{"dateTime":"2030-01-10T14:00:00-03:00"},"end":{"dateTime":"2030-01-10T14:30:00-03:00"},
                           "extendedProperties":{"private":{"bokkaAgendamentoId":"ag-1"}}},
                          {"id":"e2","status":"cancelled"},
                          {"id":"e3","status":"confirmed","start":{"date":"2030-01-11"},"end":{"date":"2030-01-12"}},
                          {"id":"e4","status":"confirmed","start":{"dateTime":"2030-01-10T17:00:00Z"},"end":{"dateTime":"2030-01-10T17:45:00Z"}}
                        ],"nextPageToken":"p2"}
                        """, MediaType.APPLICATION_JSON));

        var pagina = api.listarEventos("tok", "primary", "tok-1", null, null);

        assertEquals("p2", pagina.proximaPagina());
        assertEquals(4, pagina.eventos().size());
        var e1 = pagina.eventos().get(0);
        assertFalse(e1.cancelado());
        assertEquals(LocalDateTime.of(2030, 1, 10, 14, 0), e1.inicio());
        assertEquals("ag-1", e1.propriedadesPrivadas().get("bokkaAgendamentoId"));
        var e2 = pagina.eventos().get(1);
        assertTrue(e2.cancelado());
        assertNull(e2.inicio());
        assertTrue(e2.propriedadesPrivadas().isEmpty());
        var e3 = pagina.eventos().get(2);
        assertNull(e3.inicio(), "dia inteiro não vira horário de consulta");
        var e4 = pagina.eventos().get(3);
        assertEquals(LocalDateTime.of(2030, 1, 10, 14, 0), e4.inicio(), "UTC é convertido para horário de São Paulo");
    }

    @Test
    void listarEventos_PaginaSeguinte_UsaUrlAbsolutaEPageToken() {
        servidor.expect(requestTo(org.hamcrest.Matchers.allOf(
                        startsWith("https://www.googleapis.com/calendar/v3/calendars/a%40b.com/events?"),
                        containsString("pageToken=p2"), containsString("syncToken=tok-1"))))
                .andExpect(header("Authorization", "Bearer tok"))
                .andRespond(withSuccess("{\"items\":[],\"nextSyncToken\":\"tok-3\"}", MediaType.APPLICATION_JSON));

        assertEquals("tok-3", api.listarEventos("tok", "a@b.com", "tok-1", "p2", null).proximoSyncToken());
        servidor.verify();
    }

    @Test
    void listarEventos_Token410_ViraExcecaoDesaparecido() {
        servidor.expect(requestTo(startsWith(GoogleCalendarRestApi.URL_CALENDAR + "/calendars/primary/events?")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.GONE));

        assertTrue(assertThrows(GoogleApiException.class, () -> api.listarEventos("tok", "primary", "velho", null, null)).desaparecido());
    }

    @Test
    void criarCanal_EnviaEnderecoTokenEExpiracao_ELeOResourceId() {
        Instant expira = AGORA.plusSeconds(86400 * 6);
        servidor.expect(requestTo(GoogleCalendarRestApi.URL_CALENDAR + "/calendars/primary/events/watch"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.id").value("canal-1"))
                .andExpect(jsonPath("$.type").value("web_hook"))
                .andExpect(jsonPath("$.address").value("https://api.bokka.test/hook"))
                .andExpect(jsonPath("$.token").value("segredo-do-canal"))
                .andExpect(jsonPath("$.expiration").value(expira.toEpochMilli()))
                .andRespond(withSuccess("{\"id\":\"canal-1\",\"resourceId\":\"res-9\",\"expiration\":\"1893456000000\"}",
                        MediaType.APPLICATION_JSON));

        var canal = api.criarCanal("tok", "primary", "canal-1", "https://api.bokka.test/hook", "segredo-do-canal", expira);

        assertEquals("canal-1", canal.channelId());
        assertEquals("res-9", canal.resourceId());
        assertEquals(Instant.ofEpochMilli(1893456000000L), canal.expiracao());
    }

    @Test
    void pararCanal_EnviaIdEResourceId_E404ContaComoSucesso() {
        servidor.expect(requestTo(GoogleCalendarRestApi.URL_CALENDAR + "/channels/stop"))
                .andExpect(jsonPath("$.id").value("canal-1"))
                .andExpect(jsonPath("$.resourceId").value("res-9"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NO_CONTENT));
        servidor.expect(requestTo(GoogleCalendarRestApi.URL_CALENDAR + "/channels/stop"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        api.pararCanal("tok", "canal-1", "res-9");
        api.pararCanal("tok", "canal-1", "res-9");
        servidor.verify();
    }

    @Test
    void revogar_EnviaOTokenPorFormulario() {
        servidor.expect(requestTo(GoogleCalendarRestApi.URL_REVOGAR))
                .andExpect(content().string(containsString("token=ref-1")))
                .andRespond(withSuccess());

        api.revogar("ref-1");
        servidor.verify();
    }
}

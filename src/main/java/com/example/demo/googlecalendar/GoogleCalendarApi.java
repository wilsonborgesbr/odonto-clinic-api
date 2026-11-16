package com.example.demo.googlecalendar;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Fronteira com o Google (OAuth 2.0 + Calendar API v3). Tudo que sai do processo passa por aqui, então os
 * testes trocam esta interface por um duplo e nunca falam com o Google de verdade.
 */
public interface GoogleCalendarApi {

    record Tokens(String accessToken, String refreshToken, Instant expiraEm, String scope) {}

    /** Evento no formato do Bokka; o horário é de parede de São Paulo, sem conversão. */
    record Evento(String id, String titulo, String descricao, LocalDateTime inicio, LocalDateTime fim,
                  Map<String, String> propriedadesPrivadas) {}

    /** Evento lido do Google. {@code cancelado} vem sem os demais campos (o Google só manda o id). */
    record EventoRemoto(String id, boolean cancelado, LocalDateTime inicio, LocalDateTime fim,
                        Map<String, String> propriedadesPrivadas, String titulo) {}

    record PaginaEventos(List<EventoRemoto> eventos, String proximaPagina, String proximoSyncToken) {}

    record Canal(String channelId, String resourceId, Instant expiracao) {}

    Tokens trocarCodigo(String codigo);

    /** O Google costuma não devolver novo refresh token; nesse caso {@code refreshToken} vem nulo. */
    Tokens renovar(String refreshToken);

    /** Revoga o token (access ou refresh) no Google. */
    void revogar(String token);

    /** E-mail da conta Google dona do token. */
    String emailDaConta(String accessToken);

    void inserirEvento(String accessToken, String calendarId, Evento evento);

    void atualizarEvento(String accessToken, String calendarId, Evento evento);

    /** 404 e 410 (já apagado) contam como sucesso. */
    void excluirEvento(String accessToken, String calendarId, String eventId);

    /**
     * Lista alterações. Sem {@code syncToken}: carga inicial a partir de {@code aPartirDe} (só para obter o
     * token). Com {@code syncToken}: só o que mudou. Lança 410 quando o token venceu.
     */
    PaginaEventos listarEventos(String accessToken, String calendarId, String syncToken, String pagina,
                                Instant aPartirDe);

    Canal criarCanal(String accessToken, String calendarId, String channelId, String endereco, String tokenCanal,
                     Instant expiracao);

    /** 404 (canal já inexistente) conta como sucesso. */
    void pararCanal(String accessToken, String channelId, String resourceId);
}

package com.example.demo.dto;

import com.example.demo.enums.SyncStatusGoogleEnum;
import com.example.demo.enums.EscopoGoogleCalendarEnum;
import com.example.demo.model.GoogleCalendarConexao;

import java.time.Instant;
import java.time.LocalDateTime;

/** Contratos da integração com o Google Calendar. Tokens e segredos nunca aparecem em nenhum deles. */
public final class GoogleCalendarDTOs {

    private GoogleCalendarDTOs() {}

    /** Depois disso uma sincronização manual sem término (queda do servidor) deixa de contar como em andamento. */
    public static final java.time.Duration JANELA_MANUAL = java.time.Duration.ofMinutes(10);

    public record AuthUrlResponse(String url, EscopoGoogleCalendarEnum escopo, Instant expiraEm) {}

    public record ConexaoResponse(String id, String userId, String userNome, EscopoGoogleCalendarEnum escopo,
                                  String googleEmail, String googleCalendarId, String dentistaId,
                                  SyncStatusGoogleEnum syncStatus, boolean webhookAtivo, Instant webhookExpiracao,
                                  LocalDateTime criadaEm, LocalDateTime atualizadaEm,
                                  LocalDateTime ultimaSincronizacaoEm, boolean sincronizando,
                                  boolean ultimaSincronizacaoFalhou) {

        public static ConexaoResponse de(GoogleCalendarConexao c, String userNome) {
            var watch = c.getWebhookWatch();
            return new ConexaoResponse(c.getId(), c.getUserId(), userNome, c.getEscopo(), c.getGoogleEmail(),
                    c.getGoogleCalendarId(), c.getDentistaId(), c.getSyncStatus(),
                    watch != null && watch.getChannelId() != null,
                    watch == null ? null : watch.getExpiration(), c.getCreatedAt(), c.getUpdatedAt(),
                    c.getUltimaSincronizacaoEm(),
                    c.getSincronizandoDesde() != null && c.getSincronizandoDesde().isAfter(LocalDateTime.now().minus(JANELA_MANUAL)),
                    Boolean.TRUE.equals(c.getUltimaSincronizacaoFalhou()));
        }
    }

    /**
     * @param permitido     o usuário logado tem a permissão INTEGRACAO_GOOGLE_CALENDAR
     * @param configurada   a aplicação tem credenciais reais do Google (false = placeholders de desenvolvimento)
     * @param conectado     existe conexão do usuário (em qualquer status; veja {@code conexao.syncStatus})
     * @param escopo        o que cai na agenda deste usuário, definido pelo proprietário/sócio (padrão SOMENTE_PROPRIO)
     * @param podeConectar  o usuário pode iniciar a conexão agora (tem permissão, atende ao escopo e não tem conexão ativa)
     * @param motivoBloqueio quando {@code podeConectar} é false por falta de pré-requisito: SEM_PERMISSAO, SEM_DENTISTA_VINCULADO, DENTISTA_INATIVO ou JA_CONECTADO
     */
    public record StatusResponse(boolean permitido, boolean configurada, boolean conectado, ConexaoResponse conexao,
                                 EscopoGoogleCalendarEnum escopo, boolean podeConectar, String motivoBloqueio) {}

    public record DesconectarResponse(String mensagem) {}

    /**
     * Resposta de {@code POST /api/google-calendar/sincronizar} (202). O frontend consulta {@code GET /status} até
     * {@code conexao.sincronizando} voltar a false e então lê {@code ultimaSincronizacaoEm} e {@code ultimaSincronizacaoFalhou}.
     *
     * @param jaEmAndamento já havia uma sincronização manual rodando; nada novo foi iniciado
     */
    public record SincronizarResponse(boolean iniciada, boolean jaEmAndamento, LocalDateTime iniciadaEm) {}
}

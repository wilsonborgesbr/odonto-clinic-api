package com.example.demo.model;

import com.example.demo.enums.SyncStatusGoogleEnum;
import com.example.demo.enums.EscopoGoogleCalendarEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Conexão de um usuário (ou da clínica) com uma agenda do Google. Os tokens ficam cifrados (AES-256-GCM);
 * a chave vem do ambiente, nunca do banco. Nada aqui é devolvido ao frontend sem passar pelo DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "google_calendar_conexoes")
// Um usuário tem no máximo uma conexão por clínica.
@CompoundIndex(name = "clinica_usuario_unique", def = "{'clinicaId': 1, 'userId': 1}", unique = true)
public class GoogleCalendarConexao {

    @Id
    private String id;

    @Indexed
    private String clinicaId;

    private String userId;

    /** Dentista vinculado ao usuário dono da conexão; só os agendamentos dele vão para o Google. */
    @Indexed
    private String dentistaId;

    private EscopoGoogleCalendarEnum escopo;

    private String googleEmail;

    /** "primary" é a agenda principal da conta Google conectada. */
    private String googleCalendarId;

    private Tokens tokens;

    private SyncStatusGoogleEnum syncStatus;

    /** Canal de notificação (webhook) ativo; ausente enquanto não há URL pública configurada. */
    private WebhookWatch webhookWatch;

    /** Ponto de partida da sincronização incremental Google → Bokka. */
    private String syncToken;

    /** Última vez que a sincronização (automática ou manual) terminou sem falhas. */
    private LocalDateTime ultimaSincronizacaoEm;

    /** A última sincronização manual falhou (ou foi interrompida); limpo quando uma sincronização conclui. */
    private Boolean ultimaSincronizacaoFalhou;

    /** Preenchido enquanto uma sincronização manual roda; vence sozinho depois de 10 minutos. */
    private LocalDateTime sincronizandoDesde;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Tokens {
        private String accessTokenEncrypted;
        private String refreshTokenEncrypted;
        private Instant expiresAt;
        private String scope;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WebhookWatch {
        private String channelId;
        private String resourceId;
        private Instant expiration;
    }
}

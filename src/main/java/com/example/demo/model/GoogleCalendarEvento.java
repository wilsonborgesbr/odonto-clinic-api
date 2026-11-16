package com.example.demo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Elo entre um agendamento do Bokka e o evento criado por ele numa conexão. Serve para atualizar/excluir o
 * evento certo, para achar o agendamento quando o Google avisa que um evento foi apagado (nesse caso o evento
 * vem sem propriedades) e para reconhecer o eco das nossas próprias gravações.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "google_calendar_eventos")
@CompoundIndexes({
        @CompoundIndex(name = "conexao_agendamento_unique", def = "{'conexaoId': 1, 'agendamentoId': 1}", unique = true),
        @CompoundIndex(name = "conexao_evento", def = "{'conexaoId': 1, 'googleEventId': 1}"),
        @CompoundIndex(name = "clinica_agendamento", def = "{'clinicaId': 1, 'agendamentoId': 1}")
})
public class GoogleCalendarEvento {

    @Id
    private String id;

    private String clinicaId;

    private String conexaoId;

    private String agendamentoId;

    private String googleEventId;

    /** "inicio|fim" do último envio. Um evento que volta com esse horário é eco nosso, não edição do usuário. */
    private String ultimoHorarioEnviado;

    /** Resumo do conteúdo do último envio (título, descrição, horário): igual ao atual = nada a enviar. */
    private String ultimoConteudoEnviado;

    /**
     * Marcado antes de o Bokka apagar o evento no Google. O aviso de "evento cancelado" que volta é nosso,
     * não uma ação do usuário, e não pode cancelar o agendamento.
     */
    private Boolean removendo;

    private LocalDateTime atualizadoEm;
}

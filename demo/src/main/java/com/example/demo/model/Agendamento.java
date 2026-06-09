package com.example.demo.model;

import com.example.demo.enums.StatusAgendamentoEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "agendamentos")
public class Agendamento {

    @Id
    private String id;

    @Indexed
    private String pacienteId;

    @Indexed
    private String dentistaId;

    private String procedimentoId;

    private LocalDateTime dataHoraInicio;

    private LocalDateTime dataHoraFim;

    private StatusAgendamentoEnum status;

    private String observacoes;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

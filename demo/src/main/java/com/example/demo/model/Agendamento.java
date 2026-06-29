package com.example.demo.model;

import com.example.demo.enums.StatusAgendamentoEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "agendamentos")
public class Agendamento {

    @Id
    private String id;

    @NotBlank(message = "ID do paciente é obrigatório")
    @Indexed
    private String pacienteId;

    @NotBlank(message = "ID do dentista é obrigatório")
    @Indexed
    private String dentistaId;

    private String procedimentoId;

    @NotNull(message = "Data e hora de início é obrigatória")
    private LocalDateTime dataHoraInicio;

    @NotNull(message = "Data e hora de fim é obrigatória")
    private LocalDateTime dataHoraFim;

    @NotNull(message = "Status do agendamento é obrigatório")
    private StatusAgendamentoEnum status;

    private String observacoes;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

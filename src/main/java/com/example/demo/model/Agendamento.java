package com.example.demo.model;

import com.example.demo.enums.NomeProcedimentoEnum;
import com.example.demo.enums.StatusAgendamentoEnum;
import com.example.demo.enums.TipoAgendamentoEnum;
import com.example.demo.enums.TipoPagamentoProcedimentoEnum;
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

    /** Tenant — todos os dados são filtrados por este id em cada query. */
    @Indexed
    private String clinicaId;

    @NotBlank(message = "ID do paciente é obrigatório")
    @Indexed
    private String pacienteId;

    @NotBlank(message = "ID do dentista é obrigatório")
    @Indexed
    private String dentistaId;

    private String procedimentoId;

    private TipoAgendamentoEnum tipoAgendamento;

    private NomeProcedimentoEnum nomeProcedimento;

    // Campos financeiros usados quando tipoAgendamento = PROCEDIMENTO e paciente é particular.
    // São repassados ao Procedimento auto-criado.
    private Double valor;

    private TipoPagamentoProcedimentoEnum tipoPagamento;

    private Integer numeroParcelas;

    private java.time.LocalDate dataPrimeiroPagamento;

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

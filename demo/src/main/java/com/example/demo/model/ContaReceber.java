package com.example.demo.model;

import com.example.demo.enums.FormaPagamentoEnum;
import com.example.demo.enums.StatusFinanceiroEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "contas_receber")
public class ContaReceber {

    @Id
    private String id;

    @NotBlank(message = "ID do paciente é obrigatório")
    @Indexed
    private String pacienteId;

    private String procedimentoId; // Opcional

    @NotBlank(message = "Descrição é obrigatória")
    private String descricao;

    @NotNull(message = "Valor total é obrigatório")
    private Double valorTotal;

    @Builder.Default
    private Double valorPago = 0.0;

    @NotNull(message = "Forma de pagamento é obrigatória")
    private FormaPagamentoEnum formaPagamento;

    @Builder.Default
    private Integer numeroParcelas = 1;

    @NotNull(message = "Data de vencimento é obrigatória")
    private LocalDate dataVencimento;

    private LocalDate dataPagamento; // Preenchido automaticamente ao quitar

    @NotNull(message = "Status é obrigatório")
    @Builder.Default
    private StatusFinanceiroEnum status = StatusFinanceiroEnum.PENDENTE;

    private String observacoes;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

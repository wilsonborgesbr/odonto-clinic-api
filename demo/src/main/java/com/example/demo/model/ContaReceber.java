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

    @Indexed
    private String pacienteId;

    private String procedimentoId; // Opcional

    private String descricao;

    private Double valorTotal;

    @Builder.Default
    private Double valorPago = 0.0;

    private FormaPagamentoEnum formaPagamento;

    @Builder.Default
    private Integer numeroParcelas = 1;

    private LocalDate dataVencimento;

    private LocalDate dataPagamento; // Preenchido automaticamente ao quitar

    @Builder.Default
    private StatusFinanceiroEnum status = StatusFinanceiroEnum.PENDENTE;

    private String observacoes;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

package com.example.demo.model;

import com.example.demo.enums.CategoriaContaPagarEnum;
import com.example.demo.enums.StatusFinanceiroEnum;
import com.example.demo.enums.TipoPagamentoProcedimentoEnum;
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
@Document(collection = "contas_pagar")
public class ContaPagar {

    @Id
    private String id;

    @Indexed
    private String clinicaId;

    @NotBlank(message = "Descrição é obrigatória")
    private String descricao;

    @NotNull(message = "Categoria é obrigatória")
    private CategoriaContaPagarEnum categoria;

    private String fornecedor;

    @NotNull(message = "Valor é obrigatório")
    private Double valor;

    private TipoPagamentoProcedimentoEnum tipoPagamento;

    private Integer numeroParcelas;

    private Integer parcelaAtual;

    @NotNull(message = "Data de vencimento é obrigatória")
    private LocalDate dataVencimento;

    private LocalDate dataPagamento;

    @NotNull(message = "Status é obrigatório")
    @Builder.Default
    private StatusFinanceiroEnum status = StatusFinanceiroEnum.PENDENTE;

    private String observacoes;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

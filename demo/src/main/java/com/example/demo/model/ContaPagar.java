package com.example.demo.model;

import com.example.demo.enums.CategoriaContaPagarEnum;
import com.example.demo.enums.StatusFinanceiroEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
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

    @NotBlank(message = "Descrição é obrigatória")
    private String descricao;

    @NotNull(message = "Categoria é obrigatória")
    private CategoriaContaPagarEnum categoria;

    private String fornecedor;

    @NotNull(message = "Valor é obrigatório")
    private Double valor;

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

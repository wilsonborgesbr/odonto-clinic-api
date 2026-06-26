package com.example.demo.model;

import com.example.demo.enums.CategoriaContaPagarEnum;
import com.example.demo.enums.StatusFinanceiroEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

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

    private String descricao;

    private CategoriaContaPagarEnum categoria;

    private String fornecedor;

    private Double valor;

    private LocalDate dataVencimento;

    private LocalDate dataPagamento;

    @Builder.Default
    private StatusFinanceiroEnum status = StatusFinanceiroEnum.PENDENTE;

    private String observacoes;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

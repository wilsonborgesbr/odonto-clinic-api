package com.example.demo.model;

import com.example.demo.enums.CategoriaEstoqueEnum;
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
@Document(collection = "estoque")
public class Estoque {

    @Id
    private String id;

    @Indexed
    private String clinicaId;

    private String nomeMaterial;
    private CategoriaEstoqueEnum categoria;
    private Integer quantidadeAtual;
    private Integer quantidadeMinima;
    private String unidadeMedida;
    private String fornecedor;
    private LocalDate dataValidade;
    private Double valorCompra;
    private String observacoes;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

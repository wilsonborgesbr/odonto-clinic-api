package com.example.demo.model;

import com.example.demo.enums.NomeProcedimentoEnum;
import com.example.demo.enums.RegiaoEnum;
import com.example.demo.enums.StatusProcedimentoEnum;
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
@Document(collection = "procedimentos")
public class Procedimento {

    @Id
    private String id;

    @Indexed
    private String pacienteId;

    private NomeProcedimentoEnum nomeProcedimento;
    private String descricao;
    private String dente;
    private RegiaoEnum regiao;
    private StatusProcedimentoEnum status;
    private LocalDate dataRealizacao;
    private Double valor;
    private Integer numeroDeSessoes;
    private Integer sessaoAtual;
    private String observacoesTecnicas;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.example.demo.model;

import com.example.demo.enums.NomeProcedimentoEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "convenios")
@CompoundIndex(
        name = "clinica_cnpj_unique",
        def = "{'clinicaId': 1, 'cnpj': 1}",
        unique = true,
        partialFilter = "{'cnpj': {$type: 'string', $gt: ''}}"
)
public class Convenio {

    @Id
    private String id;

    @Indexed
    private String clinicaId;

    private String nome;

    private String cnpj;

    private String registroANS;
    private String telefone;
    private String email;
    private String website;
    private String nomeContato;
    private Endereco endereco;
    private String tabelaDePrecos;
    private List<NomeProcedimentoEnum> coberturas;
    private Double percentualCobertura;
    private Integer carenciaDias;
    private String dataInicioContrato;
    private String dataFimContrato;
    private Integer limiteConsultasMes;
    private String observacoes;

    @Builder.Default
    private Boolean ativo = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.example.demo.model;

import com.example.demo.enums.CondicaoDenteEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DenteStatus {

    private String numeroDente;

    private CondicaoDenteEnum condicao;

    private String observacao;
}

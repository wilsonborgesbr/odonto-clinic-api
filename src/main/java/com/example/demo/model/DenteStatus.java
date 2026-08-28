package com.example.demo.model;

import com.example.demo.enums.CondicaoDenteEnum;
import com.example.demo.enums.FaceDenteEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DenteStatus {

    private String numeroDente;

    private CondicaoDenteEnum condicao;

    private List<FaceDenteEnum> faces;

    private String observacao;
}

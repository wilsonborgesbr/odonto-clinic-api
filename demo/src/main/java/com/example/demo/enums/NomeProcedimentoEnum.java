package com.example.demo.enums;

public enum NomeProcedimentoEnum {
    RESTAURACAO_RESINA("Restauração em Resina"),
    RESTAURACAO_AMALGAMA("Restauração em Amálgama"),
    INLAY("Inlay"),
    ONLAY("Onlay"),
    FACETA_PORCELANA("Faceta de Porcelana"),
    FACETA_RESINA("Faceta de Resina"),
    TRATAMENTO_CANAL_UNIRRADICULAR("Tratamento de Canal (Uni)"),
    TRATAMENTO_CANAL_BIRRADICULAR("Tratamento de Canal (Bi)"),
    TRATAMENTO_CANAL_MULTIRRADICULAR("Tratamento de Canal (Multi)"),
    RETRATAMENTO_CANAL("Retratamento de Canal"),
    PROFILAXIA("Profilaxia"),
    RASPAGEM_SUPRAGENGIVAL("Raspagem Supragengival"),
    RASPAGEM_SUBGENGIVAL("Raspagem Subgengival"),
    GENGIVECTOMIA("Gengivectomia"),
    ENXERTO_GENGIVAL("Enxerto Gengival"),
    EXTRACAO_SIMPLES("Extração Simples"),
    EXTRACAO_DENTE_SISO("Extração de Siso"),
    CIRURGIA_PERIODONTAL("Cirurgia Periodontal"),
    FRENECTOMIA("Frenectomia"),
    BIOPSIA("Biópsia"),
    INSTALACAO_IMPLANTE("Instalação de Implante"),
    INSTALACAO_PROTESE_SOBRE_IMPLANTE("Prótese sobre Implante"),
    ENXERTO_OSSEO("Enxerto Ósseo"),
    PROTESE_PARCIAL_REMOVIVEL("Prótese Parcial Removível"),
    PROTESE_TOTAL("Prótese Total"),
    COROA_PORCELANA("Coroa de Porcelana"),
    COROA_METALICA("Coroa Metálica"),
    PONTE_FIXA("Ponte Fixa"),
    APARELHO_METALICO("Aparelho Metálico"),
    APARELHO_ESTETICO("Aparelho Estético"),
    APARELHO_INVISIVEL("Aparelho Invisível"),
    MANUTENCAO_ORTODONTICA("Manutenção Ortodôntica"),
    CONTENCAO("Contenção"),
    SELANTE("Selante"),
    FLUORTERAPIA("Fluorterapia"),
    COROA_PEDIATRICA("Coroa Pediátrica"),
    PULPOTOMIA("Pulpotomia"),
    CLAREAMENTO_CASEIRO("Clareamento Caseiro"),
    CLAREAMENTO_CONSULTORIO("Clareamento em Consultório"),
    MICROABRASAO("Microabrasão"),
    RADIOGRAFIA_PERIAPICAL("Radiografia Periapical"),
    RADIOGRAFIA_PANORAMICA("Radiografia Panorâmica"),
    TOMOGRAFIA("Tomografia");

    private final String label;

    NomeProcedimentoEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

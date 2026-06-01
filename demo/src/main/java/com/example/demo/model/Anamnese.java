package com.example.demo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "anamneses")
public class Anamnese {

    @Id
    private String id;

    @Indexed
    private String pacienteId;

    private String dentistaId;

    private LocalDate dataPreenchimento;

    private String queixaPrincipal;

    private String historicoDental;

    private Boolean usaMedicamentos;

    private String quaisMedicamentos;

    private Boolean temAlergia;

    private String quaisAlergias;

    private String doencasPreexistentes;

    private Boolean gestante;

    private Boolean fumante;

    private Boolean consumoAlcool;

    private String historiaFamiliar;

    private String observacoes;

    private LocalDateTime createdAt;
}

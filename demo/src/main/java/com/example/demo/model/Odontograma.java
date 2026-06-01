package com.example.demo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "odontogramas")
public class Odontograma {

    @Id
    private String id;

    @Indexed
    private String pacienteId;

    private String dentistaId;

    private List<DenteStatus> dentes;

    private LocalDate dataAvaliacao;

    private String observacoes;

    private LocalDateTime createdAt;
}

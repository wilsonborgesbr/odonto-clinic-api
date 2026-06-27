package com.example.demo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "convenios")
public class Convenio {

    @Id
    private String id;

    private String nome;

    @Indexed(unique = true)
    private String cnpj;

    private String telefone;
    private String email;
    private String tabelaDePrecos;

    @Builder.Default
    private Boolean ativo = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

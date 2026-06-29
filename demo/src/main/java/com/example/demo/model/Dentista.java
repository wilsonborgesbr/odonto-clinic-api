package com.example.demo.model;

import com.example.demo.enums.EspecialidadeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "dentistas")
public class Dentista {

    @Id
    private String id;

    @NotBlank(message = "Nome completo é obrigatório")
    private String nomeCompleto;

    @NotBlank(message = "CRO é obrigatório")
    @Indexed(unique = true)
    private String cro;

    private List<EspecialidadeEnum> especialidades;

    @Email(message = "E-mail inválido")
    private String email;

    private String telefoneCelular;

    private Boolean ativo;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

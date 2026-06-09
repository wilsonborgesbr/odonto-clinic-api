package com.example.demo.model;

import com.example.demo.enums.CargoFuncionarioEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "funcionarios")
public class Funcionario {

    @Id
    private String id;

    private String nomeCompleto;

    @Indexed(unique = true)
    private String cpf;

    private CargoFuncionarioEnum cargo;

    private String email;

    private String telefoneCelular;

    private Boolean ativo;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

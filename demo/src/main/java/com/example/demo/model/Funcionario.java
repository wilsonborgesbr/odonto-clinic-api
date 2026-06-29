package com.example.demo.model;

import com.example.demo.enums.CargoFuncionarioEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "funcionarios")
public class Funcionario {

    @Id
    private String id;

    @NotBlank(message = "Nome completo é obrigatório")
    private String nomeCompleto;

    @NotBlank(message = "CPF é obrigatório")
    @Indexed(unique = true)
    private String cpf;

    @NotNull(message = "Cargo é obrigatório")
    private CargoFuncionarioEnum cargo;

    @Email(message = "E-mail inválido")
    private String email;

    private String telefoneCelular;

    private Boolean ativo;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

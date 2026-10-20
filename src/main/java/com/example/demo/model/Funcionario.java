package com.example.demo.model;

import com.example.demo.enums.CargoFuncionarioEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import com.example.demo.enums.SexoEnum;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "funcionarios")
@CompoundIndex(
        name = "clinica_cpf_unique",
        def = "{'clinicaId': 1, 'cpf': 1}",
        unique = true,
        partialFilter = "{'cpf': {$type: 'string', $gt: ''}}"
)
public class Funcionario {

    @Id
    private String id;

    @Indexed
    private String clinicaId;

    @NotBlank(message = "Nome completo é obrigatório")
    private String nomeCompleto;

    @NotBlank(message = "CPF é obrigatório")
    private String cpf;

    @NotNull(message = "Cargo é obrigatório")
    private CargoFuncionarioEnum cargo;

    @Email(message = "E-mail inválido")
    private String email;

    private String telefoneCelular;

    @NotNull(message = "Sexo é obrigatório")
    private SexoEnum sexo;

    @Valid
    private Endereco endereco;

    private String fotoUrl;

    private Boolean ativo;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

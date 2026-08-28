package com.example.demo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;

/**
 * Unidade de negócio isolada — cada filial é uma {@code Clinica} separada,
 * com seu próprio código de login e seu próprio conjunto de dados.
 * Todos os models transacionais (Paciente, Dentista, Agendamento…) carregam
 * {@code clinicaId} e são filtrados por ele em cada query.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "clinicas")
public class Clinica {

    @Id
    private String id;

    /**
     * Código curto usado no login — ex.: "odontosocorro", "clinicasorriso-copacabana".
     * É o identificador visível pro usuário na tela de login (equivalente ao "Clínica ID"
     * do iClinic). Único no sistema.
     */
    @NotBlank(message = "Código da clínica é obrigatório")
    @Pattern(
        regexp = "^[a-z0-9](?:[a-z0-9-]{2,38}[a-z0-9])$",
        message = "Código deve ter 4–40 caracteres, minúsculas, números e hífens"
    )
    @Indexed(unique = true)
    private String codigo;

    @NotBlank(message = "Nome da clínica é obrigatório")
    private String nome;

    private String razaoSocial;

    private String cnpj;

    private String telefone;

    @Email(message = "E-mail de contato inválido")
    private String emailContato;

    @Valid
    private Endereco endereco;

    /** True enquanto a assinatura da clínica está ativa. Soft delete. */
    private Boolean ativo;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

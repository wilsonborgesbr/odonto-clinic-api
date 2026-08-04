package com.example.demo.dto;

import com.example.demo.model.Endereco;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Fluxo de auto-cadastro: cria a Clínica + o Usuário Proprietário na mesma chamada.
 * Só quem cria uma nova conta consegue registrar uma nova clínica — daí a rota
 * ser {@code POST /auth/register-clinica} pública.
 */
@Data
public class RegisterClinicaRequestDTO {

    @NotBlank(message = "Código da clínica é obrigatório")
    @Pattern(
        regexp = "^[a-z0-9](?:[a-z0-9-]{2,38}[a-z0-9])$",
        message = "Código deve ter 4–40 caracteres, minúsculas, números e hífens"
    )
    private String clinicaCodigo;

    @NotBlank(message = "Nome da clínica é obrigatório")
    private String clinicaNome;

    private String cnpj;

    private String telefone;

    @Email(message = "E-mail de contato da clínica inválido")
    private String emailContato;

    @Valid
    private Endereco endereco;

    // ─── Proprietário ───

    @NotBlank(message = "Nome do proprietário é obrigatório")
    private String adminNome;

    @NotBlank(message = "E-mail do proprietário é obrigatório")
    @Email(message = "E-mail do proprietário inválido")
    private String adminEmail;

    @NotBlank(message = "Senha do proprietário é obrigatória")
    @Size(min = 6, message = "Senha deve ter no mínimo 6 caracteres")
    private String adminPassword;
}

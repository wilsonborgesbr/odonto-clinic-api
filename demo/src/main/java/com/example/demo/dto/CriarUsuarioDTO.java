package com.example.demo.dto;

import com.example.demo.enums.PermissaoEnum;
import com.example.demo.enums.RoleEnum;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
public class CriarUsuarioDTO {

    @NotBlank(message = "Nome é obrigatório")
    private String name;

    @NotBlank(message = "E-mail é obrigatório")
    @Email(message = "E-mail inválido")
    private String email;

    @NotBlank(message = "Senha é obrigatória")
    @Size(min = 6, message = "Senha deve ter no mínimo 6 caracteres")
    private String password;

    @NotNull(message = "Cargo é obrigatório")
    private RoleEnum role;

    /** Se null, usa permissões padrão do role. */
    private Set<PermissaoEnum> permissoes;
}

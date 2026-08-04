package com.example.demo.dto;

import com.example.demo.enums.PermissaoEnum;
import com.example.demo.enums.RoleEnum;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;

@Data
public class AtualizarUsuarioDTO {

    @NotBlank(message = "Nome é obrigatório")
    private String name;

    @NotBlank(message = "E-mail é obrigatório")
    @Email(message = "E-mail inválido")
    private String email;

    @NotNull(message = "Cargo é obrigatório")
    private RoleEnum role;

    /** Se null, usa permissões padrão do role. */
    private Set<PermissaoEnum> permissoes;

    /** Opcional — se preenchido, redefine a senha do usuário. */
    private String novaSenha;
}

package com.example.demo.dto;

import com.example.demo.enums.PermissaoEnum;
import com.example.demo.enums.RoleEnum;
import com.example.demo.model.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

/** Representação de {@code User} para o frontend — sem exposer da senha. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioDTO {
    private String id;
    private String name;
    private String email;
    private RoleEnum role;
    private Set<PermissaoEnum> permissoes;
    private Boolean ativo;
    private LocalDateTime createdAt;

    public static UsuarioDTO from(User user) {
        UsuarioDTO dto = new UsuarioDTO();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setPermissoes(user.permissoesEfetivas());
        dto.setAtivo(user.getAtivo());
        dto.setCreatedAt(user.getCreatedAt());
        return dto;
    }
}

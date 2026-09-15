package com.example.demo.model;

import com.example.demo.enums.PermissaoEnum;
import com.example.demo.enums.RoleEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Conta de acesso ao sistema. Cada {@code User} pertence a UMA {@code Clinica}
 * — o par (email, clinicaId) é único, mas o mesmo email pode existir em
 * clínicas diferentes (útil para franquias).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
@CompoundIndexes({
    @CompoundIndex(name = "email_clinica_unique", def = "{'email': 1, 'clinicaId': 1}", unique = true)
})
public class User implements UserDetails {

    @Id
    private String id;

    private String name;

    /** Único dentro da clínica, não global. Serve como username. */
    private String email;

    private String password;

    /**
     * FK para {@code Clinica}. Todo dado do sistema é isolado por este id.
     */
    @Indexed
    private String clinicaId;

    /** Papel do usuário. Define permissões padrão. */
    private RoleEnum role;

    /**
     * Permissões explícitas — quando nulo/vazio, usa-se as permissões padrão do role.
     * Quando preenchido, sobrescreve as padrões (proprietário customizou).
     */
    private Set<PermissaoEnum> permissoes;

    /** Vínculo opcional com registro de Funcionario/Dentista da clínica. */
    private String funcionarioId;
    private String dentistaId;

    /** Soft delete de login (usuário não removido mas bloqueado). */
    @Builder.Default
    private Boolean ativo = true;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // ============ Spring Security UserDetails ============

    /**
     * Autoridades: uma para o role e uma para cada permissão efetiva.
     * Usadas por {@code @PreAuthorize("hasAuthority('PACIENTES')")} nos endpoints.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> auths = new ArrayList<>();
        if (role != null) {
            auths.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        }
        for (PermissaoEnum p : permissoesEfetivas()) {
            auths.add(new SimpleGrantedAuthority(p.name()));
        }
        return auths;
    }

    /**
     * Permissões efetivas = customizadas se existirem, senão padrões do role.
     * Método também usado no login pra popular claims do JWT.
     */
    public Set<PermissaoEnum> permissoesEfetivas() {
        if (permissoes != null && !permissoes.isEmpty()) {
            return EnumSet.copyOf(permissoes);
        }
        if (role != null) {
            return role.permissoesPadrao();
        }
        return EnumSet.noneOf(PermissaoEnum.class);
    }

    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return Boolean.TRUE.equals(ativo); }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return Boolean.TRUE.equals(ativo); }
}

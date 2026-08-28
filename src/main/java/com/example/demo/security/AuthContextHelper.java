package com.example.demo.security;

import com.example.demo.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

/**
 * Utilitário para extrair o usuário e clínica atuais do contexto de segurança.
 * Todo Service deve chamar {@link #currentClinicaId()} antes de qualquer query
 * pra garantir isolamento por tenant.
 */
public final class AuthContextHelper {

    private AuthContextHelper() {}

    public static User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || !(auth.getPrincipal() instanceof User)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida.");
        }
        return (User) auth.getPrincipal();
    }

    public static String currentClinicaId() {
        String id = currentUser().getClinicaId();
        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário sem clínica associada.");
        }
        return id;
    }

    public static String currentUserId() {
        return currentUser().getId();
    }
}

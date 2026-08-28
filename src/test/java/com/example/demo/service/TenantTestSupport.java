package com.example.demo.service;

import com.example.demo.enums.RoleEnum;
import com.example.demo.model.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utilitário para testes: popula o {@code SecurityContext} com um usuário fake
 * que tem {@code clinicaId=TEST_CLINICA_ID}. Todos os services que usam
 * {@code AuthContextHelper.currentClinicaId()} passam a funcionar em testes.
 *
 * <p>Chame {@link #loginTestUser()} no {@code @BeforeEach} e {@link #logout()}
 * no {@code @AfterEach}.
 */
public final class TenantTestSupport {

    /** Constante disponível para os testes filtrarem/mockarem por esta clínica. */
    public static final String TEST_CLINICA_ID = "clinica-test";

    private TenantTestSupport() {}

    public static void loginTestUser() {
        User user = User.builder()
                .id("user-test")
                .name("Test User")
                .email("test@bokka.dev")
                .clinicaId(TEST_CLINICA_ID)
                .role(RoleEnum.PROPRIETARIO)
                .ativo(true)
                .build();

        var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    public static void logout() {
        SecurityContextHolder.clearContext();
    }
}

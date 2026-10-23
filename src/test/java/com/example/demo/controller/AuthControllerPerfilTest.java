package com.example.demo.controller;

import com.example.demo.dto.AuthResponseDTO;
import com.example.demo.dto.UpdateProfileDTO;
import com.example.demo.dto.UsuarioDTO;
import com.example.demo.enums.RoleEnum;
import com.example.demo.enums.TipoTokenAuthEnum;
import com.example.demo.model.Clinica;
import com.example.demo.model.User;
import com.example.demo.repository.ClinicaRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.AuthTokenService;
import com.example.demo.service.ConfirmacaoEmailService;
import com.example.demo.service.TokenService;
import com.example.demo.service.UserLookupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerPerfilTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ClinicaRepository clinicaRepository;
    @Mock
    private UserLookupService userLookupService;
    @Mock
    private TokenService tokenService;
    @Mock
    private AuthTokenService authTokenService;
    @Mock
    private ConfirmacaoEmailService confirmacaoEmailService;

    @InjectMocks
    private AuthController controller;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User logar(RoleEnum role, Boolean confirmado) {
        User user = User.builder().id("u1").clinicaId("c1").name("Dona").email("dona@x.com").role(role)
                .ativo(true).emailConfirmado(confirmado).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        Clinica clinica = Clinica.builder().id("c1").codigo("tainah").nome("Tainah").ativo(true).build();
        lenient().when(clinicaRepository.findById("c1")).thenReturn(Optional.of(clinica));
        lenient().when(tokenService.generateToken(any(), any(), any())).thenReturn("jwt");
        return user;
    }

    private UpdateProfileDTO perfil(String nome, String email) {
        UpdateProfileDTO dto = new UpdateProfileDTO();
        dto.setName(nome);
        dto.setEmail(email);
        return dto;
    }

    @Test
    void updateProfile_DonoQueTrocaDeEmailPrecisaConfirmarOutraVez() {
        User dona = logar(RoleEnum.PROPRIETARIO, true);
        when(userLookupService.emailEmUso("novo@x.com", "c1")).thenReturn(false);

        AuthResponseDTO r = controller.updateProfile(perfil("Dona", "Novo@X.com"), new MockHttpServletRequest()).getBody();

        assertEquals("novo@x.com", dona.getEmail());
        assertEquals(Boolean.FALSE, dona.getEmailConfirmado());
        assertNull(dona.getEmailConfirmadoEm());
        assertFalse(r.isEmailConfirmado());
        verify(authTokenService).invalidarPendentes("u1", TipoTokenAuthEnum.CONFIRMAR_EMAIL_DONO);
        verify(confirmacaoEmailService).enviarConfirmacao(eq(dona), any(Clinica.class), any());
    }

    @Test
    void updateProfile_MudarSoACaixaDoEmailNaoPedeNovaConfirmacao() {
        User dona = logar(RoleEnum.PROPRIETARIO, true);
        dona.setEmail("Dona@x.com");

        AuthResponseDTO r = controller.updateProfile(perfil("Dona", "dona@x.com"), new MockHttpServletRequest()).getBody();

        assertEquals("dona@x.com", dona.getEmail());
        assertTrue(r.isEmailConfirmado());
        verifyNoInteractions(authTokenService, confirmacaoEmailService);
    }

    @Test
    void updateProfile_TrocarSoONomeNaoMexeNaConfirmacao() {
        User dona = logar(RoleEnum.PROPRIETARIO, true);

        controller.updateProfile(perfil("Dona Nova", "dona@x.com"), new MockHttpServletRequest());

        assertEquals("Dona Nova", dona.getName());
        assertEquals(Boolean.TRUE, dona.getEmailConfirmado());
        verifyNoInteractions(authTokenService, confirmacaoEmailService);
    }

    @Test
    void updateProfile_QuemNaoEDonoNaoEntraNoFluxoDeConfirmacao() {
        User recepcao = logar(RoleEnum.RECEPCIONISTA, null);
        when(userLookupService.emailEmUso("novo@x.com", "c1")).thenReturn(false);

        AuthResponseDTO r = controller.updateProfile(perfil("Rec", "novo@x.com"), new MockHttpServletRequest()).getBody();

        assertNull(recepcao.getEmailConfirmado());
        assertTrue(r.isEmailConfirmado());
        verifyNoInteractions(authTokenService, confirmacaoEmailService);
    }

    @Test
    void updateProfile_EmailJaUsadoNaoAlteraNada() {
        User dona = logar(RoleEnum.PROPRIETARIO, true);
        when(userLookupService.emailEmUso("outro@x.com", "c1")).thenReturn(true);

        assertThrows(ResponseStatusException.class,
                () -> controller.updateProfile(perfil("Dona", "outro@x.com"), new MockHttpServletRequest()));

        assertEquals(Boolean.TRUE, dona.getEmailConfirmado());
        verifyNoInteractions(confirmacaoEmailService);
    }

    @Test
    void usuarioDto_EmailConfirmadoLegadoSemOCampoVemTrue() {
        User legado = User.builder().id("u1").email("a@x.com").role(RoleEnum.PROPRIETARIO).build();
        User pendente = User.builder().id("u2").email("b@x.com").role(RoleEnum.PROPRIETARIO).emailConfirmado(false).build();
        User confirmado = User.builder().id("u3").email("c@x.com").role(RoleEnum.PROPRIETARIO).emailConfirmado(true).build();

        assertTrue(UsuarioDTO.from(legado).isEmailConfirmado());
        assertFalse(UsuarioDTO.from(pendente).isEmailConfirmado());
        assertTrue(UsuarioDTO.from(confirmado).isEmailConfirmado());
    }

    @Test
    void authResponse_UsuarioLegadoVemConfirmadoEBuilderPadraoTambem() {
        assertTrue(new AuthResponseDTO("jwt").isEmailConfirmado());
        assertTrue(AuthResponseDTO.de("jwt", User.builder().build()).isEmailConfirmado());
        assertFalse(AuthResponseDTO.de("jwt", User.builder().emailConfirmado(false).build()).isEmailConfirmado());
    }
}

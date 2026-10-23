package com.example.demo.service;

import com.example.demo.dto.MensagemDTO;
import com.example.demo.email.EmailDispatcher;
import com.example.demo.email.EmailService;
import com.example.demo.email.EmailTemplateService;
import com.example.demo.enums.RoleEnum;
import com.example.demo.enums.TipoTokenAuthEnum;
import com.example.demo.model.AuthToken;
import com.example.demo.model.Clinica;
import com.example.demo.model.User;
import com.example.demo.repository.ClinicaRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.AuthTokenService.TokenEmitido;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfirmacaoEmailServiceTest {

    private static final String TOKEN = "TOKEN_EM_CLARO_43_CARACTERES_ABCDEFGHIJKLMNO";
    private static final String IP = "10.0.0.1";

    @Mock
    private AuthTokenService authTokenService;
    @Mock
    private EmailService emailService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ClinicaRepository clinicaRepository;
    @Spy
    private EmailTemplateService templateService = new EmailTemplateService("https://bokka.com.br");
    @Spy
    private EmailDispatcher dispatcher = new EmailDispatcher(Runnable::run);
    @Spy
    private RateLimitService rateLimitService = new RateLimitService();

    @InjectMocks
    private ConfirmacaoEmailService service;

    private Clinica clinica;
    private User dono;

    @BeforeEach
    void setUp() {
        clinica = Clinica.builder().id("c1").codigo("tainah").nome("Consultório Tainah").ativo(true).build();
        dono = User.builder().id("u1").clinicaId("c1").email("dono@x.com").name("Dra. Tainah")
                .role(RoleEnum.PROPRIETARIO).ativo(true).emailConfirmado(false).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void logarComo(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private void tokenSeraEmitido() {
        when(authTokenService.emitir(eq("u1"), eq("c1"), eq(TipoTokenAuthEnum.CONFIRMAR_EMAIL_DONO), any(), any()))
                .thenReturn(new TokenEmitido(TOKEN, Instant.now().plusSeconds(3600)));
    }

    @Test
    void enviarConfirmacao_EmiteTokenDe48hEEnviaEmailComLinkDoFrontend() {
        tokenSeraEmitido();

        service.enviarConfirmacao(dono, clinica, IP);

        verify(authTokenService).emitir("u1", "c1", TipoTokenAuthEnum.CONFIRMAR_EMAIL_DONO, Duration.ofHours(48), IP);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviar(eq("dono@x.com"), eq("Confirme seu e-mail - Bokka"), html.capture());
        assertTrue(html.getValue().contains("https://bokka.com.br/confirmar-email?token=" + TOKEN + "&amp;c=tainah"));
        assertTrue(html.getValue().contains("Dra. Tainah"));
        assertTrue(html.getValue().contains("Consultório Tainah"));
        assertTrue(html.getValue().contains("48 horas"));
    }

    @Test
    void enviarConfirmacao_FalhaDeEnvioOuDeTokenNuncaVazaParaQuemChama() {
        tokenSeraEmitido();
        doThrow(new RuntimeException("resend fora")).when(emailService).enviar(any(), any(), any());
        assertDoesNotThrow(() -> service.enviarConfirmacao(dono, clinica, IP));

        when(authTokenService.emitir(any(), any(), any(), any(), any())).thenThrow(new RuntimeException("mongo fora"));
        assertDoesNotThrow(() -> service.enviarConfirmacao(dono, clinica, IP));
    }

    @Test
    void confirmar_TokenValidoMarcaOEmailComoConfirmado() {
        when(authTokenService.consumir(TOKEN, TipoTokenAuthEnum.CONFIRMAR_EMAIL_DONO))
                .thenReturn(AuthToken.builder().userId("u1").build());
        when(userRepository.findById("u1")).thenReturn(Optional.of(dono));

        boolean jaConfirmado = service.confirmar(TOKEN);

        assertFalse(jaConfirmado);
        ArgumentCaptor<User> salvo = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(salvo.capture());
        assertEquals(Boolean.TRUE, salvo.getValue().getEmailConfirmado());
        assertNotNull(salvo.getValue().getEmailConfirmadoEm());
        assertTrue(salvo.getValue().emailEstaConfirmado());
    }

    @Test
    void confirmar_TokenValidoDeUsuarioJaConfirmadoDevolveJaConfirmadoSemRegravar() {
        dono.setEmailConfirmado(true);
        when(authTokenService.consumir(TOKEN, TipoTokenAuthEnum.CONFIRMAR_EMAIL_DONO))
                .thenReturn(AuthToken.builder().userId("u1").build());
        when(userRepository.findById("u1")).thenReturn(Optional.of(dono));

        assertTrue(service.confirmar(TOKEN));

        verify(userRepository, never()).save(any());
    }

    @Test
    void confirmar_LegadoSemOCampoContaComoJaConfirmadoEGravaExplicito() {
        dono.setEmailConfirmado(null);
        when(authTokenService.consumir(TOKEN, TipoTokenAuthEnum.CONFIRMAR_EMAIL_DONO))
                .thenReturn(AuthToken.builder().userId("u1").build());
        when(userRepository.findById("u1")).thenReturn(Optional.of(dono));

        assertTrue(service.confirmar(TOKEN));

        assertEquals(Boolean.TRUE, dono.getEmailConfirmado());
        assertNull(dono.getEmailConfirmadoEm());
    }

    @Test
    void confirmar_TokenReusadoOuExpiradoRetorna400SemAlterarOUsuario() {
        when(authTokenService.consumir(any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, AuthTokenService.MENSAGEM_INVALIDO));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.confirmar(TOKEN));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void confirmar_UsuarioApagadoRetorna400() {
        when(authTokenService.consumir(TOKEN, TipoTokenAuthEnum.CONFIRMAR_EMAIL_DONO))
                .thenReturn(AuthToken.builder().userId("u1").build());
        when(userRepository.findById("u1")).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service.confirmar(TOKEN));
    }

    // ───────────── reenviar ─────────────

    @Test
    void reenviar_DonoNaoConfirmadoRecebeNovoEmail() {
        logarComo(dono);
        tokenSeraEmitido();
        when(clinicaRepository.findById("c1")).thenReturn(Optional.of(clinica));

        MensagemDTO r = service.reenviar(IP);

        assertEquals(ConfirmacaoEmailService.MSG_REENVIADO, r.mensagem());
        verify(emailService).enviar(eq("dono@x.com"), any(), any());
    }

    @Test
    void reenviar_JaConfirmadoNaoEnviaNemConsomeLimite() {
        dono.setEmailConfirmado(true);
        logarComo(dono);

        for (int i = 0; i < 10; i++) {
            assertEquals(ConfirmacaoEmailService.MSG_JA_CONFIRMADO, service.reenviar(IP).mensagem());
        }

        verifyNoInteractions(emailService, authTokenService);
    }

    @Test
    void reenviar_LegadoSemOCampoContaComoConfirmado() {
        dono.setEmailConfirmado(null);
        logarComo(dono);

        assertEquals(ConfirmacaoEmailService.MSG_JA_CONFIRMADO, service.reenviar(IP).mensagem());
    }

    @Test
    void reenviar_QuemNaoEProprietarioRecebe403() {
        dono.setRole(RoleEnum.RECEPCIONISTA);
        logarComo(dono);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.reenviar(IP));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verifyNoInteractions(emailService);
    }

    @Test
    void reenviar_AposOLimiteDeTresPedidosRetorna429() {
        logarComo(dono);
        tokenSeraEmitido();
        when(clinicaRepository.findById("c1")).thenReturn(Optional.of(clinica));

        for (int i = 0; i < ConfirmacaoEmailService.MAX_REENVIOS_POR_USUARIO; i++) {
            service.reenviar("10.0.9." + i);
        }
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.reenviar("10.0.9.99"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
        assertEquals(ConfirmacaoEmailService.MSG_LIMITE, ex.getReason());
        verify(emailService, times(ConfirmacaoEmailService.MAX_REENVIOS_POR_USUARIO)).enviar(any(), any(), any());
    }

    @Test
    void reenviar_LimitePorIpValeParaUsuariosDiferentes() {
        when(clinicaRepository.findById("c1")).thenReturn(Optional.of(clinica));
        for (int i = 0; i < ConfirmacaoEmailService.MAX_REENVIOS_POR_IP; i++) {
            User outro = User.builder().id("u" + (100 + i)).clinicaId("c1").email("o" + i + "@x.com").name("O")
                    .role(RoleEnum.PROPRIETARIO).ativo(true).emailConfirmado(false).build();
            logarComo(outro);
            when(authTokenService.emitir(eq(outro.getId()), any(), any(), any(), any()))
                    .thenReturn(new TokenEmitido(TOKEN, Instant.now().plusSeconds(60)));
            service.reenviar("10.7.7.7");
        }
        logarComo(dono);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.reenviar("10.7.7.7"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
    }
}

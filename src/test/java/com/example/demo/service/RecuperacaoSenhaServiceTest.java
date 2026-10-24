package com.example.demo.service;

import com.example.demo.dto.MensagemDTO;
import com.example.demo.dto.RecuperarSenhaDTO;
import com.example.demo.dto.RedefinirSenhaDTO;
import com.example.demo.dto.ValidarTokenRequestDTO;
import com.example.demo.dto.ValidarTokenResponseDTO;
import com.example.demo.email.EmailDispatcher;
import com.example.demo.email.EmailService;
import com.example.demo.email.EmailTemplateService;
import com.example.demo.enums.StatusContaEnum;
import com.example.demo.enums.TipoTokenAuthEnum;
import com.example.demo.model.AuthToken;
import com.example.demo.model.Clinica;
import com.example.demo.model.User;
import com.example.demo.repository.ClinicaRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.AuthTokenService.TokenEmitido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecuperacaoSenhaServiceTest {

    private static final String IP = "10.0.0.1";
    private static final String TOKEN = "TOKEN_EM_CLARO_43_CARACTERES_ABCDEFGHIJKLMNO";

    @Mock
    private AuthTokenService authTokenService;
    @Mock
    private EmailService emailService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ClinicaRepository clinicaRepository;
    @Mock
    private UserLookupService userLookupService;
    @Spy
    private EmailTemplateService templateService = new EmailTemplateService("https://bokka.com.br");
    @Spy
    private EmailDispatcher dispatcher = new EmailDispatcher(Runnable::run);
    @Spy
    private RateLimitService rateLimitService = new RateLimitService();

    @InjectMocks
    private RecuperacaoSenhaService service;

    private Clinica clinica;
    private User user;

    @BeforeEach
    void setUp() {
        clinica = Clinica.builder().id("c1").codigo("tainah").nome("Consultório Tainah").ativo(true).build();
        user = User.builder().id("u1").clinicaId("c1").email("ana@x.com").name("Ana Souza").ativo(true).build();
    }

    private RecuperarSenhaDTO pedido(String email, String codigo) {
        RecuperarSenhaDTO dto = new RecuperarSenhaDTO();
        dto.setEmail(email);
        dto.setClinicaCodigo(codigo);
        return dto;
    }

    private void contaExiste() {
        when(clinicaRepository.findByCodigo("tainah")).thenReturn(Optional.of(clinica));
        when(userLookupService.buscarPorEmail("ana@x.com", "c1")).thenReturn(Optional.of(user));
        when(authTokenService.emitir(eq("u1"), eq("c1"), eq(TipoTokenAuthEnum.RESET_SENHA), any(), any()))
                .thenReturn(new TokenEmitido(TOKEN, Instant.now().plusSeconds(1800)));
    }

    // ───────────── solicitar ─────────────

    @Test
    void solicitar_ContaExistenteRecebeEmailComLinkDoFrontendETokenDe30Min() {
        contaExiste();

        MensagemDTO r = service.solicitar(pedido("  Ana@X.com ", "TAINAH"), IP);

        assertEquals(RecuperacaoSenhaService.MSG_NEUTRA, r.mensagem());
        verify(authTokenService).emitir("u1", "c1", TipoTokenAuthEnum.RESET_SENHA, Duration.ofMinutes(30), IP);
        ArgumentCaptor<String> assunto = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviar(eq("ana@x.com"), assunto.capture(), html.capture(), texto.capture(), eq("reset-senha"));
        assertEquals("Redefinir sua senha no Bokka", assunto.getValue());
        assertTrue(texto.getValue().contains("\nhttps://bokka.com.br/redefinir-senha?token=" + TOKEN + "&c=tainah\n"));
        assertTrue(texto.getValue().contains("Ana Souza"));
        assertTrue(texto.getValue().contains("30 minutos"));
        assertFalse(texto.getValue().contains("${"));
        assertTrue(html.getValue().contains("https://bokka.com.br/redefinir-senha?token=" + TOKEN + "&amp;c=tainah"));
        assertTrue(html.getValue().contains("Ana Souza"));
        assertTrue(html.getValue().contains("30 minutos"));
        assertFalse(html.getValue().contains("${"));
    }

    @Test
    void solicitar_RespostaIdenticaParaContaInexistenteEClinicaInexistente() {
        MensagemDTO existente;
        contaExiste();
        existente = service.solicitar(pedido("ana@x.com", "tainah"), IP);

        when(userLookupService.buscarPorEmail("fantasma@x.com", "c1")).thenReturn(Optional.empty());
        MensagemDTO emailInexistente = service.solicitar(pedido("fantasma@x.com", "tainah"), IP);
        when(clinicaRepository.findByCodigo("nao-existe")).thenReturn(Optional.empty());
        MensagemDTO clinicaInexistente = service.solicitar(pedido("ana@x.com", "nao-existe"), IP);

        assertEquals(existente, emailInexistente);
        assertEquals(existente, clinicaInexistente);
        verify(emailService, times(1)).enviar(any(), any(), any(), any(), any());
    }

    @Test
    void solicitar_SemCodigoEnviaUmEmailPorContaEncontrada() {
        User outra = User.builder().id("u2").clinicaId("c2").email("ana@x.com").name("Ana").ativo(true).build();
        Clinica clinica2 = Clinica.builder().id("c2").codigo("zeta").nome("Zeta").ativo(true).build();
        when(userLookupService.buscarContasPorEmail("ana@x.com")).thenReturn(List.of(user, outra));
        when(clinicaRepository.findAllById(any())).thenReturn(List.of(clinica, clinica2));
        when(authTokenService.emitir(any(), any(), eq(TipoTokenAuthEnum.RESET_SENHA), any(), any()))
                .thenReturn(new TokenEmitido(TOKEN, Instant.now().plusSeconds(1800)));

        service.solicitar(pedido("ana@x.com", null), IP);

        verify(authTokenService).emitir("u1", "c1", TipoTokenAuthEnum.RESET_SENHA, Duration.ofMinutes(30), IP);
        verify(authTokenService).emitir("u2", "c2", TipoTokenAuthEnum.RESET_SENHA, Duration.ofMinutes(30), IP);
        verify(emailService, times(2)).enviar(eq("ana@x.com"), any(), any(), any(), any());
    }

    @Test
    void solicitar_ContaInativaOuBloqueadaNaoRecebeEmail() {
        user.setAtivo(false);
        when(clinicaRepository.findByCodigo("tainah")).thenReturn(Optional.of(clinica));
        when(userLookupService.buscarPorEmail("ana@x.com", "c1")).thenReturn(Optional.of(user));
        service.solicitar(pedido("ana@x.com", "tainah"), IP);

        user.setAtivo(true);
        user.setStatusConta(StatusContaEnum.BLOQUEADO);
        service.solicitar(pedido("ana@x.com", "tainah"), "10.0.0.2");

        verifyNoInteractions(authTokenService, emailService);
    }

    @Test
    void solicitar_FalhaNoEnvioNaoChegaAoUsuario() {
        contaExiste();
        doThrow(new RuntimeException("resend fora do ar")).when(emailService).enviar(any(), any(), any(), any(), any());

        MensagemDTO r = assertDoesNotThrow(() -> service.solicitar(pedido("ana@x.com", "tainah"), IP));

        assertEquals(RecuperacaoSenhaService.MSG_NEUTRA, r.mensagem());
    }

    @Test
    void solicitar_FalhaAoEmitirTokenTambemNaoChegaAoUsuario() {
        when(clinicaRepository.findByCodigo("tainah")).thenReturn(Optional.of(clinica));
        when(userLookupService.buscarPorEmail("ana@x.com", "c1")).thenReturn(Optional.of(user));
        when(authTokenService.emitir(any(), any(), any(), any(), any())).thenThrow(new RuntimeException("mongo fora"));

        assertDoesNotThrow(() -> service.solicitar(pedido("ana@x.com", "tainah"), IP));
        verifyNoInteractions(emailService);
    }

    @Test
    void solicitar_QuartoPedidoDoMesmoEmailNaoEnviaMasResponde200Igual() {
        contaExiste();

        MensagemDTO primeira = service.solicitar(pedido("ana@x.com", "tainah"), "10.0.1.1");
        service.solicitar(pedido("ana@x.com", "tainah"), "10.0.1.2");
        service.solicitar(pedido("ana@x.com", "tainah"), "10.0.1.3");
        MensagemDTO quarta = service.solicitar(pedido("ana@x.com", "tainah"), "10.0.1.4");

        assertEquals(primeira, quarta);
        verify(emailService, times(RecuperacaoSenhaService.MAX_PEDIDOS_POR_EMAIL)).enviar(any(), any(), any(), any(), any());
    }

    @Test
    void solicitar_LimiteContaEmailInexistenteTambemEntaoNaoDaParaDistinguir() {
        for (int i = 0; i < 6; i++) {
            MensagemDTO r = service.solicitar(pedido("fantasma@x.com", "tainah"), "10.0.2." + i);
            assertEquals(RecuperacaoSenhaService.MSG_NEUTRA, r.mensagem());
        }
        verifyNoInteractions(emailService);
    }

    @Test
    void solicitar_LimitePorIpBloqueiaEmailsDiferentesDoMesmoIp() {
        when(clinicaRepository.findByCodigo("tainah")).thenReturn(Optional.of(clinica));
        when(userLookupService.buscarPorEmail(any(), eq("c1"))).thenReturn(Optional.of(user));
        when(authTokenService.emitir(any(), any(), any(), any(), any()))
                .thenReturn(new TokenEmitido(TOKEN, Instant.now().plusSeconds(1800)));

        for (int i = 0; i < RecuperacaoSenhaService.MAX_PEDIDOS_POR_IP + 5; i++) {
            service.solicitar(pedido("conta" + i + "@x.com", "tainah"), "10.0.3.3");
        }

        verify(emailService, times(RecuperacaoSenhaService.MAX_PEDIDOS_POR_IP)).enviar(any(), any(), any(), any(), any());
    }

    @Test
    void solicitar_TokenEmClaroNuncaApareceNaResposta() {
        contaExiste();

        MensagemDTO r = service.solicitar(pedido("ana@x.com", "tainah"), IP);

        assertFalse(r.mensagem().contains(TOKEN));
    }

    // ───────────── validar-token ─────────────

    private ValidarTokenRequestDTO validar(String token, String tipo) {
        ValidarTokenRequestDTO dto = new ValidarTokenRequestDTO();
        dto.setToken(token);
        dto.setTipo(tipo);
        return dto;
    }

    @Test
    void validarToken_ValidoDevolveNomeDoUsuarioEDadosDaClinica() {
        AuthToken registro = AuthToken.builder().userId("u1").tipo(TipoTokenAuthEnum.RESET_SENHA).build();
        when(authTokenService.validar(TOKEN, TipoTokenAuthEnum.RESET_SENHA)).thenReturn(registro);
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(clinicaRepository.findById("c1")).thenReturn(Optional.of(clinica));

        ValidarTokenResponseDTO r = service.validarToken(validar(TOKEN, "reset_senha"));

        assertEquals(new ValidarTokenResponseDTO(true, "Ana Souza", "Consultório Tainah", "tainah"), r);
    }

    @Test
    void validarToken_InvalidoExpiradoOuUsadoPropagaOMesmo400() {
        when(authTokenService.validar(any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, AuthTokenService.MENSAGEM_INVALIDO));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.validarToken(validar(TOKEN, "RESET_SENHA")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals(AuthTokenService.MENSAGEM_INVALIDO, ex.getReason());
    }

    @Test
    void validarToken_TipoDesconhecidoDaMesmaMensagemPadrao() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.validarToken(validar(TOKEN, "QUALQUER_COISA")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals(AuthTokenService.MENSAGEM_INVALIDO, ex.getReason());
        verifyNoInteractions(authTokenService);
    }

    @Test
    void validarToken_UsuarioApagadoDepoisDaEmissaoDaMesmaMensagem() {
        when(authTokenService.validar(TOKEN, TipoTokenAuthEnum.RESET_SENHA))
                .thenReturn(AuthToken.builder().userId("u1").build());
        when(userRepository.findById("u1")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.validarToken(validar(TOKEN, "RESET_SENHA")));

        assertEquals(AuthTokenService.MENSAGEM_INVALIDO, ex.getReason());
    }

    // ───────────── redefinir-senha ─────────────

    private RedefinirSenhaDTO redefinirDto(String token, String senha) {
        RedefinirSenhaDTO dto = new RedefinirSenhaDTO();
        dto.setToken(token);
        dto.setNovaSenha(senha);
        return dto;
    }

    @Test
    void redefinir_GravaHashBcryptQueimaOTokenInvalidaOutrosEEnviaAlerta() {
        when(authTokenService.consumir(TOKEN, TipoTokenAuthEnum.RESET_SENHA))
                .thenReturn(AuthToken.builder().userId("u1").build());
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(clinicaRepository.findById("c1")).thenReturn(Optional.of(clinica));

        MensagemDTO r = service.redefinir(redefinirDto(TOKEN, "NovaSenha!9"));

        assertEquals("Senha alterada com sucesso.", r.mensagem());
        ArgumentCaptor<User> salvo = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(salvo.capture());
        assertNotEquals("NovaSenha!9", salvo.getValue().getPassword());
        assertTrue(new BCryptPasswordEncoder().matches("NovaSenha!9", salvo.getValue().getPassword()));
        verify(authTokenService).invalidarPendentes("u1", TipoTokenAuthEnum.RESET_SENHA);

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviar(eq("ana@x.com"), eq("Sua senha foi alterada - Bokka"), html.capture(), texto.capture(), eq("senha-alterada"));
        assertTrue(texto.getValue().contains("\nhttps://bokka.com.br/recuperar-senha?c=tainah\n"));
        assertFalse(texto.getValue().contains("NovaSenha!9"));
        assertTrue(html.getValue().contains("https://bokka.com.br/recuperar-senha?c=tainah"));
        assertTrue(html.getValue().matches("(?s).*\\d{2}/\\d{2}/\\d{4} às \\d{2}:\\d{2}.*"));
        assertFalse(html.getValue().contains("NovaSenha!9"));
    }

    @Test
    void redefinir_TokenInvalidoOuReusadoRetorna400ESenhaNaoMuda() {
        when(authTokenService.consumir(any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, AuthTokenService.MENSAGEM_INVALIDO));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.redefinir(redefinirDto(TOKEN, "NovaSenha!9")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void redefinir_UsuarioInativoNaoTrocaSenha() {
        user.setAtivo(false);
        when(authTokenService.consumir(TOKEN, TipoTokenAuthEnum.RESET_SENHA))
                .thenReturn(AuthToken.builder().userId("u1").build());
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        assertThrows(ResponseStatusException.class, () -> service.redefinir(redefinirDto(TOKEN, "NovaSenha!9")));

        verify(userRepository, never()).save(any());
    }

    @Test
    void redefinir_FalhaNoAlertaNaoDesfazAMudancaDeSenha() {
        when(authTokenService.consumir(TOKEN, TipoTokenAuthEnum.RESET_SENHA))
                .thenReturn(AuthToken.builder().userId("u1").build());
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));
        when(clinicaRepository.findById("c1")).thenReturn(Optional.of(clinica));
        doThrow(new RuntimeException("resend fora")).when(emailService).enviar(any(), any(), any(), any(), any());

        MensagemDTO r = assertDoesNotThrow(() -> service.redefinir(redefinirDto(TOKEN, "NovaSenha!9")));

        assertEquals("Senha alterada com sucesso.", r.mensagem());
        verify(userRepository).save(user);
    }
}

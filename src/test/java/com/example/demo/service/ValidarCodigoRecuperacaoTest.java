package com.example.demo.service;

import com.example.demo.dto.RecuperarSenhaDTO;
import com.example.demo.dto.ValidarCodigoRecuperacaoDTO;
import com.example.demo.dto.ValidarCodigoResponseDTO;
import com.example.demo.email.EmailDispatcher;
import com.example.demo.email.EmailService;
import com.example.demo.email.EmailTemplateService;
import com.example.demo.enums.TipoTokenAuthEnum;
import com.example.demo.model.AuthToken;
import com.example.demo.model.Clinica;
import com.example.demo.model.User;
import com.example.demo.repository.ClinicaRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.AuthTokenService.CodigoEmitido;
import com.example.demo.service.AuthTokenService.TokenEmitido;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
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
class ValidarCodigoRecuperacaoTest {

    private static final String IP = "10.0.0.1";
    private static final String TICKET = "TICKET_EM_CLARO_43_CARACTERES_ABCDEFGHIJKLMNOP";
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

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
        ReflectionTestUtils.setField(service, "tempoMinimoValidacaoMs", 0L);
        clinica = Clinica.builder().id("c1").codigo("tainah").nome("Consultório Tainah").ativo(true).build();
        user = User.builder().id("u1").clinicaId("c1").email("ana@x.com").name("Ana Souza").ativo(true).build();
    }

    private ValidarCodigoRecuperacaoDTO dto(String email, String clinicaCodigo, String codigo) {
        ValidarCodigoRecuperacaoDTO dto = new ValidarCodigoRecuperacaoDTO();
        dto.setEmail(email);
        dto.setClinicaCodigo(clinicaCodigo);
        dto.setCodigo(codigo);
        return dto;
    }

    private void contaExiste() {
        when(clinicaRepository.findByCodigo("tainah")).thenReturn(Optional.of(clinica));
        when(userLookupService.buscarPorEmail("ana@x.com", "c1")).thenReturn(Optional.of(user));
    }

    private AuthToken tokenConsumido() {
        return AuthToken.builder().id("t1").userId("u1").clinicaId("c1").tipo(TipoTokenAuthEnum.RESET_SENHA)
                .usadoEm(Instant.now()).build();
    }

    private ResponseStatusException falha(ValidarCodigoRecuperacaoDTO dto) {
        return assertThrows(ResponseStatusException.class, () -> service.validarCodigo(dto, IP));
    }

    @Test
    void codigoCorreto_ConsomeOParETrocaPorTicketDe5Minutos() {
        contaExiste();
        when(authTokenService.consumirPorCodigo("u1", TipoTokenAuthEnum.RESET_SENHA, "004812"))
                .thenReturn(Optional.of(tokenConsumido()));
        when(authTokenService.emitir("u1", "c1", TipoTokenAuthEnum.RESET_SENHA, Duration.ofMinutes(5), IP))
                .thenReturn(new TokenEmitido(TICKET, Instant.now().plusSeconds(300)));

        ValidarCodigoResponseDTO r = service.validarCodigo(dto("  Ana@X.com ", "TAINAH", "004812"), IP);

        assertTrue(r.valido());
        assertEquals(TICKET, r.ticket());
        assertEquals("Ana Souza", r.nomeUsuario());
        assertEquals("Consultório Tainah", r.clinicaNome());
        assertEquals("tainah", r.clinicaCodigo());
        verify(authTokenService, never()).simularConferencia(any());
    }

    @Test
    void codigoErrado_RetornaOErroPadronizado400() {
        contaExiste();
        when(authTokenService.consumirPorCodigo(any(), any(), any())).thenReturn(Optional.empty());

        ResponseStatusException ex = falha(dto("ana@x.com", "tainah", "111111"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals(RecuperacaoSenhaService.MSG_CODIGO_INVALIDO, ex.getReason());
        verify(authTokenService, never()).emitir(any(), any(), any(), any(), any());
    }

    @Test
    void todasAsFalhasTemMesmoStatusEMesmaMensagem() {
        User inativo = User.builder().id("u9").clinicaId("c1").email("off@x.com").ativo(false).build();
        Clinica clinicaInativa = Clinica.builder().id("c2").codigo("velha").nome("Velha").ativo(false).build();
        when(clinicaRepository.findByCodigo("tainah")).thenReturn(Optional.of(clinica));
        when(clinicaRepository.findByCodigo("velha")).thenReturn(Optional.of(clinicaInativa));
        when(clinicaRepository.findByCodigo("nao-existe")).thenReturn(Optional.empty());
        when(userLookupService.buscarPorEmail("ana@x.com", "c1")).thenReturn(Optional.of(user));
        when(userLookupService.buscarPorEmail("fantasma@x.com", "c1")).thenReturn(Optional.empty());
        when(userLookupService.buscarPorEmail("off@x.com", "c1")).thenReturn(Optional.of(inativo));
        when(authTokenService.consumirPorCodigo(any(), any(), any())).thenReturn(Optional.empty());

        List<ResponseStatusException> erros = List.of(
                falha(dto("ana@x.com", "tainah", "123456")),
                falha(dto("fantasma@x.com", "tainah", "123456")),
                falha(dto("ana@x.com", "velha", "123456")),
                falha(dto("ana@x.com", "nao-existe", "123456")),
                falha(dto("off@x.com", "tainah", "123456")));

        for (ResponseStatusException e : erros) {
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
            assertEquals(RecuperacaoSenhaService.MSG_CODIGO_INVALIDO, e.getReason());
        }
        verify(authTokenService, times(4)).simularConferencia("123456");
    }

    @Test
    void semCodigoDeClinica_ComVariasContasOuNenhuma_RetornaOMesmoErro() {
        when(userLookupService.buscarContasPorEmail("ana@x.com"))
                .thenReturn(List.of(user, User.builder().id("u2").clinicaId("c2").email("ana@x.com").ativo(true).build()));
        when(clinicaRepository.findAllById(any())).thenReturn(List.of(clinica,
                Clinica.builder().id("c2").codigo("zeta").nome("Zeta").ativo(true).build()));

        ResponseStatusException ambigua = falha(dto("ana@x.com", null, "123456"));
        ResponseStatusException nenhuma = falha(dto("outro@x.com", null, "123456"));

        assertEquals(RecuperacaoSenhaService.MSG_CODIGO_INVALIDO, ambigua.getReason());
        assertEquals(RecuperacaoSenhaService.MSG_CODIGO_INVALIDO, nenhuma.getReason());
        verify(authTokenService, never()).consumirPorCodigo(any(), any(), any());
    }

    @Test
    void semCodigoDeClinica_ComUmaContaSoValida() {
        when(userLookupService.buscarContasPorEmail("ana@x.com")).thenReturn(List.of(user));
        when(clinicaRepository.findAllById(any())).thenReturn(List.of(clinica));
        when(authTokenService.consumirPorCodigo("u1", TipoTokenAuthEnum.RESET_SENHA, "123456"))
                .thenReturn(Optional.of(tokenConsumido()));
        when(authTokenService.emitir(any(), any(), any(), any(), any()))
                .thenReturn(new TokenEmitido(TICKET, Instant.now().plusSeconds(300)));

        assertEquals(TICKET, service.validarCodigo(dto("ana@x.com", null, "123456"), IP).ticket());
    }

    @Test
    void limitePorIp_DezValidacoesEADecimaPrimeiraRecebe429Neutro() {
        when(clinicaRepository.findByCodigo("tainah")).thenReturn(Optional.of(clinica));
        when(userLookupService.buscarPorEmail(any(), eq("c1"))).thenReturn(Optional.empty());

        for (int i = 0; i < RecuperacaoSenhaService.MAX_VALIDACOES_POR_IP; i++) {
            assertEquals(HttpStatus.BAD_REQUEST, falha(dto("pessoa" + i + "@x.com", "tainah", "123456")).getStatusCode());
        }
        ResponseStatusException ex = falha(dto("pessoa99@x.com", "tainah", "123456"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
        assertEquals(RecuperacaoSenhaService.MSG_LIMITE_CODIGO, ex.getReason());
    }

    @Test
    void limitePorEmail_CincoValidacoesEASextaRecebe429MesmoDeIpsDiferentes() {
        when(clinicaRepository.findByCodigo("tainah")).thenReturn(Optional.of(clinica));
        when(userLookupService.buscarPorEmail(any(), eq("c1"))).thenReturn(Optional.empty());

        for (int i = 0; i < RecuperacaoSenhaService.MAX_VALIDACOES_POR_EMAIL; i++) {
            final int n = i;
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                    () -> service.validarCodigo(dto("alvo@x.com", "tainah", "123456"), "10.9.9." + n)).getStatusCode());
        }
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.validarCodigo(dto("alvo@x.com", "tainah", "123456"), "10.9.9.200"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
        assertEquals(RecuperacaoSenhaService.MSG_LIMITE_CODIGO, ex.getReason());
        verify(authTokenService, times(RecuperacaoSenhaService.MAX_VALIDACOES_POR_EMAIL)).simularConferencia(any());
    }

    @Test
    void limite429_ValeIgualParaEmailExistenteEInexistente() {
        when(clinicaRepository.findByCodigo("tainah")).thenReturn(Optional.of(clinica));
        when(userLookupService.buscarPorEmail("ana@x.com", "c1")).thenReturn(Optional.of(user));
        when(userLookupService.buscarPorEmail("fantasma@x.com", "c1")).thenReturn(Optional.empty());
        when(authTokenService.consumirPorCodigo(any(), any(), any())).thenReturn(Optional.empty());

        for (int i = 0; i < RecuperacaoSenhaService.MAX_VALIDACOES_POR_EMAIL; i++) {
            final int n = i;
            assertThrows(ResponseStatusException.class, () -> service.validarCodigo(dto("ana@x.com", "tainah", "123456"), "10.8.8." + n));
            assertThrows(ResponseStatusException.class, () -> service.validarCodigo(dto("fantasma@x.com", "tainah", "123456"), "10.7.7." + n));
        }

        ResponseStatusException real = assertThrows(ResponseStatusException.class,
                () -> service.validarCodigo(dto("ana@x.com", "tainah", "123456"), "10.8.8.100"));
        ResponseStatusException fantasma = assertThrows(ResponseStatusException.class,
                () -> service.validarCodigo(dto("fantasma@x.com", "tainah", "123456"), "10.7.7.100"));
        assertEquals(real.getStatusCode(), fantasma.getStatusCode());
        assertEquals(real.getReason(), fantasma.getReason());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, real.getStatusCode());
    }

    @Test
    void pisoDeTempo_FalhaESucessoLevamPeloMenosOTempoMinimo() {
        ReflectionTestUtils.setField(service, "tempoMinimoValidacaoMs", 120L);
        contaExiste();
        when(authTokenService.consumirPorCodigo(any(), any(), eq("111111"))).thenReturn(Optional.empty());
        when(authTokenService.consumirPorCodigo(any(), any(), eq("222222"))).thenReturn(Optional.of(tokenConsumido()));
        when(authTokenService.emitir(any(), any(), any(), any(), any()))
                .thenReturn(new TokenEmitido(TICKET, Instant.now().plusSeconds(300)));

        long t0 = System.nanoTime();
        falha(dto("ana@x.com", "tainah", "111111"));
        long errado = (System.nanoTime() - t0) / 1_000_000;
        t0 = System.nanoTime();
        falha(dto("fantasma@x.com", "tainah", "111111"));
        long inexistente = (System.nanoTime() - t0) / 1_000_000;
        t0 = System.nanoTime();
        service.validarCodigo(dto("ana@x.com", "tainah", "222222"), IP);
        long certo = (System.nanoTime() - t0) / 1_000_000;

        assertTrue(errado >= 115, "errado: " + errado);
        assertTrue(inexistente >= 115, "inexistente: " + inexistente);
        assertTrue(certo >= 115, "certo: " + certo);
    }

    @Test
    void solicitar_EmailTrazOCodigoFormatadoEmHtmlETextoEOAssuntoContinuaEstatico() {
        contaExiste();
        when(authTokenService.emitirComCodigo(eq("u1"), eq("c1"), eq(TipoTokenAuthEnum.RESET_SENHA), any(), any(), any()))
                .thenReturn(new CodigoEmitido("TOKEN", "004812", Instant.now().plusSeconds(1800)));
        RecuperarSenhaDTO pedido = new RecuperarSenhaDTO();
        pedido.setEmail("ana@x.com");
        pedido.setClinicaCodigo("tainah");

        service.solicitar(pedido, IP);

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviar(eq("ana@x.com"), eq("Redefinir sua senha no Bokka"), html.capture(), texto.capture(), eq("reset-senha"));
        assertTrue(html.getValue().contains("004 812"));
        assertTrue(texto.getValue().contains("\n004 812\n"));
        assertTrue(html.getValue().contains("15 minutos") && html.getValue().contains("30 minutos"));
        assertFalse(html.getValue().contains("${"));
        assertFalse(texto.getValue().contains("${"));
        assertFalse(html.getValue().contains("display:none"), "sem preheader escondido");
        assertFalse(html.getValue().contains("<img"));
        assertFalse(html.getValue().contains("text-transform:uppercase"));
        assertTrue(html.getValue().contains("https://bokka.com.br/redefinir-senha?token=TOKEN&amp;c=tainah"), "o link continua no e-mail");
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456", "000000", "004812"})
    void validacaoDoDto_AceitaSeisDigitosInclusiveComZerosAEsquerda(String codigo) {
        assertTrue(VALIDATOR.validate(dto("ana@x.com", null, codigo)).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "1234567", "12a456", "123 456", "123-456", " 123456", "", "abcdef"})
    void validacaoDoDto_RejeitaQualquerCoisaQueNaoSejaSeisDigitos(String codigo) {
        assertFalse(VALIDATOR.validate(dto("ana@x.com", null, codigo)).isEmpty(), codigo);
    }

    @Test
    void validacaoDoDto_EmailObrigatorioEValido_ToStringNaoVazaOCodigo() {
        assertFalse(VALIDATOR.validate(dto("", null, "123456")).isEmpty());
        assertFalse(VALIDATOR.validate(dto("nao-e-email", null, "123456")).isEmpty());
        assertFalse(dto("ana@x.com", "tainah", "987654").toString().contains("987654"));
    }
}

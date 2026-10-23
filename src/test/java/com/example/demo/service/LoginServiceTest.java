package com.example.demo.service;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.example.demo.dto.AuthResponseDTO;
import com.example.demo.dto.LoginRequestDTO;
import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.SelecaoClinicaDTO;
import com.example.demo.enums.StatusContaEnum;
import com.example.demo.model.Clinica;
import com.example.demo.model.User;
import com.example.demo.repository.ClinicaRepository;
import com.example.demo.repository.UserRepository;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    private static final String IP = "10.0.0.1";
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final String HASH = ENCODER.encode("segredo123");

    @Mock
    private UserRepository userRepository;
    @Mock
    private ClinicaRepository clinicaRepository;
    @Mock
    private UserLookupService userLookupService;
    @Mock
    private TokenService tokenService;
    @Spy
    private RateLimitService rateLimitService = new RateLimitService();

    @InjectMocks
    private LoginService service;

    private Clinica clinica(String id, String codigo, String nome, boolean ativa) {
        return Clinica.builder().id(id).codigo(codigo).nome(nome).ativo(ativa).build();
    }

    private User user(String id, String clinicaId, String email) {
        return User.builder().id(id).clinicaId(clinicaId).email(email).name("Ana").password(HASH).ativo(true).build();
    }

    private LoginRequestDTO dto(String codigo, String email, String senha) {
        LoginRequestDTO d = new LoginRequestDTO();
        d.setClinicaCodigo(codigo);
        d.setEmail(email);
        d.setPassword(senha);
        return d;
    }

    private ResponseStatusException falha(HttpStatus status, org.junit.jupiter.api.function.Executable acao) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, acao);
        assertEquals(status, ex.getStatusCode());
        return ex;
    }

    // ───────────── com código da clínica (fluxo original, idêntico) ─────────────

    @Test
    void comCodigo_SucessoDevolveTokenEEmailConfirmado() {
        Clinica c = clinica("c1", "tainah", "Tainah", true);
        User u = user("u1", "c1", "ana@x.com");
        when(clinicaRepository.findByCodigo("tainah")).thenReturn(Optional.of(c));
        when(userLookupService.buscarPorEmail("ana@x.com", "c1")).thenReturn(Optional.of(u));
        when(tokenService.generateToken(u, "tainah", "Tainah")).thenReturn("jwt");

        AuthResponseDTO r = (AuthResponseDTO) service.login(dto("TAINAH", "ana@x.com", "segredo123"), IP);

        assertEquals("jwt", r.getToken());
        assertTrue(r.isEmailConfirmado(), "usuário legado sem o campo conta como confirmado");
    }

    @Test
    void comCodigo_DonoNaoConfirmadoVemComEmailConfirmadoFalse() {
        Clinica c = clinica("c1", "tainah", "Tainah", true);
        User u = user("u1", "c1", "ana@x.com");
        u.setEmailConfirmado(false);
        when(clinicaRepository.findByCodigo("tainah")).thenReturn(Optional.of(c));
        when(userLookupService.buscarPorEmail("ana@x.com", "c1")).thenReturn(Optional.of(u));
        when(tokenService.generateToken(u, "tainah", "Tainah")).thenReturn("jwt");

        AuthResponseDTO r = (AuthResponseDTO) service.login(dto("tainah", "ana@x.com", "segredo123"), IP);

        assertFalse(r.isEmailConfirmado());
    }

    @Test
    void comCodigo_MensagensOriginaisContinuamIguais() {
        when(clinicaRepository.findByCodigo("nao-existe")).thenReturn(Optional.empty());
        assertEquals("Clínica não encontrada.",
                falha(HttpStatus.UNAUTHORIZED, () -> service.login(dto("nao-existe", "ana@x.com", "x"), IP)).getReason());

        Clinica c = clinica("c1", "tainah", "Tainah", true);
        when(clinicaRepository.findByCodigo("tainah")).thenReturn(Optional.of(c));
        when(userLookupService.buscarPorEmail("ana@x.com", "c1")).thenReturn(Optional.empty());
        assertEquals("Credenciais inválidas.",
                falha(HttpStatus.UNAUTHORIZED, () -> service.login(dto("tainah", "ana@x.com", "x"), IP)).getReason());

        when(userLookupService.buscarPorEmail("ana@x.com", "c1")).thenReturn(Optional.of(user("u1", "c1", "ana@x.com")));
        assertEquals("Credenciais inválidas.",
                falha(HttpStatus.UNAUTHORIZED, () -> service.login(dto("tainah", "ana@x.com", "errada"), IP)).getReason());
    }

    @Test
    void comCodigo_ClinicaInativaUsuarioInativoEContaBloqueadaNaoEntram() {
        when(clinicaRepository.findByCodigo("velha")).thenReturn(Optional.of(clinica("c0", "velha", "Velha", false)));
        falha(HttpStatus.UNAUTHORIZED, () -> service.login(dto("velha", "ana@x.com", "segredo123"), IP));

        Clinica c = clinica("c1", "tainah", "Tainah", true);
        when(clinicaRepository.findByCodigo("tainah")).thenReturn(Optional.of(c));
        User inativo = user("u1", "c1", "ana@x.com");
        inativo.setAtivo(false);
        when(userLookupService.buscarPorEmail("ana@x.com", "c1")).thenReturn(Optional.of(inativo));
        falha(HttpStatus.UNAUTHORIZED, () -> service.login(dto("tainah", "ana@x.com", "segredo123"), IP));

        User bloqueado = user("u2", "c1", "ana@x.com");
        bloqueado.setStatusConta(StatusContaEnum.BLOQUEADO);
        when(userLookupService.buscarPorEmail("ana@x.com", "c1")).thenReturn(Optional.of(bloqueado));
        falha(HttpStatus.UNAUTHORIZED, () -> service.login(dto("tainah", "ana@x.com", "segredo123"), IP));
    }

    @Test
    void comCodigo_ContaPendenteSemSenhaNaoEntraENaoQuebra() {
        Clinica c = clinica("c1", "tainah", "Tainah", true);
        User pendente = user("u1", "c1", "ana@x.com");
        pendente.setPassword(null);
        pendente.setStatusConta(StatusContaEnum.PENDENTE_ATIVACAO);
        when(clinicaRepository.findByCodigo("tainah")).thenReturn(Optional.of(c));
        when(userLookupService.buscarPorEmail("ana@x.com", "c1")).thenReturn(Optional.of(pendente));

        falha(HttpStatus.UNAUTHORIZED, () -> service.login(dto("tainah", "ana@x.com", "qualquer"), IP));
    }

    // ───────────── sem código da clínica ─────────────

    @Test
    void semCodigo_UmaContaComSenhaCertaEntraDireto() {
        User u = user("u1", "c1", "ana@x.com");
        when(userLookupService.buscarContasPorEmail("ana@x.com")).thenReturn(List.of(u));
        when(clinicaRepository.findAllById(List.of("c1"))).thenReturn(List.of(clinica("c1", "tainah", "Tainah", true)));
        when(tokenService.generateToken(u, "tainah", "Tainah")).thenReturn("jwt");

        LoginResponse r = service.login(dto(null, "Ana@X.com", "segredo123"), IP);

        assertEquals("jwt", ((AuthResponseDTO) r).getToken());
    }

    @Test
    void semCodigo_CodigoEmBrancoEquivaleAAusente() {
        User u = user("u1", "c1", "ana@x.com");
        when(userLookupService.buscarContasPorEmail("ana@x.com")).thenReturn(List.of(u));
        when(clinicaRepository.findAllById(List.of("c1"))).thenReturn(List.of(clinica("c1", "tainah", "Tainah", true)));
        when(tokenService.generateToken(u, "tainah", "Tainah")).thenReturn("jwt");

        assertInstanceOf(AuthResponseDTO.class, service.login(dto("   ", "ana@x.com", "segredo123"), IP));
        verify(clinicaRepository, never()).findByCodigo(any());
    }

    @Test
    void semCodigo_SenhaErradaE401GenericoSemRevelarClinicas() {
        List<User> contas = List.of(user("u1", "c1", "ana@x.com"), user("u2", "c2", "ana@x.com"));
        when(userLookupService.buscarContasPorEmail("ana@x.com")).thenReturn(contas);
        when(clinicaRepository.findAllById(any())).thenReturn(List.of(
                clinica("c1", "a", "Clínica A", true), clinica("c2", "b", "Clínica B", true)));

        ResponseStatusException ex = falha(HttpStatus.UNAUTHORIZED, () -> service.login(dto(null, "ana@x.com", "errada"), IP));

        assertEquals("Credenciais inválidas.", ex.getReason());
        assertFalse(ex.getReason().contains("Clínica"));
        verify(tokenService, never()).generateTicketSelecaoClinica(any());
    }

    @Test
    void semCodigo_EmailInexistenteDaMesmaRespostaDaSenhaErrada() {
        when(userLookupService.buscarContasPorEmail("fantasma@x.com")).thenReturn(List.of());

        ResponseStatusException ex = falha(HttpStatus.UNAUTHORIZED, () -> service.login(dto(null, "fantasma@x.com", "qualquer"), IP));

        assertEquals("Credenciais inválidas.", ex.getReason());
    }

    @Test
    void semCodigo_EmailInexistenteGastaOMesmoTempoDeBcryptQueSenhaErrada() {
        when(userLookupService.buscarContasPorEmail("fantasma@x.com")).thenReturn(List.of());
        long inicio = System.nanoTime();
        falha(HttpStatus.UNAUTHORIZED, () -> service.login(dto(null, "fantasma@x.com", "qualquer"), IP));
        long ms = (System.nanoTime() - inicio) / 1_000_000;

        assertTrue(ms >= 20, "sem BCrypt dummy a resposta seria quase instantânea (" + ms + " ms)");
    }

    @Test
    void semCodigo_MesmaSenhaEmDuasClinicasDevolveSelecaoComTicketDe5MinSoDepoisDeCasar() {
        List<User> contas = List.of(user("u1", "c1", "ana@x.com"), user("u2", "c2", "ana@x.com"));
        when(userLookupService.buscarContasPorEmail("ana@x.com")).thenReturn(contas);
        when(clinicaRepository.findAllById(any())).thenReturn(List.of(
                clinica("c2", "zeta", "Zeta Odonto", true), clinica("c1", "alfa", "alfa Odonto", true)));
        when(tokenService.generateTicketSelecaoClinica(List.of("u1", "u2"))).thenReturn("ticket-assinado");

        SelecaoClinicaDTO r = (SelecaoClinicaDTO) service.login(dto(null, "ana@x.com", "segredo123"), IP);

        assertEquals("SELECAO_CLINICA", r.status());
        assertEquals("ticket-assinado", r.ticket());
        assertEquals(List.of(
                new SelecaoClinicaDTO.ClinicaOpcao("c1", "alfa", "alfa Odonto"),
                new SelecaoClinicaDTO.ClinicaOpcao("c2", "zeta", "Zeta Odonto")), r.clinicas());
        verify(tokenService, never()).generateToken(any(), any(), any());
    }

    @Test
    void semCodigo_SenhaCasandoEmApenasUmaDasClinicasEntraDiretoSemSelecao() {
        User outraSenha = user("u2", "c2", "ana@x.com");
        outraSenha.setPassword(ENCODER.encode("outra-senha"));
        User certa = user("u1", "c1", "ana@x.com");
        when(userLookupService.buscarContasPorEmail("ana@x.com")).thenReturn(List.of(outraSenha, certa));
        when(clinicaRepository.findAllById(any())).thenReturn(List.of(
                clinica("c1", "alfa", "Alfa", true), clinica("c2", "zeta", "Zeta", true)));
        when(tokenService.generateToken(certa, "alfa", "Alfa")).thenReturn("jwt-alfa");

        LoginResponse r = service.login(dto(null, "ana@x.com", "segredo123"), IP);

        assertEquals("jwt-alfa", ((AuthResponseDTO) r).getToken());
    }

    @Test
    void semCodigo_ContasInativasEClinicasInativasSaoIgnoradas() {
        User inativo = user("u1", "c1", "ana@x.com");
        inativo.setAtivo(false);
        User deClinicaInativa = user("u2", "c2", "ana@x.com");
        when(userLookupService.buscarContasPorEmail("ana@x.com")).thenReturn(List.of(inativo, deClinicaInativa));
        when(clinicaRepository.findAllById(any())).thenReturn(List.of(
                clinica("c1", "alfa", "Alfa", true), clinica("c2", "zeta", "Zeta", false)));

        falha(HttpStatus.UNAUTHORIZED, () -> service.login(dto(null, "ana@x.com", "segredo123"), IP));
    }

    // ───────────── rate limit ─────────────

    @Test
    void rateLimit_DezFalhasDoMesmoEmailBloqueiamAteComASenhaCerta() {
        when(userLookupService.buscarContasPorEmail("ana@x.com")).thenReturn(List.of());
        for (int i = 0; i < LoginService.MAX_FALHAS_POR_EMAIL; i++) {
            String ip = "10.1.1." + i;
            falha(HttpStatus.UNAUTHORIZED, () -> service.login(dto(null, "ana@x.com", "errada"), ip));
        }

        ResponseStatusException ex = falha(HttpStatus.TOO_MANY_REQUESTS,
                () -> service.login(dto(null, "ana@x.com", "segredo123"), "10.9.9.9"));

        assertEquals(LoginService.MSG_LIMITE, ex.getReason());
    }

    @Test
    void rateLimit_FalhasDeOutroEmailNaoBloqueiamEsteEmail() {
        User u = user("u1", "c1", "ana@x.com");
        when(userLookupService.buscarContasPorEmail("outro@x.com")).thenReturn(List.of());
        when(userLookupService.buscarContasPorEmail("ana@x.com")).thenReturn(List.of(u));
        when(clinicaRepository.findAllById(any())).thenReturn(List.of(clinica("c1", "tainah", "Tainah", true)));
        when(tokenService.generateToken(any(), any(), any())).thenReturn("jwt");
        for (int i = 0; i < LoginService.MAX_FALHAS_POR_EMAIL; i++) {
            String ip = "10.2.2." + i;
            falha(HttpStatus.UNAUTHORIZED, () -> service.login(dto(null, "outro@x.com", "errada"), ip));
        }

        assertInstanceOf(AuthResponseDTO.class, service.login(dto(null, "ana@x.com", "segredo123"), "10.8.8.8"));
    }

    @Test
    void rateLimit_LoginBemSucedidoZeraOContadorDoEmail() {
        User u = user("u1", "c1", "ana@x.com");
        when(userLookupService.buscarContasPorEmail("ana@x.com")).thenReturn(List.of(u));
        when(clinicaRepository.findAllById(any())).thenReturn(List.of(clinica("c1", "tainah", "Tainah", true)));
        when(tokenService.generateToken(any(), any(), any())).thenReturn("jwt");
        for (int rodada = 0; rodada < 3; rodada++) {
            for (int i = 0; i < LoginService.MAX_FALHAS_POR_EMAIL - 1; i++) {
                String ip = "10.3." + rodada + "." + i;
                falha(HttpStatus.UNAUTHORIZED, () -> service.login(dto(null, "ana@x.com", "errada"), ip));
            }
            assertInstanceOf(AuthResponseDTO.class, service.login(dto(null, "ana@x.com", "segredo123"), "10.3.0.250"));
        }
    }

    @Test
    void rateLimit_FalhasDemaisDoMesmoIpBloqueiamQualquerEmail() {
        when(userLookupService.buscarContasPorEmail(any())).thenReturn(List.of());
        for (int i = 0; i < LoginService.MAX_FALHAS_POR_IP; i++) {
            String email = "alvo" + i + "@x.com";
            falha(HttpStatus.UNAUTHORIZED, () -> service.login(dto(null, email, "errada"), "10.4.4.4"));
        }

        falha(HttpStatus.TOO_MANY_REQUESTS, () -> service.login(dto(null, "novo@x.com", "errada"), "10.4.4.4"));
        when(userLookupService.buscarContasPorEmail("novo@x.com")).thenReturn(List.of());
        falha(HttpStatus.UNAUTHORIZED, () -> service.login(dto(null, "novo@x.com", "errada"), "10.5.5.5"));
    }

    // ───────────── seleção de clínica ─────────────

    @Test
    void selecionarClinica_TicketValidoEmiteTokenDaClinicaEscolhida() {
        User u1 = user("u1", "c1", "ana@x.com");
        User u2 = user("u2", "c2", "ana@x.com");
        when(tokenService.validarTicketSelecaoClinica("ticket")).thenReturn(List.of("u1", "u2"));
        when(userRepository.findAllById(List.of("u1", "u2"))).thenReturn(List.of(u1, u2));
        when(clinicaRepository.findById("c2")).thenReturn(Optional.of(clinica("c2", "zeta", "Zeta", true)));
        when(tokenService.generateToken(u2, "zeta", "Zeta")).thenReturn("jwt-zeta");

        AuthResponseDTO r = service.selecionarClinica("ticket", "c2", IP);

        assertEquals("jwt-zeta", r.getToken());
    }

    @Test
    void selecionarClinica_TicketInvalidoOuExpiradoRetorna401() {
        when(tokenService.validarTicketSelecaoClinica("ruim")).thenThrow(new JWTVerificationException("expirado"));

        ResponseStatusException ex = falha(HttpStatus.UNAUTHORIZED, () -> service.selecionarClinica("ruim", "c1", IP));

        assertEquals(LoginService.MSG_TICKET, ex.getReason());
    }

    @Test
    void selecionarClinica_ClinicaForaDoTicketRetorna401() {
        when(tokenService.validarTicketSelecaoClinica("ticket")).thenReturn(List.of("u1"));
        when(userRepository.findAllById(List.of("u1"))).thenReturn(List.of(user("u1", "c1", "ana@x.com")));

        falha(HttpStatus.UNAUTHORIZED, () -> service.selecionarClinica("ticket", "c-de-outra-pessoa", IP));

        verify(tokenService, never()).generateToken(any(), any(), any());
    }

    @Test
    void selecionarClinica_UsuarioDesativadoDepoisDoTicketNaoEntra() {
        User u1 = user("u1", "c1", "ana@x.com");
        u1.setAtivo(false);
        when(tokenService.validarTicketSelecaoClinica("ticket")).thenReturn(List.of("u1"));
        when(userRepository.findAllById(List.of("u1"))).thenReturn(List.of(u1));

        falha(HttpStatus.UNAUTHORIZED, () -> service.selecionarClinica("ticket", "c1", IP));
    }

    @Test
    void selecionarClinica_TentativasInvalidasDemaisDoMesmoIpDao429() {
        when(tokenService.validarTicketSelecaoClinica(any())).thenThrow(new JWTVerificationException("x"));
        for (int i = 0; i < LoginService.MAX_FALHAS_POR_IP; i++) {
            falha(HttpStatus.UNAUTHORIZED, () -> service.selecionarClinica("ruim", "c1", "10.6.6.6"));
        }

        falha(HttpStatus.TOO_MANY_REQUESTS, () -> service.selecionarClinica("ruim", "c1", "10.6.6.6"));
    }

    @Test
    void selecionarClinica_CapturaOsIdsDoTicketNaConsultaDoUsuario() {
        when(tokenService.validarTicketSelecaoClinica("t")).thenReturn(List.of("u1"));
        when(userRepository.findAllById(any())).thenReturn(List.of());

        falha(HttpStatus.UNAUTHORIZED, () -> service.selecionarClinica("t", "c1", IP));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<String>> ids = ArgumentCaptor.forClass(Iterable.class);
        verify(userRepository).findAllById(ids.capture());
        assertEquals(List.of("u1"), ids.getValue());
    }
}

package com.example.demo.controller;

import com.example.demo.dto.AtualizarUsuarioDTO;
import com.example.demo.dto.AuthResponseDTO;
import com.example.demo.dto.CriarUsuarioDTO;
import com.example.demo.dto.LoginRequestDTO;
import com.example.demo.dto.RegisterClinicaRequestDTO;
import com.example.demo.dto.UsuarioDTO;
import com.example.demo.enums.RoleEnum;
import com.example.demo.model.Clinica;
import com.example.demo.model.User;
import com.example.demo.repository.ClinicaRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.TenantTestSupport;
import com.example.demo.service.TokenService;
import com.example.demo.service.UserLookupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static com.example.demo.service.TenantTestSupport.TEST_CLINICA_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailUsuarioTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserLookupService userLookupService;
    @Mock
    private ClinicaRepository clinicaRepository;
    @Mock
    private TokenService tokenService;

    @InjectMocks
    private UsuarioController usuarioController;
    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        TenantTestSupport.loginTestUser();
    }

    @AfterEach
    void tearDown() {
        TenantTestSupport.logout();
    }

    @Test
    void criarUsuario_GravaEmailTrimEMinusculoEChecaDuplicidadeComEleNormalizado() {
        when(userLookupService.emailEmUso("novo@x.com", TEST_CLINICA_ID)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        CriarUsuarioDTO dto = new CriarUsuarioDTO();
        dto.setName("Novo");
        dto.setEmail("  Novo@X.COM ");
        dto.setPassword("123456");
        dto.setRole(RoleEnum.RECEPCIONISTA);

        UsuarioDTO criado = usuarioController.criar(dto).getBody();

        assertEquals("novo@x.com", criado.getEmail());
        ArgumentCaptor<User> salvo = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(salvo.capture());
        assertEquals("novo@x.com", salvo.getValue().getEmail());
    }

    @Test
    void criarUsuario_EmailJaUsadoIgnorandoCaixaRetorna400() {
        when(userLookupService.emailEmUso("dup@x.com", TEST_CLINICA_ID)).thenReturn(true);
        CriarUsuarioDTO dto = new CriarUsuarioDTO();
        dto.setEmail("DUP@x.com");
        dto.setRole(RoleEnum.RECEPCIONISTA);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> usuarioController.criar(dto));

        assertEquals("E-mail já cadastrado nesta clínica.", ex.getReason());
        verify(userRepository, never()).save(any());
    }

    @Test
    void atualizarUsuario_NormalizaEmailLegadoComMaiusculaSemExigirChecagemDeDuplicidade() {
        User legado = User.builder().id("u2").clinicaId(TEST_CLINICA_ID).name("Ana").email("Ana@X.com")
                .role(RoleEnum.RECEPCIONISTA).ativo(true).build();
        when(userRepository.findById("u2")).thenReturn(Optional.of(legado));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        AtualizarUsuarioDTO dto = new AtualizarUsuarioDTO();
        dto.setName("Ana");
        dto.setEmail("Ana@X.com");
        dto.setRole(RoleEnum.RECEPCIONISTA);

        usuarioController.atualizar("u2", dto);

        assertEquals("ana@x.com", legado.getEmail());
        verify(userLookupService, never()).emailEmUso(any(), any());
    }

    @Test
    void login_UsaBuscaTolerantePorEmailEMinusculo() {
        Clinica clinica = Clinica.builder().id(TEST_CLINICA_ID).codigo("minha-clinica").ativo(true).build();
        when(clinicaRepository.findByCodigo("minha-clinica")).thenReturn(Optional.of(clinica));
        User legado = User.builder().id("u3").clinicaId(TEST_CLINICA_ID).email("Ana@X.com")
                .password(new BCryptPasswordEncoder().encode("segredo")).ativo(true).build();
        when(userLookupService.buscarPorEmail("ANA@x.com", TEST_CLINICA_ID)).thenReturn(Optional.of(legado));
        when(tokenService.generateToken(legado, "minha-clinica")).thenReturn("jwt");
        LoginRequestDTO dto = new LoginRequestDTO();
        dto.setClinicaCodigo("MINHA-clinica");
        dto.setEmail("ANA@x.com");
        dto.setPassword("segredo");

        AuthResponseDTO resposta = authController.login(dto).getBody();

        assertEquals("jwt", resposta.getToken());
    }

    @Test
    void login_EmailNaoEncontradoRetorna401() {
        Clinica clinica = Clinica.builder().id(TEST_CLINICA_ID).codigo("minha-clinica").ativo(true).build();
        when(clinicaRepository.findByCodigo("minha-clinica")).thenReturn(Optional.of(clinica));
        when(userLookupService.buscarPorEmail("x@x.com", TEST_CLINICA_ID)).thenReturn(Optional.empty());
        LoginRequestDTO dto = new LoginRequestDTO();
        dto.setClinicaCodigo("minha-clinica");
        dto.setEmail("x@x.com");
        dto.setPassword("segredo");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> authController.login(dto));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void registerClinica_NormalizaEmailDoProprietarioEDadosDaClinica() {
        when(clinicaRepository.existsByCodigo("nova-clinica")).thenReturn(false);
        when(clinicaRepository.save(any(Clinica.class))).thenAnswer(i -> {
            Clinica c = i.getArgument(0);
            c.setId("c9");
            return c;
        });
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(tokenService.generateToken(any(User.class), any())).thenReturn("jwt");
        RegisterClinicaRequestDTO dto = new RegisterClinicaRequestDTO();
        dto.setClinicaCodigo("nova-clinica");
        dto.setClinicaNome("Nova");
        dto.setCnpj("11222333000181");
        dto.setTelefone("08001234567");
        dto.setEmailContato(" Contato@Nova.COM ");
        dto.setAdminNome("Dono");
        dto.setAdminEmail("  Dono@Nova.COM ");
        dto.setAdminPassword("123456");

        authController.registerClinica(dto);

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(user.capture());
        assertEquals("dono@nova.com", user.getValue().getEmail());
        ArgumentCaptor<Clinica> clinica = ArgumentCaptor.forClass(Clinica.class);
        verify(clinicaRepository).save(clinica.capture());
        assertEquals("11.222.333/0001-81", clinica.getValue().getCnpj());
        assertEquals("0800 123 4567", clinica.getValue().getTelefone());
        assertEquals("contato@nova.com", clinica.getValue().getEmailContato());
    }
}

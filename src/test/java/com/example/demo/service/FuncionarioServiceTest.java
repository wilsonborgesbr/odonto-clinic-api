package com.example.demo.service;

import com.example.demo.model.Funcionario;
import com.example.demo.model.Paciente;
import com.example.demo.repository.FuncionarioRepository;
import com.example.demo.repository.PacienteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static com.example.demo.service.TenantTestSupport.TEST_CLINICA_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FuncionarioServiceTest {

    @Mock
    private FuncionarioRepository funcionarioRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @InjectMocks
    private FuncionarioService funcionarioService;

    @InjectMocks
    private PacienteService pacienteService;

    @BeforeEach
    void setUp() {
        TenantTestSupport.loginTestUser();
    }

    @AfterEach
    void tearDown() {
        TenantTestSupport.logout();
    }

    @Test
    void criar_NormalizaCpfEmailETelefone() {
        when(funcionarioRepository.findByCpfAndClinicaId("529.982.247-25", TEST_CLINICA_ID)).thenReturn(Optional.empty());
        when(funcionarioRepository.save(any(Funcionario.class))).thenAnswer(i -> i.getArgument(0));

        Funcionario f = new Funcionario();
        f.setCpf("52998224725");
        f.setEmail(" Rec@Clinica.COM ");
        f.setTelefoneCelular("79999991234");

        Funcionario salvo = funcionarioService.criar(f);

        assertEquals("529.982.247-25", salvo.getCpf());
        assertEquals("rec@clinica.com", salvo.getEmail());
        assertEquals("(79) 99999-1234", salvo.getTelefoneCelular());
    }

    @Test
    void criar_CpfDuplicadoNaClinicaRetorna400() {
        when(funcionarioRepository.findByCpfAndClinicaId("529.982.247-25", TEST_CLINICA_ID))
                .thenReturn(Optional.of(new Funcionario()));

        Funcionario f = new Funcionario();
        f.setCpf("529.982.247-25");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> funcionarioService.criar(f));

        assertEquals("CPF já cadastrado nesta clínica.", ex.getReason());
        verify(funcionarioRepository, never()).save(any());
    }

    @Test
    void criar_CpfInvalidoRetorna400() {
        Funcionario f = new Funcionario();
        f.setCpf("123.456.789-00");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> funcionarioService.criar(f));

        assertTrue(ex.getReason().startsWith("CPF inválido"));
        verifyNoInteractions(funcionarioRepository);
    }

    @Test
    void criar_CorridaNoIndiceVira400() {
        when(funcionarioRepository.findByCpfAndClinicaId(any(), any())).thenReturn(Optional.empty());
        when(funcionarioRepository.save(any(Funcionario.class))).thenThrow(new DuplicateKeyException("E11000"));

        Funcionario f = new Funcionario();
        f.setCpf("529.982.247-25");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> funcionarioService.criar(f));

        assertEquals("CPF já cadastrado nesta clínica.", ex.getReason());
    }

    @Test
    void mesmoCpfPodeSerFuncionarioEPacienteNaMesmaClinica() {
        when(funcionarioRepository.findByCpfAndClinicaId("529.982.247-25", TEST_CLINICA_ID)).thenReturn(Optional.empty());
        when(funcionarioRepository.save(any(Funcionario.class))).thenAnswer(i -> i.getArgument(0));
        when(pacienteRepository.findByCpfAndClinicaId("529.982.247-25", TEST_CLINICA_ID)).thenReturn(Optional.empty());
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(i -> i.getArgument(0));

        Funcionario f = new Funcionario();
        f.setCpf("529.982.247-25");
        Paciente p = Paciente.builder().cpf("529.982.247-25").nomeCompleto("Maria").build();

        assertDoesNotThrow(() -> funcionarioService.criar(f));
        assertDoesNotThrow(() -> pacienteService.criar(p));
        verify(funcionarioRepository).findByCpfAndClinicaId("529.982.247-25", TEST_CLINICA_ID);
        verify(pacienteRepository).findByCpfAndClinicaId("529.982.247-25", TEST_CLINICA_ID);
        verify(funcionarioRepository, never()).findByIdAndClinicaId(any(), any());
    }

    @Test
    void atualizar_MantendoOMesmoCpfNaoConsultaDuplicidadeEReenviarCpfSemMascaraNormaliza() {
        Funcionario existente = new Funcionario();
        existente.setId("f1");
        existente.setCpf("529.982.247-25");
        existente.setAtivo(true);
        when(funcionarioRepository.findByIdAndClinicaId("f1", TEST_CLINICA_ID)).thenReturn(Optional.of(existente));
        when(funcionarioRepository.save(any(Funcionario.class))).thenAnswer(i -> i.getArgument(0));

        Funcionario dados = new Funcionario();
        dados.setCpf("52998224725");

        Funcionario atualizado = funcionarioService.atualizar("f1", dados);

        assertEquals("529.982.247-25", atualizado.getCpf());
        verify(funcionarioRepository, never()).findByCpfAndClinicaId(any(), any());
    }

    @Test
    void indiceDeCpfEUnicoPorClinicaParcial() throws NoSuchFieldException {
        CompoundIndex indice = Funcionario.class.getAnnotation(CompoundIndex.class);

        assertNotNull(indice);
        assertTrue(indice.unique());
        assertEquals("{'clinicaId': 1, 'cpf': 1}", indice.def());
        assertEquals("{'cpf': {$type: 'string', $gt: ''}}", indice.partialFilter());
        assertFalse(Funcionario.class.getDeclaredField("cpf").isAnnotationPresent(Indexed.class));
        assertEquals("funcionarios", Funcionario.class.getAnnotation(Document.class).collection());
    }
}

package com.example.demo.service;

import com.example.demo.dto.PendenciasCadastroDTO;
import com.example.demo.enums.PermissaoEnum;
import com.example.demo.enums.RoleEnum;
import com.example.demo.model.Clinica;
import com.example.demo.model.Convenio;
import com.example.demo.model.Dentista;
import com.example.demo.model.Funcionario;
import com.example.demo.model.Paciente;
import com.example.demo.model.User;
import com.example.demo.repository.ClinicaRepository;
import com.example.demo.repository.ConvenioRepository;
import com.example.demo.repository.DentistaRepository;
import com.example.demo.repository.FuncionarioRepository;
import com.example.demo.repository.PacienteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.example.demo.service.TenantTestSupport.TEST_CLINICA_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PendenciaCadastroServiceTest {

    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private FuncionarioRepository funcionarioRepository;
    @Mock
    private DentistaRepository dentistaRepository;
    @Mock
    private ConvenioRepository convenioRepository;
    @Mock
    private ClinicaRepository clinicaRepository;

    @InjectMocks
    private PendenciaCadastroService service;

    @BeforeEach
    void setUp() {
        TenantTestSupport.loginTestUser();
    }

    @AfterEach
    void tearDown() {
        TenantTestSupport.logout();
    }

    @Test
    void listar_RetornaApenasRegistrosComDadoInvalidoFiltradosPelaClinica() {
        Paciente invalido = Paciente.builder().id("p1").nomeCompleto("Maria").cpf("12345678900")
                .telefoneCelular("123").build();
        Paciente valido = Paciente.builder().id("p2").nomeCompleto("Ana").cpf("529.982.247-25").build();
        Paciente legadoSemMascaraValido = Paciente.builder().id("p3").nomeCompleto("Bia").cpf("52998224725").build();
        when(pacienteRepository.findByClinicaIdAndAtivoTrue(TEST_CLINICA_ID))
                .thenReturn(List.of(invalido, valido, legadoSemMascaraValido));
        when(funcionarioRepository.findByClinicaIdAndAtivoTrue(TEST_CLINICA_ID)).thenReturn(List.of());
        when(dentistaRepository.findByClinicaIdAndAtivoTrue(TEST_CLINICA_ID)).thenReturn(List.of());
        when(convenioRepository.findByClinicaId(TEST_CLINICA_ID)).thenReturn(List.of());
        when(clinicaRepository.findById(TEST_CLINICA_ID)).thenReturn(Optional.empty());

        PendenciasCadastroDTO resposta = service.listar();

        assertEquals(1, resposta.total());
        assertEquals(1, resposta.porEntidade().get("PACIENTE"));
        PendenciasCadastroDTO.Item item = resposta.itens().get(0);
        assertEquals("PACIENTE", item.entidade());
        assertEquals("p1", item.id());
        assertEquals("Maria", item.nome());
        assertEquals(2, item.campos().size());
        assertEquals("cpf", item.campos().get(0).campo());
        assertEquals("12345678900", item.campos().get(0).valor());
        assertTrue(item.campos().get(0).motivo().startsWith("CPF inválido"));
        assertEquals("telefoneCelular", item.campos().get(1).campo());
        verify(pacienteRepository, never()).findAll();
    }

    @Test
    void listar_CobreTodasAsEntidadesECamposVaziosNaoSaoPendencia() {
        when(pacienteRepository.findByClinicaIdAndAtivoTrue(TEST_CLINICA_ID)).thenReturn(List.of(
                Paciente.builder().id("p1").nomeCompleto("Sem telefone").cpf("529.982.247-25").build()));
        Funcionario f = new Funcionario();
        f.setId("f1");
        f.setNomeCompleto("Func");
        f.setCpf("111.111.111-11");
        when(funcionarioRepository.findByClinicaIdAndAtivoTrue(TEST_CLINICA_ID)).thenReturn(List.of(f));
        Dentista d = new Dentista();
        d.setId("d1");
        d.setNomeCompleto("Dent");
        d.setCro("CRO-XX 1");
        d.setCpf("");
        when(dentistaRepository.findByClinicaIdAndAtivoTrue(TEST_CLINICA_ID)).thenReturn(List.of(d));
        Convenio c = new Convenio();
        c.setId("c1");
        c.setNome("Conv");
        c.setCnpj("11.222.333/0001-80");
        c.setTelefone("0800 123 4567");
        Convenio inativo = new Convenio();
        inativo.setId("c2");
        inativo.setNome("Conv inativo");
        inativo.setCnpj("00");
        inativo.setAtivo(false);
        when(convenioRepository.findByClinicaId(TEST_CLINICA_ID)).thenReturn(List.of(c, inativo));
        Clinica clinica = new Clinica();
        clinica.setId(TEST_CLINICA_ID);
        clinica.setNome("Clínica");
        clinica.setCnpj("123");
        when(clinicaRepository.findById(TEST_CLINICA_ID)).thenReturn(Optional.of(clinica));

        PendenciasCadastroDTO resposta = service.listar();

        assertEquals(4, resposta.total());
        assertEquals(List.of("CLINICA", "CONVENIO", "DENTISTA", "FUNCIONARIO"),
                resposta.itens().stream().map(PendenciasCadastroDTO.Item::entidade).toList());
        PendenciasCadastroDTO.Item convenio = resposta.itens().get(1);
        assertEquals(1, convenio.campos().size());
        assertEquals("cnpj", convenio.campos().get(0).campo());
        assertEquals("cro", resposta.itens().get(2).campos().get(0).campo());
    }

    @Test
    void listar_PacienteSemCpfEPendenciaCpfNaoInformadoMasDentistaSemCpfNao() {
        Paciente semCpf = Paciente.builder().id("p1").nomeCompleto("Legado").build();
        Paciente cpfVazio = Paciente.builder().id("p2").nomeCompleto("Legado vazio").cpf("").build();
        when(pacienteRepository.findByClinicaIdAndAtivoTrue(TEST_CLINICA_ID)).thenReturn(List.of(semCpf, cpfVazio));
        Dentista d = new Dentista();
        d.setId("d1");
        d.setNomeCompleto("Dent");
        d.setCpf(null);
        when(funcionarioRepository.findByClinicaIdAndAtivoTrue(TEST_CLINICA_ID)).thenReturn(List.of());
        when(dentistaRepository.findByClinicaIdAndAtivoTrue(TEST_CLINICA_ID)).thenReturn(List.of(d));
        when(convenioRepository.findByClinicaId(TEST_CLINICA_ID)).thenReturn(List.of());
        when(clinicaRepository.findById(TEST_CLINICA_ID)).thenReturn(Optional.empty());

        PendenciasCadastroDTO resposta = service.listar();

        assertEquals(2, resposta.total());
        assertEquals(2, resposta.porEntidade().get("PACIENTE"));
        assertNull(resposta.porEntidade().get("DENTISTA"));
        PendenciasCadastroDTO.Campo campo = resposta.itens().get(0).campos().get(0);
        assertEquals("cpf", campo.campo());
        assertEquals("CPF não informado.", campo.motivo());
        assertNull(campo.valor());
        assertEquals("", resposta.itens().get(1).campos().get(0).valor());
    }

    @Test
    void listar_PacienteComResponsavelCpfInvalidoApareceNoCampoResponsavelCpf() {
        Paciente p = Paciente.builder().id("p1").nomeCompleto("Léo").cpf("529.982.247-25")
                .responsavelNome("Joana").responsavelCpf("111").build();
        autenticarCom(Set.of(PermissaoEnum.PACIENTES));
        when(pacienteRepository.findByClinicaIdAndAtivoTrue(TEST_CLINICA_ID)).thenReturn(List.of(p));

        PendenciasCadastroDTO resposta = service.listar();

        assertEquals(1, resposta.total());
        assertEquals("responsavelCpf", resposta.itens().get(0).campos().get(0).campo());
        assertEquals("111", resposta.itens().get(0).campos().get(0).valor());
    }

    @Test
    void listar_SemNenhumaPermissaoDeCadastroRetorna403() {
        autenticarCom(Set.of(PermissaoEnum.DASHBOARD, PermissaoEnum.AUDITORIA_FINANCEIRA));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.listar());

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verifyNoInteractions(pacienteRepository, funcionarioRepository, dentistaRepository, convenioRepository, clinicaRepository);
    }

    @Test
    void listar_SoConsultaOQueOUsuarioPodeVer() {
        autenticarCom(Set.of(PermissaoEnum.PACIENTES));
        when(pacienteRepository.findByClinicaIdAndAtivoTrue(TEST_CLINICA_ID)).thenReturn(List.of());

        PendenciasCadastroDTO resposta = service.listar();

        assertEquals(0, resposta.total());
        assertTrue(resposta.itens().isEmpty());
        verify(pacienteRepository).findByClinicaIdAndAtivoTrue(TEST_CLINICA_ID);
        verifyNoInteractions(funcionarioRepository, dentistaRepository, convenioRepository, clinicaRepository);
    }

    @Test
    void listar_ClinicaSoComConfiguracoes() {
        autenticarCom(Set.of(PermissaoEnum.CONFIGURACOES));
        Clinica clinica = new Clinica();
        clinica.setNome("Clínica");
        clinica.setCnpj("11.222.333/0001-80");
        when(clinicaRepository.findById(TEST_CLINICA_ID)).thenReturn(Optional.of(clinica));

        PendenciasCadastroDTO resposta = service.listar();

        assertEquals(1, resposta.total());
        assertEquals("CLINICA", resposta.itens().get(0).entidade());
        verifyNoInteractions(pacienteRepository, funcionarioRepository, dentistaRepository, convenioRepository);
    }

    private void autenticarCom(Set<PermissaoEnum> permissoes) {
        User user = User.builder().id("u").clinicaId(TEST_CLINICA_ID).role(RoleEnum.PROPRIETARIO)
                .permissoes(permissoes).ativo(true).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}

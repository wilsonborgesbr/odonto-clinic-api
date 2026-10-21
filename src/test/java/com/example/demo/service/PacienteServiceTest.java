package com.example.demo.service;

import com.example.demo.dto.PacienteListagemDTO;
import com.example.demo.dto.PacienteResumoDTO;
import com.example.demo.enums.SexoEnum;
import com.example.demo.exception.CampoInvalidoException;
import com.example.demo.model.Paciente;
import com.example.demo.repository.PacienteRepository;
import com.example.demo.util.FusoBrasil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.example.demo.service.TenantTestSupport.TEST_CLINICA_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PacienteServiceTest {

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private PacienteService pacienteService;

    @BeforeEach
    void setUp() {
        TenantTestSupport.loginTestUser();
        lenient().when(mongoTemplate.find(any(Query.class), eq(Paciente.class))).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantTestSupport.logout();
    }

    @Test
    void buscarResumos_DeveFiltrarPelaClinicaDoUsuarioERetornarApenasIdENome() {
        Paciente paciente = Paciente.builder()
                .id("p1")
                .clinicaId(TEST_CLINICA_ID)
                .nomeCompleto("Maria Silva")
                .cpf("529.982.247-25")
                .build();
        when(pacienteRepository.findByIdInAndClinicaId(any(), eq(TEST_CLINICA_ID)))
                .thenReturn(List.of(paciente));

        List<PacienteResumoDTO> resultado = pacienteService.buscarResumos(List.of("p1", "p-outra-clinica"));

        assertEquals(List.of(new PacienteResumoDTO("p1", "Maria Silva")), resultado);
        verify(pacienteRepository).findByIdInAndClinicaId(any(), eq(TEST_CLINICA_ID));
        verifyNoMoreInteractions(pacienteRepository);
    }

    @Test
    void buscarResumos_IdsDeOutraClinicaNaoVazam() {
        when(pacienteRepository.findByIdInAndClinicaId(any(), eq(TEST_CLINICA_ID)))
                .thenReturn(List.of());

        List<PacienteResumoDTO> resultado = pacienteService.buscarResumos(List.of("p-outra-clinica"));

        assertTrue(resultado.isEmpty());
        verify(pacienteRepository, never()).findByIdInAndClinicaId(any(), eq("outra-clinica"));
    }

    @Test
    void buscarResumos_IdsVaziosOuNulosRetornamListaVaziaSemConsultarBanco() {
        assertTrue(pacienteService.buscarResumos(null).isEmpty());
        assertTrue(pacienteService.buscarResumos(List.of()).isEmpty());
        assertTrue(pacienteService.buscarResumos(Arrays.asList("", " ", null)).isEmpty());

        verifyNoInteractions(pacienteRepository);
    }

    @Test
    void buscarResumos_DeveDescartarBrancosEDuplicadosAntesDeConsultar() {
        when(pacienteRepository.findByIdInAndClinicaId(any(), eq(TEST_CLINICA_ID)))
                .thenReturn(List.of());

        pacienteService.buscarResumos(Arrays.asList("a", " a ", "", "b", null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(pacienteRepository).findByIdInAndClinicaId(captor.capture(), eq(TEST_CLINICA_ID));
        assertEquals(List.of("a", "b"), List.copyOf(captor.getValue()));
    }

    @Test
    void buscarResumos_AceitaExatamenteOLimite() {
        when(pacienteRepository.findByIdInAndClinicaId(any(), eq(TEST_CLINICA_ID)))
                .thenReturn(List.of());

        assertDoesNotThrow(() -> pacienteService.buscarResumos(gerarIds(PacienteService.MAX_IDS_RESUMO)));
    }

    @Test
    void buscarResumos_AcimaDoLimiteRetorna400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> pacienteService.buscarResumos(gerarIds(PacienteService.MAX_IDS_RESUMO + 1)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(pacienteRepository);
    }

    @Test
    void listar_DeveFiltrarPorClinicaEAtivoSemTermo() {
        pacienteService.listar(null, null, PageRequest.of(0, 10));

        Document filtro = capturarQuery().getQueryObject();
        assertEquals(TEST_CLINICA_ID, filtro.get("clinicaId"));
        assertEquals(true, filtro.get("ativo"));
        assertFalse(filtro.containsKey("$or"));
        assertFalse(filtro.containsKey("createdAt"));
    }

    @Test
    void listar_BuscaPorNomeIgnoraAcentoECaixaEContemOTexto() {
        List<Pattern> nome = padroes("jose", "nomeCompleto");

        assertTrue(casa(nome, "José"));
        assertTrue(casa(nome, "Joséfa Almeida"));
        assertTrue(casa(nome, "MARIA JOSÉ"));
        assertTrue(casa(nome, "Jose Silva"));
        assertFalse(casa(nome, "Maria Silva"));
    }

    @Test
    void listar_BuscaComAcentoNoTermoEncontraNomeSemAcento() {
        List<Pattern> nome = padroes("JOSÉ", "nomeCompleto");

        assertTrue(casa(nome, "Jose"));
        assertTrue(casa(nome, "José"));
        assertTrue(casa(padroes("conceicao", "nomeCompleto"), "Maria da Conceição"));
        assertTrue(casa(padroes("joao  silva", "nomeCompleto"), "João Silva"));
    }

    @Test
    void listar_BuscaPorCpfComOuSemPontuacaoEParcial() {
        assertTrue(casa(padroes("12345678900", "cpf"), "123.456.789-00"));
        assertTrue(casa(padroes("123.456.789-00", "cpf"), "12345678900"));
        assertTrue(casa(padroes("123.456.789-00", "cpf"), "123.456.789-00"));
        assertTrue(casa(padroes("456.789", "cpf"), "123.456.789-00"));
        assertTrue(casa(padroes("789", "cpf"), "12345678900"));
        assertFalse(casa(padroes("12345678900", "cpf"), "123.456.789-01"));
        assertFalse(casa(padroes("123.789", "cpf"), "123.456.789-00"));
    }

    @Test
    void listar_TermoTextualNaoGeraCriterioDeCpf() {
        pacienteService.listar("jose", null, PageRequest.of(0, 10));

        assertTrue(padroes("jose", "cpf").isEmpty());
    }

    @Test
    void listar_BuscaPorEmailContemOTexto() {
        List<Pattern> email = padroes("maria.silva@", "email");

        assertTrue(casa(email, "maria.silva@gmail.com"));
        assertTrue(casa(email, "Maria.Silva@Gmail.com"));
        assertFalse(casa(email, "mariaXsilva@gmail.com"));
        assertTrue(casa(padroes("gmail", "email"), "ana@gmail.com"));
    }

    @Test
    void listar_CaracteresEspeciaisDoTermoNaoViramRegex() {
        assertFalse(casa(padroes(".*", "nomeCompleto"), "Maria Silva"));
        assertFalse(casa(padroes("a+(", "email"), "aaa@x.com"));
    }

    @Test
    void listar_BuscaMantemFiltroDeClinica() {
        pacienteService.listar("jose", null, PageRequest.of(0, 10));

        Document filtro = capturarQuery().getQueryObject();
        assertEquals(TEST_CLINICA_ID, filtro.get("clinicaId"));
        assertEquals(true, filtro.get("ativo"));
        assertEquals(2, ((List<?>) filtro.get("$or")).size());
    }

    @Test
    void listar_OrdenaPorNomeComColacaoPtIgnorandoAcentoECaixa() {
        pacienteService.listar(null, null, PageRequest.of(0, 10, Sort.by("nomeCompleto")));

        Query query = capturarQuery();
        assertEquals(1, query.getSortObject().get("nomeCompleto"));
        Document colacao = query.getCollation().orElseThrow().toDocument();
        assertEquals("pt", colacao.getString("locale"));
        assertEquals(1, colacao.getInteger("strength"));
    }

    @Test
    void listar_ColacaoNaoAlteraOFiltroDeBuscaNemDeClinica() {
        pacienteService.listar("jose", null, PageRequest.of(0, 10, Sort.by("nomeCompleto")));

        Query query = capturarQuery();
        assertTrue(query.getCollation().isPresent());
        assertEquals(TEST_CLINICA_ID, query.getQueryObject().get("clinicaId"));
        assertEquals(true, query.getQueryObject().get("ativo"));
        assertEquals(2, ((List<?>) query.getQueryObject().get("$or")).size());
        assertTrue(casa(padroes("jose", "nomeCompleto"), "José"));
    }

    @Test
    void listar_DesdeFiltraPorDataDeCadastroAPartirDoInicioDoDia() {
        pacienteService.listar(null, LocalDate.of(2026, 10, 6), PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")));

        Query query = capturarQuery();
        Document createdAt = (Document) query.getQueryObject().get("createdAt");
        assertEquals(LocalDateTime.of(2026, 10, 6, 0, 0), createdAt.get("$gte"));
        assertEquals(TEST_CLINICA_ID, query.getQueryObject().get("clinicaId"));
        assertEquals(-1, query.getSortObject().get("createdAt"));
    }

    @Test
    void listar_RetornaApenasCamposDaListagem() {
        Paciente paciente = Paciente.builder().id("p1").nomeCompleto("Maria").ativo(true).build();
        when(mongoTemplate.find(any(Query.class), eq(Paciente.class))).thenReturn(List.of(paciente));

        Page<PacienteListagemDTO> pagina = pacienteService.listar(null, null, PageRequest.of(0, 10));

        assertEquals(1, pagina.getTotalElements());
        assertEquals("p1", pagina.getContent().get(0).id());
        assertEquals("Maria", pagina.getContent().get(0).nomeCompleto());
    }

    @Test
    void criar_ConsultaDuplicidadeDeCpfSomenteNaClinicaDoUsuario() {
        Paciente novo = pacienteComCpf("529.982.247-25");
        when(pacienteRepository.findByCpfAndClinicaId("529.982.247-25", TEST_CLINICA_ID))
                .thenReturn(Optional.empty());
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(i -> i.getArgument(0));

        Paciente salvo = pacienteService.criar(novo);

        assertEquals(TEST_CLINICA_ID, salvo.getClinicaId());
        verify(pacienteRepository).findByCpfAndClinicaId("529.982.247-25", TEST_CLINICA_ID);
    }

    @Test
    void criar_CpfJaCadastradoNaClinicaRetorna400ComMensagemAtual() {
        when(pacienteRepository.findByCpfAndClinicaId("529.982.247-25", TEST_CLINICA_ID))
                .thenReturn(Optional.of(pacienteComCpf("529.982.247-25")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> pacienteService.criar(pacienteComCpf("529.982.247-25")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("CPF já cadastrado nesta clínica.", ex.getReason());
        verify(pacienteRepository, never()).save(any());
    }

    @Test
    void criar_CorridaNoIndiceUnicoVira400ComMesmaMensagem() {
        when(pacienteRepository.findByCpfAndClinicaId("529.982.247-25", TEST_CLINICA_ID))
                .thenReturn(Optional.empty());
        when(pacienteRepository.save(any(Paciente.class))).thenThrow(new DuplicateKeyException("E11000"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> pacienteService.criar(pacienteComCpf("529.982.247-25")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("CPF já cadastrado nesta clínica.", ex.getReason());
    }

    @Test
    void criar_CpfNuloOuVazioNaoConsultaDuplicidade() {
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(i -> i.getArgument(0));

        assertDoesNotThrow(() -> pacienteService.criar(pacienteComCpf(null)));
        assertDoesNotThrow(() -> pacienteService.criar(pacienteComCpf("")));

        verify(pacienteRepository, never()).findByCpfAndClinicaId(any(), any());
    }

    @Test
    void atualizar_TrocaParaCpfDeOutroPacienteDaClinicaRetorna400() {
        Paciente existente = pacienteComCpf("111.444.777-35");
        existente.setId("p1");
        existente.setAtivo(true);
        when(pacienteRepository.findByIdAndClinicaId("p1", TEST_CLINICA_ID)).thenReturn(Optional.of(existente));
        when(pacienteRepository.findByCpfAndClinicaId("390.533.447-05", TEST_CLINICA_ID))
                .thenReturn(Optional.of(pacienteComCpf("390.533.447-05")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> pacienteService.atualizar("p1", pacienteComCpf("390.533.447-05")));

        assertEquals("CPF já cadastrado nesta clínica.", ex.getReason());
        verify(pacienteRepository, never()).save(any());
    }

    @Test
    void atualizar_MantendoOMesmoCpfNaoConsultaDuplicidade() {
        Paciente existente = pacienteComCpf("111.444.777-35");
        existente.setId("p1");
        existente.setAtivo(true);
        when(pacienteRepository.findByIdAndClinicaId("p1", TEST_CLINICA_ID)).thenReturn(Optional.of(existente));
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(i -> i.getArgument(0));

        assertDoesNotThrow(() -> pacienteService.atualizar("p1", pacienteComCpf("111.444.777-35")));

        verify(pacienteRepository, never()).findByCpfAndClinicaId(any(), any());
    }

    @Test
    void indiceDeCpfEUnicoPorClinicaEIgnoraCpfVazio() throws NoSuchFieldException {
        CompoundIndex indice = Paciente.class.getAnnotation(CompoundIndex.class);

        assertNotNull(indice);
        assertTrue(indice.unique());
        assertEquals("{'clinicaId': 1, 'cpf': 1}", indice.def());
        assertEquals("{'cpf': {$gt: ''}}", indice.partialFilter());
        assertFalse(Paciente.class.getDeclaredField("cpf").isAnnotationPresent(Indexed.class));
    }

    @Test
    void criar_GravaTodosOsCamposNoFormatoCanonico() {
        when(pacienteRepository.findByCpfAndClinicaId("529.982.247-25", TEST_CLINICA_ID)).thenReturn(Optional.empty());
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(i -> i.getArgument(0));
        Paciente novo = pacienteComCpf("52998224725");
        novo.setEmail("  Maria@Mail.COM ");
        novo.setTelefoneCelular("79999991234");
        novo.setTelefoneFixo("7932221234");
        novo.setTelefoneEmergencia("");

        Paciente salvo = pacienteService.criar(novo);

        assertEquals("529.982.247-25", salvo.getCpf());
        assertEquals("maria@mail.com", salvo.getEmail());
        assertEquals("(79) 99999-1234", salvo.getTelefoneCelular());
        assertEquals("(79) 3222-1234", salvo.getTelefoneFixo());
        assertNull(salvo.getTelefoneEmergencia());
    }

    @Test
    void criar_CpfComDigitoVerificadorInvalidoRetorna400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> pacienteService.criar(pacienteComCpf("123.456.789-00")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().startsWith("CPF inválido"));
        verifyNoInteractions(pacienteRepository);
    }

    @Test
    void atualizar_ReenviarCpfInvalidoLegadoPedeCorrecao() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> pacienteService.atualizar("p1", pacienteComCpf("123.456.789-00")));

        assertTrue(ex.getReason().startsWith("CPF inválido"));
        verify(pacienteRepository, never()).save(any());
    }

    private Map<String, String> violacoes(Paciente paciente) {
        try (ValidatorFactory fabrica = Validation.buildDefaultValidatorFactory()) {
            return fabrica.getValidator().validate(paciente).stream().collect(Collectors.toMap(
                    v -> v.getPropertyPath().toString(), ConstraintViolation::getMessage));
        }
    }

    private Paciente pacienteValido() {
        return Paciente.builder().nomeCompleto("Maria Silva").cpf("529.982.247-25").sexo(SexoEnum.values()[0])
                .tipoPaciente(Paciente.TipoPaciente.PARTICULAR).build();
    }

    @Test
    void validacao_CpfContinuaObrigatorioComMensagemNoCampoCpf() {
        Paciente semCpf = pacienteValido();
        semCpf.setCpf(null);
        assertEquals(Map.of("cpf", "CPF é obrigatório"), violacoes(semCpf));

        semCpf.setCpf("  ");
        assertEquals(Map.of("cpf", "CPF é obrigatório"), violacoes(semCpf));
    }

    @Test
    void validacao_DataNascimentoEOpcionalMasNaoPodeSerFutura() {
        Paciente p = pacienteValido();
        p.setDataNascimento(null);
        assertTrue(violacoes(p).isEmpty());

        p.setDataNascimento(FusoBrasil.hoje());
        assertTrue(violacoes(p).isEmpty());

        p.setDataNascimento(FusoBrasil.hoje().plusDays(1));
        assertEquals(Map.of("dataNascimento", "Data de nascimento não pode ser futura"), violacoes(p));
    }

    @Test
    void validacao_ResponsavelNaoTemRestricaoDeBeanValidation() {
        Paciente p = pacienteValido();
        p.setResponsavelNome("Joana");
        p.setResponsavelCpf("qualquer");
        assertTrue(violacoes(p).isEmpty());
    }

    @Test
    void atualizar_PacienteLegadoSemCpfNaoEditaSemPreencherOCpf() {
        Paciente legado = pacienteValido();
        legado.setCpf(null);
        assertEquals("CPF é obrigatório", violacoes(legado).get("cpf"));
    }

    @Test
    void criar_GravaResponsavelNormalizadoSemChecarUnicidadeDoCpfDele() {
        when(pacienteRepository.findByCpfAndClinicaId("390.533.447-05", TEST_CLINICA_ID)).thenReturn(Optional.empty());
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(i -> i.getArgument(0));
        Paciente novo = pacienteComCpf("39053344705");
        novo.setResponsavelNome("  Joana   da  Silva ");
        novo.setResponsavelCpf("52998224725");

        Paciente salvo = pacienteService.criar(novo);

        assertEquals("Joana da Silva", salvo.getResponsavelNome());
        assertEquals("529.982.247-25", salvo.getResponsavelCpf());
        verify(pacienteRepository, never()).findByCpfAndClinicaId(eq("529.982.247-25"), any());
    }

    @Test
    void criar_ResponsavelPodeTerOMesmoCpfDeOutroPacienteSemErro() {
        when(pacienteRepository.findByCpfAndClinicaId("390.533.447-05", TEST_CLINICA_ID)).thenReturn(Optional.empty());
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(i -> i.getArgument(0));
        Paciente novo = pacienteComCpf("390.533.447-05");
        novo.setResponsavelNome("Joana");
        novo.setResponsavelCpf("390.533.447-05");

        assertDoesNotThrow(() -> pacienteService.criar(novo));
    }

    @Test
    void criar_SemResponsavelFuncionaMesmoSendoMenor() {
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(i -> i.getArgument(0));
        Paciente crianca = pacienteComCpf(null);
        crianca.setDataNascimento(FusoBrasil.hoje().minusYears(5));

        Paciente salvo = pacienteService.criar(crianca);

        assertNull(salvo.getResponsavelNome());
        assertNull(salvo.getResponsavelCpf());
    }

    @Test
    void criar_ResponsavelCpfInvalidoRetorna400NoCampoResponsavelCpf() {
        Paciente novo = pacienteComCpf("529.982.247-25");
        novo.setResponsavelNome("Joana");
        novo.setResponsavelCpf("123.456.789-00");

        CampoInvalidoException ex = assertThrows(CampoInvalidoException.class, () -> pacienteService.criar(novo));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("responsavelCpf", ex.getCampo());
        assertEquals("CPF do responsável inválido. Verifique os números digitados.", ex.getReason());
        verifyNoInteractions(pacienteRepository);
    }

    @Test
    void criar_ResponsavelCpfSemNomeRetorna400NoCampoResponsavelNome() {
        Paciente novo = pacienteComCpf("529.982.247-25");
        novo.setResponsavelCpf("390.533.447-05");

        CampoInvalidoException ex = assertThrows(CampoInvalidoException.class, () -> pacienteService.criar(novo));

        assertEquals("responsavelNome", ex.getCampo());
        assertEquals("Nome do responsável é obrigatório quando o CPF do responsável é informado.", ex.getReason());
    }

    @Test
    void criar_ResponsavelNomeSemCpfRetorna400NoCampoResponsavelCpf() {
        Paciente novo = pacienteComCpf("529.982.247-25");
        novo.setResponsavelNome("Joana");
        novo.setResponsavelCpf("  ");

        CampoInvalidoException ex = assertThrows(CampoInvalidoException.class, () -> pacienteService.criar(novo));

        assertEquals("responsavelCpf", ex.getCampo());
        assertEquals("CPF do responsável é obrigatório quando o nome do responsável é informado.", ex.getReason());
    }

    @Test
    void atualizar_GravaOsCamposDoResponsavel() {
        Paciente existente = pacienteComCpf("529.982.247-25");
        existente.setAtivo(true);
        when(pacienteRepository.findByIdAndClinicaId("p1", TEST_CLINICA_ID)).thenReturn(Optional.of(existente));
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(i -> i.getArgument(0));
        Paciente dados = pacienteComCpf("529.982.247-25");
        dados.setResponsavelNome("Joana");
        dados.setResponsavelCpf("39053344705");

        Paciente salvo = pacienteService.atualizar("p1", dados);

        assertEquals("Joana", salvo.getResponsavelNome());
        assertEquals("390.533.447-05", salvo.getResponsavelCpf());
    }

    @Test
    void listar_BuscaPorCpfOuNomeNaoConsideraOResponsavel() {
        pacienteService.listar("390.533.447-05", null, PageRequest.of(0, 10));

        String filtro = capturarQuery().getQueryObject().toJson();
        assertFalse(filtro.contains("responsavel"));
    }

    @Test
    void listar_ProjetaOsCamposDoResponsavel() {
        pacienteService.listar(null, null, PageRequest.of(0, 10));

        Document campos = capturarQuery().getFieldsObject();
        assertTrue(campos.containsKey("responsavelNome"));
        assertFalse(campos.containsKey("responsavelCpf"));
        assertTrue(campos.containsKey("dataNascimento"));
    }

    private Paciente pacienteComCpf(String cpf) {
        return Paciente.builder().nomeCompleto("Maria Silva").cpf(cpf).build();
    }

    private Query capturarQuery() {
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate, atLeastOnce()).find(captor.capture(), eq(Paciente.class));
        return captor.getValue();
    }

    private List<Pattern> padroes(String termo, String campo) {
        pacienteService.listar(termo, null, PageRequest.of(0, 10));
        Document filtro = capturarQuery().getQueryObject();
        List<Pattern> padroes = new ArrayList<>();
        for (Object criterio : (List<?>) filtro.get("$or")) {
            Document doc = (Document) criterio;
            if (doc.containsKey(campo)) {
                padroes.add((Pattern) doc.get(campo));
            }
        }
        return padroes;
    }

    private boolean casa(List<Pattern> padroes, String valor) {
        return padroes.stream().anyMatch(p -> p.matcher(valor).find());
    }

    private List<String> gerarIds(int quantidade) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            ids.add("id-" + i);
        }
        return ids;
    }
}

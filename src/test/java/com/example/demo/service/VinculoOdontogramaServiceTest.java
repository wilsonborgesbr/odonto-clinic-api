package com.example.demo.service;

import com.example.demo.dto.VincularOdontogramaDTO;
import com.example.demo.dto.VinculoOdontogramaResponseDTO;
import com.example.demo.enums.CondicaoDenteEnum;
import com.example.demo.enums.FaceDenteEnum;
import com.example.demo.model.DenteStatus;
import com.example.demo.model.Odontograma;
import com.example.demo.model.Paciente;
import com.example.demo.model.Procedimento;
import com.example.demo.repository.OdontogramaRepository;
import com.example.demo.repository.PacienteRepository;
import com.example.demo.repository.ProcedimentoRepository;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.example.demo.service.TenantTestSupport.TEST_CLINICA_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VinculoOdontogramaServiceTest {

    private static final String PACIENTE = "p1";

    @Mock
    private ProcedimentoRepository procedimentoRepository;
    @Mock
    private OdontogramaRepository odontogramaRepository;
    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private VinculoOdontogramaService service;

    private Procedimento procedimento;

    @BeforeEach
    void setUp() {
        TenantTestSupport.loginTestUser();
        procedimento = Procedimento.builder().id("proc1").clinicaId(TEST_CLINICA_ID).pacienteId(PACIENTE).build();
        lenient().when(procedimentoRepository.findByIdAndClinicaId("proc1", TEST_CLINICA_ID)).thenReturn(Optional.of(procedimento));
        lenient().when(pacienteRepository.findByIdAndClinicaId(PACIENTE, TEST_CLINICA_ID))
                .thenReturn(Optional.of(Paciente.builder().id(PACIENTE).ativo(true).build()));
    }

    @AfterEach
    void tearDown() {
        TenantTestSupport.logout();
    }

    // ───────────── helpers ─────────────

    private VincularOdontogramaDTO dto(String odontogramaId, String dente, List<FaceDenteEnum> faces, CondicaoDenteEnum condicao) {
        VincularOdontogramaDTO dto = new VincularOdontogramaDTO();
        dto.setOdontogramaId(odontogramaId);
        dto.setDente(dente);
        dto.setFaces(faces);
        dto.setCondicao(condicao);
        return dto;
    }

    private Odontograma odontograma(String id, DenteStatus... dentes) {
        Odontograma o = new Odontograma();
        o.setId(id);
        o.setClinicaId(TEST_CLINICA_ID);
        o.setPacienteId(PACIENTE);
        o.setDentes(new ArrayList<>(List.of(dentes)));
        return o;
    }

    private UpdateResult acertou(long quantos) {
        return UpdateResult.acknowledged(quantos, quantos, null);
    }

    private void odontogramaExiste(Odontograma o) {
        lenient().when(odontogramaRepository.findByIdAndClinicaId(o.getId(), TEST_CLINICA_ID)).thenReturn(Optional.of(o));
    }

    private void escritaDoDenteExistenteAcerta() {
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Odontograma.class))).thenReturn(acertou(1));
    }

    private void procedimentoGravaOk() {
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class), any(FindAndModifyOptions.class), eq(Procedimento.class)))
                .thenAnswer(i -> procedimento);
    }

    private Document updateDoProcedimento() {
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).findAndModify(any(Query.class), update.capture(), any(FindAndModifyOptions.class), eq(Procedimento.class));
        return update.getValue().getUpdateObject().get("$set", Document.class);
    }

    private ResponseStatusException falha(VincularOdontogramaDTO dto) {
        return assertThrows(ResponseStatusException.class, () -> service.vincular("proc1", dto));
    }

    // ───────────── (a) odontograma informado ─────────────

    @Test
    void odontogramaInformado_AtualizaODenteExistenteEGravaOVinculoNoProcedimento() {
        Odontograma o = odontograma("o1", new DenteStatus("16", CondicaoDenteEnum.CARIE, List.of(FaceDenteEnum.OCLUSAL), null));
        odontogramaExiste(o);
        escritaDoDenteExistenteAcerta();
        procedimentoGravaOk();

        VinculoOdontogramaResponseDTO r = service.vincular("proc1",
                dto("o1", "16", List.of(FaceDenteEnum.MESIAL, FaceDenteEnum.MESIAL), CondicaoDenteEnum.CARIE));

        assertFalse(r.odontogramaCriado());
        assertEquals("o1", r.odontograma().getId());
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).updateFirst(query.capture(), update.capture(), eq(Odontograma.class));
        Document set = update.getValue().getUpdateObject().get("$set", Document.class);
        assertEquals(CondicaoDenteEnum.CARIE, set.get("dentes.$.condicao"));
        Document addToSet = update.getValue().getUpdateObject().get("$addToSet", Document.class);
        assertNotNull(addToSet.get("dentes.$.faces"), "soma faces sem trocar a lista inteira");
        assertTrue(query.getValue().getQueryObject().toString().contains("clinicaId"), "escopo da clínica na query");
        Document proc = updateDoProcedimento();
        assertEquals("o1", proc.get("odontogramaId"));
        assertEquals("16", proc.get("dente"));
        assertEquals(List.of(FaceDenteEnum.MESIAL), proc.get("faces"), "faces repetidas viram uma");
        verify(odontogramaRepository, never()).save(any());
    }

    @Test
    void odontogramaInformadoDeOutroPacienteOuClinica_Retorna400SemEscrever() {
        Odontograma deOutro = odontograma("o9");
        deOutro.setPacienteId("outro-paciente");
        odontogramaExiste(deOutro);
        when(odontogramaRepository.findByIdAndClinicaId("o-de-outra-clinica", TEST_CLINICA_ID)).thenReturn(Optional.empty());

        for (String id : new String[]{"o9", "o-de-outra-clinica"}) {
            ResponseStatusException ex = falha(dto(id, "16", null, null));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            assertEquals(VinculoOdontogramaService.MSG_ODONTOGRAMA_INVALIDO, ex.getReason());
        }
        verifyNoInteractions(mongoTemplate);
        verify(odontogramaRepository, never()).save(any());
    }

    // ───────────── (b) sem odontogramaId, paciente já tem ─────────────

    @Test
    void semOdontogramaId_UsaOMaisRecenteDoPaciente() {
        Odontograma recente = odontograma("recente");
        when(odontogramaRepository.findTopByClinicaIdAndPacienteIdOrderByCreatedAtDesc(TEST_CLINICA_ID, PACIENTE))
                .thenReturn(Optional.of(recente));
        odontogramaExiste(recente);
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Odontograma.class)))
                .thenReturn(acertou(0), acertou(1));
        procedimentoGravaOk();

        VinculoOdontogramaResponseDTO r = service.vincular("proc1", dto(null, "21", List.of(), CondicaoDenteEnum.HIGIDO));

        assertEquals("recente", r.odontograma().getId());
        assertFalse(r.odontogramaCriado());
        assertEquals("recente", updateDoProcedimento().get("odontogramaId"));
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate, times(2)).updateFirst(any(Query.class), update.capture(), eq(Odontograma.class));
        Document push = update.getAllValues().get(1).getUpdateObject().get("$push", Document.class);
        DenteStatus novo = (DenteStatus) push.get("dentes");
        assertEquals("21", novo.getNumeroDente());
        assertEquals(CondicaoDenteEnum.HIGIDO, novo.getCondicao());
        assertNull(novo.getFaces());
    }

    @Test
    void semOdontogramaId_ProcedimentoJaLigado_ContinuaNoMesmoMesmoHavendoUmMaisNovo() {
        procedimento.setOdontogramaId("ligado");
        Odontograma ligado = odontograma("ligado", new DenteStatus("16", null, null, null));
        odontogramaExiste(ligado);
        escritaDoDenteExistenteAcerta();
        procedimentoGravaOk();

        VinculoOdontogramaResponseDTO r = service.vincular("proc1", dto(null, "16", List.of(FaceDenteEnum.OCLUSAL), null));

        assertEquals("ligado", r.odontograma().getId());
        verify(odontogramaRepository, never()).findTopByClinicaIdAndPacienteIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void semOdontogramaId_LigacaoAntigaQuebrada_CaiNoMaisRecente() {
        procedimento.setOdontogramaId("apagado");
        when(odontogramaRepository.findByIdAndClinicaId("apagado", TEST_CLINICA_ID)).thenReturn(Optional.empty());
        Odontograma recente = odontograma("recente", new DenteStatus("16", null, null, null));
        when(odontogramaRepository.findTopByClinicaIdAndPacienteIdOrderByCreatedAtDesc(TEST_CLINICA_ID, PACIENTE))
                .thenReturn(Optional.of(recente));
        odontogramaExiste(recente);
        escritaDoDenteExistenteAcerta();
        procedimentoGravaOk();

        assertEquals("recente", service.vincular("proc1", dto(null, "16", null, null)).odontograma().getId());
    }

    // ───────────── (c) sem odontogramaId e paciente sem nenhum ─────────────

    @Test
    void semNenhumOdontograma_CriaUmMinimoParaOPacienteEMarcaODente() {
        when(odontogramaRepository.findTopByClinicaIdAndPacienteIdOrderByCreatedAtDesc(TEST_CLINICA_ID, PACIENTE))
                .thenReturn(Optional.empty());
        when(odontogramaRepository.save(any(Odontograma.class))).thenAnswer(i -> {
            Odontograma o = i.getArgument(0);
            o.setId("novo");
            return o;
        });
        odontogramaExiste(odontograma("novo"));
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Odontograma.class)))
                .thenReturn(acertou(0), acertou(1));
        procedimentoGravaOk();

        VinculoOdontogramaResponseDTO r = service.vincular("proc1", dto(null, "36", List.of(FaceDenteEnum.OCLUSAL), CondicaoDenteEnum.CARIE));

        assertTrue(r.odontogramaCriado());
        ArgumentCaptor<Odontograma> salvo = ArgumentCaptor.forClass(Odontograma.class);
        verify(odontogramaRepository).save(salvo.capture());
        assertEquals(TEST_CLINICA_ID, salvo.getValue().getClinicaId());
        assertEquals(PACIENTE, salvo.getValue().getPacienteId());
        assertTrue(salvo.getValue().getDentes().isEmpty());
        assertNotNull(salvo.getValue().getDataAvaliacao());
        assertNotNull(salvo.getValue().getCreatedAt());
        assertEquals("novo", updateDoProcedimento().get("odontogramaId"));
    }

    @Test
    void condicaoIncoerenteComFaces_Retorna400ENaoCriaNada() {
        when(odontogramaRepository.findTopByClinicaIdAndPacienteIdOrderByCreatedAtDesc(TEST_CLINICA_ID, PACIENTE))
                .thenReturn(Optional.empty());

        ResponseStatusException ex = falha(dto(null, "16", List.of(FaceDenteEnum.OCLUSAL), CondicaoDenteEnum.AUSENTE));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(odontogramaRepository, never()).save(any());
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void facesSemCondicaoInformada_ValemContraACondicaoJaExistenteNoDente() {
        odontogramaExiste(odontograma("o1", new DenteStatus("16", CondicaoDenteEnum.IMPLANTE, null, null)));

        assertEquals(HttpStatus.BAD_REQUEST, falha(dto("o1", "16", List.of(FaceDenteEnum.OCLUSAL), null)).getStatusCode());
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void semCondicaoInformada_NaoMexeNaCondicaoExistente() {
        odontogramaExiste(odontograma("o1", new DenteStatus("16", CondicaoDenteEnum.CARIE, null, null)));
        escritaDoDenteExistenteAcerta();
        procedimentoGravaOk();

        service.vincular("proc1", dto("o1", "16", List.of(FaceDenteEnum.OCLUSAL), null));

        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).updateFirst(any(Query.class), update.capture(), eq(Odontograma.class));
        assertNull(update.getValue().getUpdateObject().get("$set"), "a condição só muda quando informada");
        assertNotNull(update.getValue().getUpdateObject().get("$addToSet"));
    }

    @Test
    void semCondicaoENemFaces_ComODenteJaPresente_NaoEscreveNoOdontograma() {
        Odontograma o = odontograma("o1", new DenteStatus("16", CondicaoDenteEnum.CARIE, null, null));
        odontogramaExiste(o);
        when(mongoTemplate.exists(any(Query.class), eq(Odontograma.class))).thenReturn(true);
        procedimentoGravaOk();

        service.vincular("proc1", dto("o1", "16", null, null));

        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Odontograma.class));
        assertEquals("o1", updateDoProcedimento().get("odontogramaId"));
    }

    // ───────────── (d) FDI e escopo ─────────────

    @Test
    void denteForaDoPadraoFdi_Retorna400SemTocarNoBanco() {
        for (String dente : new String[]{"99", "09", "19", "49", "56", "86", "1", "111", "A1", "", " ", "16 ", null}) {
            ResponseStatusException ex = falha(dto("o1", dente, null, null));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode(), String.valueOf(dente));
        }
        verifyNoInteractions(mongoTemplate);
        verify(odontogramaRepository, never()).save(any());
    }

    @Test
    void procedimentoDeOutraClinicaOuInexistente_Retorna404() {
        when(procedimentoRepository.findByIdAndClinicaId("alheio", TEST_CLINICA_ID)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.vincular("alheio", dto(null, "16", null, null)));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verifyNoInteractions(odontogramaRepository, mongoTemplate);
    }

    @Test
    void pacienteInativoOuAusente_Retorna404() {
        when(pacienteRepository.findByIdAndClinicaId(PACIENTE, TEST_CLINICA_ID))
                .thenReturn(Optional.of(Paciente.builder().id(PACIENTE).ativo(false).build()));

        assertEquals(HttpStatus.NOT_FOUND, falha(dto(null, "16", null, null)).getStatusCode());
        verifyNoInteractions(mongoTemplate);
    }

    // ───────────── (e) atomicidade: falha no meio ─────────────

    @Test
    void falhaAoGravarOProcedimento_ComOdontogramaCriado_ApagaOOdontogramaCriado() {
        when(odontogramaRepository.findTopByClinicaIdAndPacienteIdOrderByCreatedAtDesc(TEST_CLINICA_ID, PACIENTE))
                .thenReturn(Optional.empty());
        when(odontogramaRepository.save(any(Odontograma.class))).thenAnswer(i -> {
            Odontograma o = i.getArgument(0);
            o.setId("novo");
            return o;
        });
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Odontograma.class)))
                .thenReturn(acertou(0), acertou(1));
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class), any(FindAndModifyOptions.class), eq(Procedimento.class)))
                .thenThrow(new RuntimeException("mongo caiu"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.vincular("proc1", dto(null, "16", List.of(FaceDenteEnum.OCLUSAL), CondicaoDenteEnum.CARIE)));

        assertEquals("mongo caiu", ex.getMessage());
        verify(odontogramaRepository).deleteById("novo");
    }

    @Test
    void falhaAoGravarOProcedimento_ComDenteQueJaExistia_RestauraOEstadoAnteriorDoDente() {
        DenteStatus antes = new DenteStatus("16", CondicaoDenteEnum.CARIE, new ArrayList<>(List.of(FaceDenteEnum.OCLUSAL)), "obs");
        odontogramaExiste(odontograma("o1", antes));
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Odontograma.class))).thenReturn(acertou(1));
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class), any(FindAndModifyOptions.class), eq(Procedimento.class)))
                .thenThrow(new RuntimeException("mongo caiu"));

        assertThrows(RuntimeException.class,
                () -> service.vincular("proc1", dto("o1", "16", List.of(FaceDenteEnum.MESIAL), CondicaoDenteEnum.RESTAURACAO_SATISFATORIA)));

        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate, times(2)).updateFirst(any(Query.class), update.capture(), eq(Odontograma.class));
        Document restauracao = update.getAllValues().get(1).getUpdateObject().get("$set", Document.class);
        DenteStatus restaurado = (DenteStatus) restauracao.get("dentes.$");
        assertEquals("16", restaurado.getNumeroDente());
        assertEquals(CondicaoDenteEnum.CARIE, restaurado.getCondicao());
        assertEquals(List.of(FaceDenteEnum.OCLUSAL), restaurado.getFaces());
        assertEquals("obs", restaurado.getObservacao());
        verify(odontogramaRepository, never()).deleteById(any());
    }

    @Test
    void falhaAoGravarOProcedimento_ComDenteNovoEmOdontogramaExistente_RemoveODenteAcrescentado() {
        odontogramaExiste(odontograma("o1", new DenteStatus("11", CondicaoDenteEnum.HIGIDO, null, null)));
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Odontograma.class)))
                .thenReturn(acertou(0), acertou(1), acertou(1));
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class), any(FindAndModifyOptions.class), eq(Procedimento.class)))
                .thenThrow(new RuntimeException("mongo caiu"));

        assertThrows(RuntimeException.class, () -> service.vincular("proc1", dto("o1", "16", null, CondicaoDenteEnum.CARIE)));

        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate, times(3)).updateFirst(any(Query.class), update.capture(), eq(Odontograma.class));
        Document pull = update.getAllValues().get(2).getUpdateObject().get("$pull", Document.class);
        assertEquals(new Document("numeroDente", "16"), pull.get("dentes"));
    }

    @Test
    void procedimentoApagadoNoMeio_Retorna404EDesfaz() {
        odontogramaExiste(odontograma("o1", new DenteStatus("16", CondicaoDenteEnum.CARIE, null, null)));
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Odontograma.class))).thenReturn(acertou(1));
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class), any(FindAndModifyOptions.class), eq(Procedimento.class)))
                .thenReturn(null);

        ResponseStatusException ex = falha(dto("o1", "16", List.of(FaceDenteEnum.OCLUSAL), null));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(mongoTemplate, times(2)).updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Odontograma.class));
    }

    @Test
    void falhaAoGravarODente_ComOdontogramaCriado_ApagaOOdontogramaENaoGravaOProcedimento() {
        when(odontogramaRepository.findTopByClinicaIdAndPacienteIdOrderByCreatedAtDesc(TEST_CLINICA_ID, PACIENTE))
                .thenReturn(Optional.empty());
        when(odontogramaRepository.save(any(Odontograma.class))).thenAnswer(i -> {
            Odontograma o = i.getArgument(0);
            o.setId("novo");
            return o;
        });
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Odontograma.class)))
                .thenThrow(new RuntimeException("mongo caiu"));

        assertThrows(RuntimeException.class, () -> service.vincular("proc1", dto(null, "16", null, CondicaoDenteEnum.CARIE)));

        verify(odontogramaRepository).deleteById("novo");
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(UpdateDefinition.class), any(FindAndModifyOptions.class), eq(Procedimento.class));
    }

    @Test
    void falhaAoGravarODente_EmOdontogramaExistente_NaoTentaDesfazerNada() {
        odontogramaExiste(odontograma("o1"));
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Odontograma.class)))
                .thenThrow(new RuntimeException("mongo caiu"));

        assertThrows(RuntimeException.class, () -> service.vincular("proc1", dto("o1", "16", null, CondicaoDenteEnum.CARIE)));

        verify(mongoTemplate, times(1)).updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Odontograma.class));
        verify(odontogramaRepository, never()).deleteById(any());
    }

    @Test
    void falhaNoDesfazer_NaoMascaraOErroOriginal() {
        when(odontogramaRepository.findTopByClinicaIdAndPacienteIdOrderByCreatedAtDesc(TEST_CLINICA_ID, PACIENTE))
                .thenReturn(Optional.empty());
        when(odontogramaRepository.save(any(Odontograma.class))).thenAnswer(i -> {
            Odontograma o = i.getArgument(0);
            o.setId("novo");
            return o;
        });
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Odontograma.class)))
                .thenReturn(acertou(0), acertou(1));
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class), any(FindAndModifyOptions.class), eq(Procedimento.class)))
                .thenThrow(new RuntimeException("erro original"));
        doThrow(new RuntimeException("erro no desfazer")).when(odontogramaRepository).deleteById("novo");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.vincular("proc1", dto(null, "16", null, null)));

        assertEquals("erro original", ex.getMessage());
    }

    @Test
    void concorrenciaQueNuncaSeResolve_Retorna409AposTresTentativas() {
        odontogramaExiste(odontograma("o1"));
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Odontograma.class))).thenReturn(acertou(0));

        ResponseStatusException ex = falha(dto("o1", "16", null, CondicaoDenteEnum.CARIE));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(mongoTemplate, times(6)).updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Odontograma.class));
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(UpdateDefinition.class), any(FindAndModifyOptions.class), eq(Procedimento.class));
    }
}

package com.example.demo.service;

import com.example.demo.dto.DentistaListagemDTO;
import com.example.demo.enums.SexoEnum;
import com.example.demo.model.Endereco;
import jakarta.validation.Validation;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import com.example.demo.model.Dentista;
import com.example.demo.repository.DentistaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Optional;

import static com.example.demo.service.TenantTestSupport.TEST_CLINICA_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DentistaServiceTest {

    @Mock
    private DentistaRepository dentistaRepository;

    @InjectMocks
    private DentistaService dentistaService;

    @BeforeEach
    void setUp() {
        TenantTestSupport.loginTestUser();
    }

    @AfterEach
    void tearDown() {
        TenantTestSupport.logout();
    }

    @Test
    void criar_SemCpfEPermitidoESalvaCpfNulo() {
        when(dentistaRepository.findByCroAndClinicaId("CRO-SE 12345", TEST_CLINICA_ID)).thenReturn(Optional.empty());
        when(dentistaRepository.save(any(Dentista.class))).thenAnswer(i -> i.getArgument(0));

        Dentista salvo = dentistaService.criar(dentista("cro-se 12345", ""));

        assertEquals("CRO-SE 12345", salvo.getCro());
        assertNull(salvo.getCpf());
        assertEquals(TEST_CLINICA_ID, salvo.getClinicaId());
        verify(dentistaRepository, never()).findByCpfAndClinicaId(any(), any());
    }

    @Test
    void criar_NormalizaCpfEChecaDuplicidadePorClinica() {
        when(dentistaRepository.findByCroAndClinicaId("CRO-SE 12345", TEST_CLINICA_ID)).thenReturn(Optional.empty());
        when(dentistaRepository.findByCpfAndClinicaId("529.982.247-25", TEST_CLINICA_ID)).thenReturn(Optional.empty());
        when(dentistaRepository.save(any(Dentista.class))).thenAnswer(i -> i.getArgument(0));

        Dentista salvo = dentistaService.criar(dentista("CRO-SE 12345", "52998224725"));

        assertEquals("529.982.247-25", salvo.getCpf());
    }

    @Test
    void criar_CroDuplicadoNaClinicaRetorna400() {
        when(dentistaRepository.findByCroAndClinicaId("CRO-SE 12345", TEST_CLINICA_ID))
                .thenReturn(Optional.of(dentista("CRO-SE 12345", null)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> dentistaService.criar(dentista("cro se 12345", null)));

        assertEquals("CRO já cadastrado nesta clínica.", ex.getReason());
        verify(dentistaRepository, never()).save(any());
    }

    @Test
    void criar_CpfDuplicadoNaClinicaRetorna400() {
        when(dentistaRepository.findByCroAndClinicaId(any(), any())).thenReturn(Optional.empty());
        when(dentistaRepository.findByCpfAndClinicaId("529.982.247-25", TEST_CLINICA_ID))
                .thenReturn(Optional.of(dentista("CRO-SE 99999", "529.982.247-25")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> dentistaService.criar(dentista("CRO-SE 12345", "529.982.247-25")));

        assertEquals("CPF já cadastrado nesta clínica.", ex.getReason());
    }

    @Test
    void criar_CpfInvalidoRetorna400ComMensagemClara() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> dentistaService.criar(dentista("CRO-SE 12345", "111.111.111-11")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().startsWith("CPF inválido"));
        verifyNoInteractions(dentistaRepository);
    }

    @Test
    void criar_CroInvalidoRetorna400ComMensagemClara() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> dentistaService.criar(dentista("CRO-XX 12345", null)));

        assertTrue(ex.getReason().startsWith("CRO inválido"));
        verifyNoInteractions(dentistaRepository);
    }

    @Test
    void criar_CorridaNoIndiceDeCpfVira400DeCpf() {
        when(dentistaRepository.findByCroAndClinicaId(any(), any())).thenReturn(Optional.empty());
        when(dentistaRepository.findByCpfAndClinicaId(any(), any())).thenReturn(Optional.empty());
        when(dentistaRepository.save(any(Dentista.class)))
                .thenThrow(new DuplicateKeyException("E11000 index: clinica_cpf_unique dup key"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> dentistaService.criar(dentista("CRO-SE 12345", "529.982.247-25")));

        assertEquals("CPF já cadastrado nesta clínica.", ex.getReason());
    }

    @Test
    void criar_CorridaNoIndiceDeCroVira400DeCro() {
        when(dentistaRepository.findByCroAndClinicaId(any(), any())).thenReturn(Optional.empty());
        when(dentistaRepository.save(any(Dentista.class)))
                .thenThrow(new DuplicateKeyException("E11000 index: clinica_cro_unique dup key"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> dentistaService.criar(dentista("CRO-SE 12345", null)));

        assertEquals("CRO já cadastrado nesta clínica.", ex.getReason());
    }

    @Test
    void atualizar_RemoverCpfEPermitido() {
        Dentista existente = dentista("CRO-SE 12345", "529.982.247-25");
        existente.setId("d1");
        existente.setAtivo(true);
        when(dentistaRepository.findByIdAndClinicaId("d1", TEST_CLINICA_ID)).thenReturn(Optional.of(existente));
        when(dentistaRepository.save(any(Dentista.class))).thenAnswer(i -> i.getArgument(0));

        Dentista atualizado = dentistaService.atualizar("d1", dentista("CRO-SE 12345", "  "));

        assertNull(atualizado.getCpf());
        verify(dentistaRepository, never()).findByCroAndClinicaId(any(), any());
    }

    @Test
    void atualizar_CorrigeCroLegadoSemQuebrarQuandoCampoLegadoEhNulo() {
        Dentista existente = dentista(null, null);
        existente.setId("d1");
        existente.setAtivo(true);
        when(dentistaRepository.findByIdAndClinicaId("d1", TEST_CLINICA_ID)).thenReturn(Optional.of(existente));
        when(dentistaRepository.findByCroAndClinicaId("CRO-SE 12345", TEST_CLINICA_ID)).thenReturn(Optional.empty());
        when(dentistaRepository.save(any(Dentista.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals("CRO-SE 12345", dentistaService.atualizar("d1", dentista("cro-se 12345", null)).getCro());
    }

    @Test
    void atualizar_ReenviarDadoInvalidoLegadoPedeCorrecao() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> dentistaService.atualizar("d1", dentista("CRO SE12345 ???", null)));

        assertTrue(ex.getReason().startsWith("CRO inválido"));
        verify(dentistaRepository, never()).save(any());
    }

    @Test
    void indicesSaoCompostosParciaisPorClinicaESemUniqueGlobal() throws NoSuchFieldException {
        CompoundIndexes indices = Dentista.class.getAnnotation(CompoundIndexes.class);

        assertNotNull(indices);
        assertEquals(2, indices.value().length);
        for (CompoundIndex indice : indices.value()) {
            assertTrue(indice.unique());
            assertTrue(indice.def().startsWith("{'clinicaId': 1, "));
            assertTrue(indice.partialFilter().contains("$type: 'string'") && indice.partialFilter().contains("$gt: ''"));
        }
        assertEquals(Arrays.asList("clinica_cro_unique", "clinica_cpf_unique"),
                Arrays.stream(indices.value()).map(CompoundIndex::name).toList());
        assertFalse(Dentista.class.getDeclaredField("cro").isAnnotationPresent(Indexed.class));
        assertFalse(Dentista.class.getDeclaredField("cpf").isAnnotationPresent(Indexed.class));
    }

    @Test
    void listagemExpoeCpfCanonicoOuNulo() {
        Dentista com = dentista("CRO-SE 12345", "529.982.247-25");
        Dentista sem = dentista("CRO-SE 54321", null);

        assertEquals("529.982.247-25", new DentistaListagemDTO(com).cpf());
        assertNull(new DentistaListagemDTO(sem).cpf());
    }

    @Test
    void erroDeBeanValidationDoCepVemComChaveEnderecoPontoCep() {
        Dentista d = dentista("CRO-SE 12345", null);
        d.setEndereco(Endereco.builder().cep("abc").build());
        BeanPropertyBindingResult resultado = new BeanPropertyBindingResult(d, "dentista");

        new SpringValidatorAdapter(Validation.buildDefaultValidatorFactory().getValidator()).validate(d, resultado);

        assertTrue(resultado.getFieldErrors().stream().anyMatch(e -> e.getField().equals("endereco.cep")));
    }

    private Dentista dentista(String cro, String cpf) {
        Dentista d = new Dentista();
        d.setNomeCompleto("Dr. Teste");
        d.setCro(cro);
        d.setCpf(cpf);
        d.setSexo(SexoEnum.values()[0]);
        return d;
    }
}

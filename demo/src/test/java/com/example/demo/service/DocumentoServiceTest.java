package com.example.demo.service;

import com.example.demo.enums.TipoDocumentoEnum;
import com.example.demo.model.Documento;
import com.example.demo.model.Paciente;
import com.example.demo.repository.DocumentoRepository;
import com.example.demo.repository.PacienteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static com.example.demo.service.TenantTestSupport.TEST_CLINICA_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentoServiceTest {

    @Mock
    private DocumentoRepository documentoRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @InjectMocks
    private DocumentoService documentoService;

    private Documento documento;
    private Paciente pacienteAtivo;
    private Paciente pacienteInativo;

    @BeforeEach
    void setUp() {
        TenantTestSupport.loginTestUser();

        documento = Documento.builder()
                .id("doc-123")
                .clinicaId(TEST_CLINICA_ID)
                .pacienteId("pac-123")
                .tipo(TipoDocumentoEnum.RADIOGRAFIA)
                .urlArquivo("https://s3.amazonaws.com/clinica/radiografia123.jpg")
                .descricao("Radiografia Panorâmica")
                .build();

        pacienteAtivo = Paciente.builder()
                .id("pac-123")
                .clinicaId(TEST_CLINICA_ID)
                .nomeCompleto("Tainah Borges")
                .ativo(true)
                .build();

        pacienteInativo = Paciente.builder()
                .id("pac-inactive")
                .clinicaId(TEST_CLINICA_ID)
                .nomeCompleto("Fulano Inativo")
                .ativo(false)
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantTestSupport.logout();
    }

    @Test
    void criar_ComPacienteAtivo_DeveSalvarComDataUpload() {
        when(pacienteRepository.findByIdAndClinicaId("pac-123", TEST_CLINICA_ID))
                .thenReturn(Optional.of(pacienteAtivo));
        when(documentoRepository.save(any(Documento.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Documento resultado = documentoService.criar(documento);

        assertNotNull(resultado);
        assertEquals(TEST_CLINICA_ID, resultado.getClinicaId());
        assertNotNull(resultado.getDataUpload());
        verify(documentoRepository, times(1)).save(any(Documento.class));
    }

    @Test
    void criar_ComPacienteInexistente_DeveLancarExcecao() {
        when(pacienteRepository.findByIdAndClinicaId("pac-123", TEST_CLINICA_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> documentoService.criar(documento));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Paciente não encontrado ou inativo.", exception.getReason());
        verify(documentoRepository, never()).save(any(Documento.class));
    }

    @Test
    void criar_ComPacienteInativo_DeveLancarExcecao() {
        documento.setPacienteId("pac-inactive");
        when(pacienteRepository.findByIdAndClinicaId("pac-inactive", TEST_CLINICA_ID))
                .thenReturn(Optional.of(pacienteInativo));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> documentoService.criar(documento));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Paciente não encontrado ou inativo.", exception.getReason());
        verify(documentoRepository, never()).save(any(Documento.class));
    }

    @Test
    void criar_ComPacienteIdNulo_DeveLancarExcecao() {
        documento.setPacienteId(null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> documentoService.criar(documento));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("ID do paciente não pode ser nulo.", exception.getReason());
    }

    @Test
    void buscarPorId_Inexistente_DeveLancarExcecao() {
        when(documentoRepository.findByIdAndClinicaId("invalido", TEST_CLINICA_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> documentoService.buscarPorId("invalido"));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Documento não encontrado.", exception.getReason());
    }

    @Test
    void deletar_ComSucesso_DeveDeletarFisicamente() {
        when(documentoRepository.findByIdAndClinicaId("doc-123", TEST_CLINICA_ID))
                .thenReturn(Optional.of(documento));
        doNothing().when(documentoRepository).delete(any(Documento.class));

        documentoService.deletar("doc-123");

        verify(documentoRepository, times(1)).delete(documento);
    }

    @Test
    void deletar_Inexistente_DeveLancarExcecao() {
        when(documentoRepository.findByIdAndClinicaId("invalido", TEST_CLINICA_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> documentoService.deletar("invalido"));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Documento não encontrado.", exception.getReason());
        verify(documentoRepository, never()).delete(any(Documento.class));
    }
}

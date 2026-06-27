package com.example.demo.service;

import com.example.demo.enums.TipoDocumentoEnum;
import com.example.demo.model.Documento;
import com.example.demo.model.Paciente;
import com.example.demo.repository.DocumentoRepository;
import com.example.demo.repository.PacienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

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
        documento = Documento.builder()
                .id("doc-123")
                .pacienteId("pac-123")
                .tipo(TipoDocumentoEnum.RADIOGRAFIA)
                .urlArquivo("https://s3.amazonaws.com/clinica/radiografia123.jpg")
                .descricao("Radiografia Panorâmica")
                .build();

        pacienteAtivo = Paciente.builder()
                .id("pac-123")
                .nomeCompleto("Tainah Borges")
                .ativo(true)
                .build();

        pacienteInativo = Paciente.builder()
                .id("pac-inactive")
                .nomeCompleto("Fulano Inativo")
                .ativo(false)
                .build();
    }

    @Test
    void criar_ComPacienteAtivo_DeveSalvarComDataUpload() {
        when(pacienteRepository.findById("pac-123")).thenReturn(Optional.of(pacienteAtivo));
        when(documentoRepository.save(any(Documento.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Documento resultado = documentoService.criar(documento);

        assertNotNull(resultado);
        assertNotNull(resultado.getDataUpload());
        verify(documentoRepository, times(1)).save(any(Documento.class));
    }

    @Test
    void criar_ComPacienteInexistente_DeveLancarExcecao() {
        when(pacienteRepository.findById("pac-123")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            documentoService.criar(documento);
        });

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Paciente não encontrado ou inativo.", exception.getReason());
        verify(documentoRepository, never()).save(any(Documento.class));
    }

    @Test
    void criar_ComPacienteInativo_DeveLancarExcecao() {
        documento.setPacienteId("pac-inactive");
        when(pacienteRepository.findById("pac-inactive")).thenReturn(Optional.of(pacienteInativo));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            documentoService.criar(documento);
        });

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Paciente não encontrado ou inativo.", exception.getReason());
        verify(documentoRepository, never()).save(any(Documento.class));
    }

    @Test
    void criar_ComPacienteIdNulo_DeveLancarExcecao() {
        documento.setPacienteId(null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            documentoService.criar(documento);
        });

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("ID do paciente não pode ser nulo.", exception.getReason());
    }

    @Test
    void buscarPorId_Inexistente_DeveLancarExcecao() {
        when(documentoRepository.findById("invalido")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            documentoService.buscarPorId("invalido");
        });

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Documento não encontrado.", exception.getReason());
    }

    @Test
    void deletar_ComSucesso_DeveDeletarFisicamente() {
        when(documentoRepository.findById("doc-123")).thenReturn(Optional.of(documento));
        doNothing().when(documentoRepository).delete(any(Documento.class));

        documentoService.deletar("doc-123");

        verify(documentoRepository, times(1)).delete(documento);
    }

    @Test
    void deletar_Inexistente_DeveLancarExcecao() {
        when(documentoRepository.findById("invalido")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            documentoService.deletar("invalido");
        });

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Documento não encontrado.", exception.getReason());
        verify(documentoRepository, never()).delete(any(Documento.class));
    }
}

package com.example.demo.service;

import com.example.demo.model.Convenio;
import com.example.demo.repository.ConvenioRepository;
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
class ConvenioServiceTest {

    @Mock
    private ConvenioRepository convenioRepository;

    @InjectMocks
    private ConvenioService convenioService;

    private Convenio convenio;

    @BeforeEach
    void setUp() {
        convenio = Convenio.builder()
                .id("conv-123")
                .nome("Bradesco Dental")
                .cnpj("12.345.678/0001-90")
                .telefone("(11) 4004-0019")
                .email("contato@bradescodental.com.br")
                .tabelaDePrecos("Tabela A")
                .ativo(true)
                .build();
    }

    @Test
    void criar_DeveSalvarConvenioComAtivoETimestamps() {
        when(convenioRepository.findByCnpj(convenio.getCnpj())).thenReturn(Optional.empty());
        when(convenioRepository.save(any(Convenio.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Convenio resultado = convenioService.criar(convenio);

        assertNotNull(resultado);
        assertTrue(resultado.getAtivo());
        assertNotNull(resultado.getCreatedAt());
        assertNotNull(resultado.getUpdatedAt());
        verify(convenioRepository, times(1)).save(any(Convenio.class));
    }

    @Test
    void criar_ComCnpjDuplicado_DeveLancarExcecao() {
        when(convenioRepository.findByCnpj(convenio.getCnpj())).thenReturn(Optional.of(convenio));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            convenioService.criar(convenio);
        });

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("CNPJ já cadastrado.", exception.getReason());
        verify(convenioRepository, never()).save(any(Convenio.class));
    }

    @Test
    void buscarPorId_InexistenteOuInativo_DeveLancarExcecao() {
        when(convenioRepository.findById("invalido")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            convenioService.buscarPorId("invalido");
        });

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Convênio não encontrado.", exception.getReason());
    }

    @Test
    void atualizar_ComSucesso_DeveAtualizarCamposETimestamp() {
        Convenio dadosAtualizados = Convenio.builder()
                .nome("Bradesco Dental Premium")
                .cnpj("12.345.678/0001-90") // mesmo CNPJ
                .telefone("(11) 99999-9999")
                .email("premium@bradescodental.com.br")
                .tabelaDePrecos("Tabela Premium")
                .build();

        when(convenioRepository.findById("conv-123")).thenReturn(Optional.of(convenio));
        when(convenioRepository.save(any(Convenio.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Convenio resultado = convenioService.atualizar("conv-123", dadosAtualizados);

        assertEquals("Bradesco Dental Premium", resultado.getNome());
        assertEquals("(11) 99999-9999", resultado.getTelefone());
        assertEquals("premium@bradescodental.com.br", resultado.getEmail());
        assertEquals("Tabela Premium", resultado.getTabelaDePrecos());
        assertNotNull(resultado.getUpdatedAt());
    }

    @Test
    void atualizar_CnpjExistenteEmOutroConvenio_DeveLancarExcecao() {
        Convenio dadosAtualizados = Convenio.builder()
                .nome("Bradesco Dental Premium")
                .cnpj("99.999.999/9999-99") // novo CNPJ
                .build();

        Convenio outroConvenio = Convenio.builder()
                .id("conv-999")
                .cnpj("99.999.999/9999-99")
                .build();

        when(convenioRepository.findById("conv-123")).thenReturn(Optional.of(convenio));
        when(convenioRepository.findByCnpj("99.999.999/9999-99")).thenReturn(Optional.of(outroConvenio));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            convenioService.atualizar("conv-123", dadosAtualizados);
        });

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("CNPJ já cadastrado.", exception.getReason());
    }

    @Test
    void deletar_DeveRealizarSoftDelete() {
        when(convenioRepository.findById("conv-123")).thenReturn(Optional.of(convenio));
        when(convenioRepository.save(any(Convenio.class))).thenAnswer(invocation -> invocation.getArgument(0));

        convenioService.deletar("conv-123");

        assertFalse(convenio.getAtivo());
        assertNotNull(convenio.getUpdatedAt());
        verify(convenioRepository, times(1)).save(convenio);
    }
}

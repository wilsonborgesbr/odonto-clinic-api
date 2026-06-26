package com.example.demo.service;

import com.example.demo.enums.FormaPagamentoEnum;
import com.example.demo.enums.StatusFinanceiroEnum;
import com.example.demo.model.ContaReceber;
import com.example.demo.model.Paciente;
import com.example.demo.repository.ContaReceberRepository;
import com.example.demo.repository.PacienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContaReceberServiceTest {

    @Mock
    private ContaReceberRepository contaReceberRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @InjectMocks
    private ContaReceberService contaReceberService;

    private Paciente pacienteAtivo;
    private ContaReceber contaPendente;

    @BeforeEach
    void setUp() {
        pacienteAtivo = Paciente.builder()
                .id("paciente-123")
                .nomeCompleto("Paciente de Teste")
                .ativo(true)
                .build();

        contaPendente = ContaReceber.builder()
                .id("conta-123")
                .pacienteId("paciente-123")
                .descricao("Consulta de Canal")
                .valorTotal(500.0)
                .valorPago(0.0)
                .formaPagamento(FormaPagamentoEnum.PIX)
                .numeroParcelas(1)
                .dataVencimento(LocalDate.now().plusDays(10))
                .status(StatusFinanceiroEnum.PENDENTE)
                .build();
    }

    @Test
    void criar_ComPacienteInexistente_DeveLancarExcecao() {
        when(pacienteRepository.findById("paciente-123")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            contaReceberService.criar(contaPendente);
        });

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Paciente não encontrado.", exception.getReason());
        verify(contaReceberRepository, never()).save(any(ContaReceber.class));
    }

    @Test
    void criar_ComPacienteInativo_DeveLancarExcecao() {
        pacienteAtivo.setAtivo(false);
        when(pacienteRepository.findById("paciente-123")).thenReturn(Optional.of(pacienteAtivo));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            contaReceberService.criar(contaPendente);
        });

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Paciente não encontrado.", exception.getReason());
        verify(contaReceberRepository, never()).save(any(ContaReceber.class));
    }

    @Test
    void criar_ComPacienteAtivo_DeveCriarContaComValoresPadrao() {
        when(pacienteRepository.findById("paciente-123")).thenReturn(Optional.of(pacienteAtivo));
        when(contaReceberRepository.save(any(ContaReceber.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ContaReceber resultado = contaReceberService.criar(contaPendente);

        assertNotNull(resultado);
        assertEquals(0.0, resultado.getValorPago());
        assertEquals(StatusFinanceiroEnum.PENDENTE, resultado.getStatus());
        assertNotNull(resultado.getCreatedAt());
        assertNotNull(resultado.getUpdatedAt());
        verify(contaReceberRepository, times(1)).save(any(ContaReceber.class));
    }

    @Test
    void registrarPagamento_Parcial_DeveAcumularValorPagoEManterPendente() {
        when(contaReceberRepository.findById("conta-123")).thenReturn(Optional.of(contaPendente));
        when(contaReceberRepository.save(any(ContaReceber.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ContaReceber resultado = contaReceberService.registrarPagamento("conta-123", 200.0);

        assertEquals(200.0, resultado.getValorPago());
        assertEquals(StatusFinanceiroEnum.PENDENTE, resultado.getStatus());
        assertNull(resultado.getDataPagamento());
        verify(contaReceberRepository, times(1)).save(any(ContaReceber.class));
    }

    @Test
    void registrarPagamento_Total_DeveAlterarStatusParaPagoEDefinirDataPagamento() {
        when(contaReceberRepository.findById("conta-123")).thenReturn(Optional.of(contaPendente));
        when(contaReceberRepository.save(any(ContaReceber.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ContaReceber resultado = contaReceberService.registrarPagamento("conta-123", 500.0);

        assertEquals(500.0, resultado.getValorPago());
        assertEquals(StatusFinanceiroEnum.PAGO, resultado.getStatus());
        assertEquals(LocalDate.now(), resultado.getDataPagamento());
        verify(contaReceberRepository, times(1)).save(any(ContaReceber.class));
    }
}

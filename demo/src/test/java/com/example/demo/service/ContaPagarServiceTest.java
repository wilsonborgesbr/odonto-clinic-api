package com.example.demo.service;

import com.example.demo.enums.CategoriaContaPagarEnum;
import com.example.demo.enums.StatusFinanceiroEnum;
import com.example.demo.model.ContaPagar;
import com.example.demo.repository.ContaPagarRepository;
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
class ContaPagarServiceTest {

    @Mock
    private ContaPagarRepository contaPagarRepository;

    @InjectMocks
    private ContaPagarService contaPagarService;

    private ContaPagar contaPagarPendente;

    @BeforeEach
    void setUp() {
        contaPagarPendente = ContaPagar.builder()
                .id("despesa-123")
                .descricao("Compra de luvas e máscaras")
                .categoria(CategoriaContaPagarEnum.MATERIAL_ODONTOLOGICO)
                .fornecedor("Dental Cremer")
                .valor(350.0)
                .dataVencimento(LocalDate.now().plusDays(5))
                .status(StatusFinanceiroEnum.PENDENTE)
                .build();
    }

    @Test
    void criar_DeveDefinirDatasEStatusPadrante() {
        when(contaPagarRepository.save(any(ContaPagar.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ContaPagar resultado = contaPagarService.criar(contaPagarPendente);

        assertNotNull(resultado);
        assertNotNull(resultado.getCreatedAt());
        assertNotNull(resultado.getUpdatedAt());
        assertEquals(StatusFinanceiroEnum.PENDENTE, resultado.getStatus());
        verify(contaPagarRepository, times(1)).save(any(ContaPagar.class));
    }

    @Test
    void criar_ComStatusPagoEDataNula_DeveDefinirDataPagamentoComoHoje() {
        contaPagarPendente.setStatus(StatusFinanceiroEnum.PAGO);
        when(contaPagarRepository.save(any(ContaPagar.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ContaPagar resultado = contaPagarService.criar(contaPagarPendente);

        assertEquals(StatusFinanceiroEnum.PAGO, resultado.getStatus());
        assertEquals(LocalDate.now(), resultado.getDataPagamento());
    }

    @Test
    void buscarPorId_Inexistente_DeveLancarExcecao() {
        when(contaPagarRepository.findById("invalido")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            contaPagarService.buscarPorId("invalido");
        });

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Conta a pagar não encontrada.", exception.getReason());
    }

    @Test
    void atualizar_ParaPago_DeveDefinirDataPagamento() {
        ContaPagar dadosAtualizacao = ContaPagar.builder()
                .descricao("Novo Nome")
                .categoria(CategoriaContaPagarEnum.LUZ)
                .status(StatusFinanceiroEnum.PAGO)
                .valor(350.0)
                .build();

        when(contaPagarRepository.findById("despesa-123")).thenReturn(Optional.of(contaPagarPendente));
        when(contaPagarRepository.save(any(ContaPagar.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ContaPagar resultado = contaPagarService.atualizar("despesa-123", dadosAtualizacao);

        assertEquals("Novo Nome", resultado.getDescricao());
        assertEquals(CategoriaContaPagarEnum.LUZ, resultado.getCategoria());
        assertEquals(StatusFinanceiroEnum.PAGO, resultado.getStatus());
        assertEquals(LocalDate.now(), resultado.getDataPagamento());
    }
}

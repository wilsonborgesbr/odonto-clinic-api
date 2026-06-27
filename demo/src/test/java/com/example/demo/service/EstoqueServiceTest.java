package com.example.demo.service;

import com.example.demo.enums.CategoriaEstoqueEnum;
import com.example.demo.model.Estoque;
import com.example.demo.repository.EstoqueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EstoqueServiceTest {

    @Mock
    private EstoqueRepository estoqueRepository;

    @InjectMocks
    private EstoqueService estoqueService;

    private Estoque itemEstoque;

    @BeforeEach
    void setUp() {
        itemEstoque = Estoque.builder()
                .id("est-123")
                .nomeMaterial("Luva de Procedimento Látex")
                .categoria(CategoriaEstoqueEnum.EPI)
                .quantidadeAtual(10)
                .quantidadeMinima(5)
                .unidadeMedida("caixa")
                .fornecedor("Dental Cremer")
                .dataValidade(LocalDate.now().plusMonths(12))
                .observacoes("Tamanho M")
                .build();
    }

    @Test
    void criar_DeveDefinirTimestamps() {
        when(estoqueRepository.save(any(Estoque.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Estoque resultado = estoqueService.criar(itemEstoque);

        assertNotNull(resultado);
        assertNotNull(resultado.getCreatedAt());
        assertNotNull(resultado.getUpdatedAt());
        verify(estoqueRepository, times(1)).save(any(Estoque.class));
    }

    @Test
    void buscarPorId_Inexistente_DeveLancarExcecao() {
        when(estoqueRepository.findById("invalido")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            estoqueService.buscarPorId("invalido");
        });

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Item não encontrado no estoque.", exception.getReason());
    }

    @Test
    void listarAbaixoDoMinimo_DeveFiltrarItensAbaixoOuIgualAoMinimo() {
        Estoque item1 = Estoque.builder()
                .id("est-1")
                .nomeMaterial("Mascara Descartavel")
                .quantidadeAtual(2)
                .quantidadeMinima(5) // Abaixo do mínimo!
                .build();

        Estoque item2 = Estoque.builder()
                .id("est-2")
                .nomeMaterial("Alcool 70%")
                .quantidadeAtual(10)
                .quantidadeMinima(5) // Acima do mínimo!
                .build();

        when(estoqueRepository.findAll()).thenReturn(Arrays.asList(item1, item2));
        
        // Mock a query para item1 (quantidadeMinima = 5)
        // O item1 (quantidadeAtual = 2) está abaixo ou igual a 5. Então findByQuantidadeAtualLessThanEqual(5) deve conter item1.
        when(estoqueRepository.findByQuantidadeAtualLessThanEqual(5)).thenReturn(Arrays.asList(item1));
        
        List<Estoque> resultado = estoqueService.listarAbaixoDoMinimo();

        assertEquals(1, resultado.size());
        assertEquals("est-1", resultado.get(0).getId());
    }

    @Test
    void atualizar_DeveAtualizarDadosETimestamp() {
        Estoque dadosNovos = Estoque.builder()
                .nomeMaterial("Luva de Procedimento Vinil")
                .categoria(CategoriaEstoqueEnum.EPI)
                .quantidadeAtual(15)
                .quantidadeMinima(3)
                .unidadeMedida("caixa")
                .fornecedor("Medix")
                .dataValidade(LocalDate.now().plusMonths(6))
                .observacoes("Tamanho G")
                .build();

        when(estoqueRepository.findById("est-123")).thenReturn(Optional.of(itemEstoque));
        when(estoqueRepository.save(any(Estoque.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Estoque resultado = estoqueService.atualizar("est-123", dadosNovos);

        assertEquals("Luva de Procedimento Vinil", resultado.getNomeMaterial());
        assertEquals(15, resultado.getQuantidadeAtual());
        assertEquals(3, resultado.getQuantidadeMinima());
        assertEquals("Medix", resultado.getFornecedor());
        assertNotNull(resultado.getUpdatedAt());
    }

    @Test
    void deletar_DeveRemoverFisicamente() {
        when(estoqueRepository.findById("est-123")).thenReturn(Optional.of(itemEstoque));
        doNothing().when(estoqueRepository).delete(any(Estoque.class));

        estoqueService.deletar("est-123");

        verify(estoqueRepository, times(1)).delete(itemEstoque);
    }
}

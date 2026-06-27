package com.example.demo.service;

import com.example.demo.enums.CategoriaEstoqueEnum;
import com.example.demo.model.Estoque;
import com.example.demo.repository.EstoqueRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class EstoqueService {

    @Autowired
    private EstoqueRepository estoqueRepository;

    public Estoque criar(Estoque estoque) {
        estoque.setCreatedAt(LocalDateTime.now());
        estoque.setUpdatedAt(LocalDateTime.now());
        return estoqueRepository.save(estoque);
    }

    public List<Estoque> listarTodos() {
        return estoqueRepository.findAll();
    }

    public List<Estoque> listarAbaixoDoMinimo() {
        List<Estoque> todos = estoqueRepository.findAll();
        List<Estoque> abaixoMinimo = new ArrayList<>();
        for (Estoque item : todos) {
            List<Estoque> matches = estoqueRepository.findByQuantidadeAtualLessThanEqual(item.getQuantidadeMinima());
            if (matches.stream().anyMatch(m -> m.getId().equals(item.getId()))) {
                abaixoMinimo.add(item);
            }
        }
        return abaixoMinimo;
    }

    public List<Estoque> buscarPorCategoria(CategoriaEstoqueEnum categoria) {
        return estoqueRepository.findByCategoria(categoria);
    }

    public Estoque buscarPorId(String id) {
        return estoqueRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item não encontrado no estoque."));
    }

    public Estoque atualizar(String id, Estoque dadosAtualizados) {
        Estoque estoqueExistente = estoqueRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item não encontrado no estoque."));

        estoqueExistente.setNomeMaterial(dadosAtualizados.getNomeMaterial());
        estoqueExistente.setCategoria(dadosAtualizados.getCategoria());
        estoqueExistente.setQuantidadeAtual(dadosAtualizados.getQuantidadeAtual());
        estoqueExistente.setQuantidadeMinima(dadosAtualizados.getQuantidadeMinima());
        estoqueExistente.setUnidadeMedida(dadosAtualizados.getUnidadeMedida());
        estoqueExistente.setFornecedor(dadosAtualizados.getFornecedor());
        estoqueExistente.setDataValidade(dadosAtualizados.getDataValidade());
        estoqueExistente.setObservacoes(dadosAtualizados.getObservacoes());
        estoqueExistente.setUpdatedAt(LocalDateTime.now());

        return estoqueRepository.save(estoqueExistente);
    }

    public void deletar(String id) {
        Estoque estoqueExistente = estoqueRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item não encontrado no estoque."));

        estoqueRepository.delete(estoqueExistente);
    }
}

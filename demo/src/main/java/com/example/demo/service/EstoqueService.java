package com.example.demo.service;

import com.example.demo.enums.CategoriaEstoqueEnum;
import com.example.demo.model.Estoque;
import com.example.demo.repository.EstoqueRepository;
import com.example.demo.security.AuthContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class EstoqueService {

    @Autowired
    private EstoqueRepository estoqueRepository;

    public Estoque criar(Estoque estoque) {
        estoque.setClinicaId(AuthContextHelper.currentClinicaId());
        estoque.setCreatedAt(LocalDateTime.now());
        estoque.setUpdatedAt(LocalDateTime.now());
        return estoqueRepository.save(estoque);
    }

    public List<Estoque> listarTodos() {
        return estoqueRepository.findByClinicaId(AuthContextHelper.currentClinicaId());
    }

    public List<Estoque> listarAbaixoDoMinimo() {
        return listarTodos().stream()
                .filter(e -> e.getQuantidadeMinima() != null && e.getQuantidadeAtual() != null
                        && e.getQuantidadeAtual() <= e.getQuantidadeMinima())
                .toList();
    }

    public List<Estoque> buscarPorCategoria(CategoriaEstoqueEnum categoria) {
        return estoqueRepository.findByClinicaIdAndCategoria(
                AuthContextHelper.currentClinicaId(), categoria);
    }

    public Estoque buscarPorId(String id) {
        return estoqueRepository
                .findByIdAndClinicaId(id, AuthContextHelper.currentClinicaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item não encontrado no estoque."));
    }

    public Estoque atualizar(String id, Estoque dadosAtualizados) {
        Estoque estoqueExistente = buscarPorId(id);

        estoqueExistente.setNomeMaterial(dadosAtualizados.getNomeMaterial());
        estoqueExistente.setCategoria(dadosAtualizados.getCategoria());
        estoqueExistente.setQuantidadeAtual(dadosAtualizados.getQuantidadeAtual());
        estoqueExistente.setQuantidadeMinima(dadosAtualizados.getQuantidadeMinima());
        estoqueExistente.setUnidadeMedida(dadosAtualizados.getUnidadeMedida());
        estoqueExistente.setFornecedor(dadosAtualizados.getFornecedor());
        estoqueExistente.setDataValidade(dadosAtualizados.getDataValidade());
        estoqueExistente.setValorCompra(dadosAtualizados.getValorCompra());
        estoqueExistente.setObservacoes(dadosAtualizados.getObservacoes());
        estoqueExistente.setUpdatedAt(LocalDateTime.now());

        return estoqueRepository.save(estoqueExistente);
    }

    public void deletar(String id) {
        estoqueRepository.delete(buscarPorId(id));
    }
}

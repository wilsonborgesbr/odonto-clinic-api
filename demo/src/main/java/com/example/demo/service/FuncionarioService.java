package com.example.demo.service;

import com.example.demo.model.Funcionario;
import com.example.demo.repository.FuncionarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FuncionarioService {

    @Autowired
    private FuncionarioRepository funcionarioRepository;

    public Funcionario criar(Funcionario funcionario) {
        if (funcionarioRepository.findByCpf(funcionario.getCpf()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CPF já cadastrado.");
        }

        funcionario.setAtivo(true);
        funcionario.setCreatedAt(LocalDateTime.now());
        funcionario.setUpdatedAt(LocalDateTime.now());

        return funcionarioRepository.save(funcionario);
    }

    public List<Funcionario> listarTodos() {
        return funcionarioRepository.findByAtivoTrue();
    }

    public Funcionario buscarPorId(String id) {
        return funcionarioRepository.findById(id)
                .filter(Funcionario::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Funcionário não encontrado."));
    }

    public Funcionario atualizar(String id, Funcionario dadosAtualizados) {
        Funcionario funcionarioExistente = funcionarioRepository.findById(id)
                .filter(Funcionario::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Funcionário não encontrado."));

        if (!funcionarioExistente.getCpf().equals(dadosAtualizados.getCpf())) {
            if (funcionarioRepository.findByCpf(dadosAtualizados.getCpf()).isPresent()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CPF já cadastrado.");
            }
        }

        funcionarioExistente.setNomeCompleto(dadosAtualizados.getNomeCompleto());
        funcionarioExistente.setCpf(dadosAtualizados.getCpf());
        funcionarioExistente.setCargo(dadosAtualizados.getCargo());
        funcionarioExistente.setEmail(dadosAtualizados.getEmail());
        funcionarioExistente.setTelefoneCelular(dadosAtualizados.getTelefoneCelular());
        funcionarioExistente.setUpdatedAt(LocalDateTime.now());

        return funcionarioRepository.save(funcionarioExistente);
    }

    public void deletar(String id) {
        Funcionario funcionarioExistente = funcionarioRepository.findById(id)
                .filter(Funcionario::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Funcionário não encontrado."));

        funcionarioExistente.setAtivo(false);
        funcionarioExistente.setUpdatedAt(LocalDateTime.now());

        funcionarioRepository.save(funcionarioExistente);
    }
}

package com.example.demo.service;

import com.example.demo.dto.FuncionarioListagemDTO;
import com.example.demo.model.Funcionario;
import com.example.demo.repository.FuncionarioRepository;
import com.example.demo.security.AuthContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class FuncionarioService {

    @Autowired
    private FuncionarioRepository funcionarioRepository;

    public Funcionario criar(Funcionario funcionario) {
        String clinicaId = AuthContextHelper.currentClinicaId();
        if (funcionarioRepository.findByCpfAndClinicaId(funcionario.getCpf(), clinicaId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CPF já cadastrado nesta clínica.");
        }

        funcionario.setClinicaId(clinicaId);
        funcionario.setAtivo(true);
        funcionario.setCreatedAt(LocalDateTime.now());
        funcionario.setUpdatedAt(LocalDateTime.now());

        return funcionarioRepository.save(funcionario);
    }

    public Page<FuncionarioListagemDTO> listarTodos(Pageable pageable) {
        return funcionarioRepository
                .findByClinicaIdAndAtivoTrue(AuthContextHelper.currentClinicaId(), pageable)
                .map(FuncionarioListagemDTO::new);
    }

    public Funcionario buscarPorId(String id) {
        return funcionarioRepository
                .findByIdAndClinicaId(id, AuthContextHelper.currentClinicaId())
                .filter(Funcionario::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Funcionário não encontrado."));
    }

    public Funcionario atualizar(String id, Funcionario dadosAtualizados) {
        String clinicaId = AuthContextHelper.currentClinicaId();
        Funcionario existente = funcionarioRepository.findByIdAndClinicaId(id, clinicaId)
                .filter(Funcionario::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Funcionário não encontrado."));

        if (!existente.getCpf().equals(dadosAtualizados.getCpf())
                && funcionarioRepository.findByCpfAndClinicaId(dadosAtualizados.getCpf(), clinicaId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CPF já cadastrado nesta clínica.");
        }

        existente.setNomeCompleto(dadosAtualizados.getNomeCompleto());
        existente.setCpf(dadosAtualizados.getCpf());
        existente.setCargo(dadosAtualizados.getCargo());
        existente.setEmail(dadosAtualizados.getEmail());
        existente.setTelefoneCelular(dadosAtualizados.getTelefoneCelular());
        existente.setSexo(dadosAtualizados.getSexo());
        existente.setEndereco(dadosAtualizados.getEndereco());
        existente.setUpdatedAt(LocalDateTime.now());

        return funcionarioRepository.save(existente);
    }

    public void deletar(String id) {
        Funcionario f = funcionarioRepository
                .findByIdAndClinicaId(id, AuthContextHelper.currentClinicaId())
                .filter(Funcionario::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Funcionário não encontrado."));
        f.setAtivo(false);
        f.setUpdatedAt(LocalDateTime.now());
        funcionarioRepository.save(f);
    }

    public Funcionario reativar(String id) {
        Funcionario f = funcionarioRepository
                .findByIdAndClinicaId(id, AuthContextHelper.currentClinicaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Funcionário não encontrado."));
        f.setAtivo(true);
        f.setUpdatedAt(LocalDateTime.now());
        return funcionarioRepository.save(f);
    }
}

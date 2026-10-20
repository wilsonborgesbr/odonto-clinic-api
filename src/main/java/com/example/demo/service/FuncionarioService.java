package com.example.demo.service;

import com.example.demo.dto.FuncionarioListagemDTO;
import com.example.demo.model.Funcionario;
import com.example.demo.repository.FuncionarioRepository;
import com.example.demo.security.AuthContextHelper;
import com.example.demo.util.FormatadorTexto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
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
        FormatadorTexto.normalizar(funcionario);
        String clinicaId = AuthContextHelper.currentClinicaId();
        if (cpfEmUso(funcionario.getCpf(), null, clinicaId)) {
            throw cpfDuplicado();
        }

        funcionario.setClinicaId(clinicaId);
        funcionario.setAtivo(true);
        funcionario.setCreatedAt(LocalDateTime.now());
        funcionario.setUpdatedAt(LocalDateTime.now());

        return salvar(funcionario);
    }

    private boolean cpfEmUso(String cpf, String cpfAtual, String clinicaId) {
        return cpf != null && !cpf.isBlank() && !cpf.equals(cpfAtual)
                && funcionarioRepository.findByCpfAndClinicaId(cpf, clinicaId).isPresent();
    }

    private ResponseStatusException cpfDuplicado() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "CPF já cadastrado nesta clínica.");
    }

    private Funcionario salvar(Funcionario funcionario) {
        try {
            return funcionarioRepository.save(funcionario);
        } catch (DuplicateKeyException e) {
            throw cpfDuplicado();
        }
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
        FormatadorTexto.normalizar(dadosAtualizados);
        String clinicaId = AuthContextHelper.currentClinicaId();
        Funcionario existente = funcionarioRepository.findByIdAndClinicaId(id, clinicaId)
                .filter(Funcionario::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Funcionário não encontrado."));

        if (cpfEmUso(dadosAtualizados.getCpf(), existente.getCpf(), clinicaId)) {
            throw cpfDuplicado();
        }

        existente.setNomeCompleto(dadosAtualizados.getNomeCompleto());
        existente.setCpf(dadosAtualizados.getCpf());
        existente.setCargo(dadosAtualizados.getCargo());
        existente.setEmail(dadosAtualizados.getEmail());
        existente.setTelefoneCelular(dadosAtualizados.getTelefoneCelular());
        existente.setSexo(dadosAtualizados.getSexo());
        existente.setEndereco(dadosAtualizados.getEndereco());
        existente.setUpdatedAt(LocalDateTime.now());

        return salvar(existente);
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

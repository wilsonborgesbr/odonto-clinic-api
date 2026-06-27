package com.example.demo.service;

import com.example.demo.model.Convenio;
import com.example.demo.repository.ConvenioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ConvenioService {

    @Autowired
    private ConvenioRepository convenioRepository;

    public Convenio criar(Convenio convenio) {
        if (convenioRepository.findByCnpj(convenio.getCnpj()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CNPJ já cadastrado.");
        }

        convenio.setAtivo(true);
        convenio.setCreatedAt(LocalDateTime.now());
        convenio.setUpdatedAt(LocalDateTime.now());

        return convenioRepository.save(convenio);
    }

    public List<Convenio> listarTodos() {
        return convenioRepository.findByAtivoTrue();
    }

    public Convenio buscarPorId(String id) {
        return convenioRepository.findById(id)
                .filter(Convenio::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Convênio não encontrado."));
    }

    public Convenio atualizar(String id, Convenio dadosAtualizados) {
        Convenio convenioExistente = convenioRepository.findById(id)
                .filter(Convenio::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Convênio não encontrado."));

        if (!convenioExistente.getCnpj().equals(dadosAtualizados.getCnpj())) {
            if (convenioRepository.findByCnpj(dadosAtualizados.getCnpj()).isPresent()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CNPJ já cadastrado.");
            }
        }

        convenioExistente.setNome(dadosAtualizados.getNome());
        convenioExistente.setCnpj(dadosAtualizados.getCnpj());
        convenioExistente.setTelefone(dadosAtualizados.getTelefone());
        convenioExistente.setEmail(dadosAtualizados.getEmail());
        convenioExistente.setTabelaDePrecos(dadosAtualizados.getTabelaDePrecos());
        convenioExistente.setUpdatedAt(LocalDateTime.now());

        return convenioRepository.save(convenioExistente);
    }

    public void deletar(String id) {
        Convenio convenioExistente = convenioRepository.findById(id)
                .filter(Convenio::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Convênio não encontrado."));

        convenioExistente.setAtivo(false);
        convenioExistente.setUpdatedAt(LocalDateTime.now());

        convenioRepository.save(convenioExistente);
    }
}

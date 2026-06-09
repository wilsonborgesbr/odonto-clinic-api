package com.example.demo.service;

import com.example.demo.model.Dentista;
import com.example.demo.repository.DentistaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DentistaService {

    @Autowired
    private DentistaRepository dentistaRepository;

    public Dentista criar(Dentista dentista) {
        if (dentistaRepository.findByCro(dentista.getCro()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CRO já cadastrado.");
        }

        dentista.setAtivo(true);
        dentista.setCreatedAt(LocalDateTime.now());
        dentista.setUpdatedAt(LocalDateTime.now());

        return dentistaRepository.save(dentista);
    }

    public List<Dentista> listarTodos() {
        return dentistaRepository.findByAtivoTrue();
    }

    public Dentista buscarPorId(String id) {
        return dentistaRepository.findById(id)
                .filter(Dentista::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dentista não encontrado."));
    }

    public Dentista atualizar(String id, Dentista dadosAtualizados) {
        Dentista dentistaExistente = dentistaRepository.findById(id)
                .filter(Dentista::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dentista não encontrado."));

        if (!dentistaExistente.getCro().equals(dadosAtualizados.getCro())) {
            if (dentistaRepository.findByCro(dadosAtualizados.getCro()).isPresent()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CRO já cadastrado.");
            }
        }

        dentistaExistente.setNomeCompleto(dadosAtualizados.getNomeCompleto());
        dentistaExistente.setCro(dadosAtualizados.getCro());
        dentistaExistente.setEspecialidades(dadosAtualizados.getEspecialidades());
        dentistaExistente.setEmail(dadosAtualizados.getEmail());
        dentistaExistente.setTelefoneCelular(dadosAtualizados.getTelefoneCelular());
        dentistaExistente.setUpdatedAt(LocalDateTime.now());

        return dentistaRepository.save(dentistaExistente);
    }

    public void deletar(String id) {
        Dentista dentistaExistente = dentistaRepository.findById(id)
                .filter(Dentista::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dentista não encontrado."));

        dentistaExistente.setAtivo(false);
        dentistaExistente.setUpdatedAt(LocalDateTime.now());

        dentistaRepository.save(dentistaExistente);
    }
}

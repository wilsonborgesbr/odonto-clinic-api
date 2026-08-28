package com.example.demo.service;

import com.example.demo.dto.DentistaListagemDTO;
import com.example.demo.model.Dentista;
import com.example.demo.repository.DentistaRepository;
import com.example.demo.security.AuthContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class DentistaService {

    @Autowired
    private DentistaRepository dentistaRepository;

    public Dentista criar(Dentista dentista) {
        String clinicaId = AuthContextHelper.currentClinicaId();
        if (dentistaRepository.findByCroAndClinicaId(dentista.getCro(), clinicaId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CRO já cadastrado nesta clínica.");
        }

        dentista.setClinicaId(clinicaId);
        dentista.setAtivo(true);
        dentista.setCreatedAt(LocalDateTime.now());
        dentista.setUpdatedAt(LocalDateTime.now());

        return dentistaRepository.save(dentista);
    }

    public Page<DentistaListagemDTO> listarTodos(Pageable pageable) {
        return dentistaRepository
                .findByClinicaIdAndAtivoTrue(AuthContextHelper.currentClinicaId(), pageable)
                .map(DentistaListagemDTO::new);
    }

    public Dentista buscarPorId(String id) {
        return dentistaRepository
                .findByIdAndClinicaId(id, AuthContextHelper.currentClinicaId())
                .filter(Dentista::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dentista não encontrado."));
    }

    public Dentista atualizar(String id, Dentista dadosAtualizados) {
        String clinicaId = AuthContextHelper.currentClinicaId();
        Dentista dentistaExistente = dentistaRepository.findByIdAndClinicaId(id, clinicaId)
                .filter(Dentista::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dentista não encontrado."));

        if (!dentistaExistente.getCro().equals(dadosAtualizados.getCro())
                && dentistaRepository.findByCroAndClinicaId(dadosAtualizados.getCro(), clinicaId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CRO já cadastrado nesta clínica.");
        }

        dentistaExistente.setNomeCompleto(dadosAtualizados.getNomeCompleto());
        dentistaExistente.setCro(dadosAtualizados.getCro());
        dentistaExistente.setSexo(dadosAtualizados.getSexo());
        dentistaExistente.setEspecialidades(dadosAtualizados.getEspecialidades());
        dentistaExistente.setEmail(dadosAtualizados.getEmail());
        dentistaExistente.setTelefoneCelular(dadosAtualizados.getTelefoneCelular());
        dentistaExistente.setEndereco(dadosAtualizados.getEndereco());
        dentistaExistente.setUpdatedAt(LocalDateTime.now());

        return dentistaRepository.save(dentistaExistente);
    }

    public void deletar(String id) {
        Dentista d = dentistaRepository
                .findByIdAndClinicaId(id, AuthContextHelper.currentClinicaId())
                .filter(Dentista::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dentista não encontrado."));
        d.setAtivo(false);
        d.setUpdatedAt(LocalDateTime.now());
        dentistaRepository.save(d);
    }

    public Dentista reativar(String id) {
        Dentista d = dentistaRepository
                .findByIdAndClinicaId(id, AuthContextHelper.currentClinicaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dentista não encontrado."));
        d.setAtivo(true);
        d.setUpdatedAt(LocalDateTime.now());
        return dentistaRepository.save(d);
    }
}

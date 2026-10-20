package com.example.demo.service;

import com.example.demo.dto.DentistaListagemDTO;
import com.example.demo.model.Dentista;
import com.example.demo.repository.DentistaRepository;
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
public class DentistaService {

    @Autowired
    private DentistaRepository dentistaRepository;

    public Dentista criar(Dentista dentista) {
        FormatadorTexto.normalizar(dentista);
        String clinicaId = AuthContextHelper.currentClinicaId();
        validarDuplicidade(dentista, null, clinicaId);

        dentista.setClinicaId(clinicaId);
        dentista.setAtivo(true);
        dentista.setCreatedAt(LocalDateTime.now());
        dentista.setUpdatedAt(LocalDateTime.now());

        return salvar(dentista);
    }

    private void validarDuplicidade(Dentista novo, Dentista existente, String clinicaId) {
        if (valorNovoEmUso(novo.getCro(), existente == null ? null : existente.getCro())
                && dentistaRepository.findByCroAndClinicaId(novo.getCro(), clinicaId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CRO já cadastrado nesta clínica.");
        }
        if (valorNovoEmUso(novo.getCpf(), existente == null ? null : existente.getCpf())
                && dentistaRepository.findByCpfAndClinicaId(novo.getCpf(), clinicaId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CPF já cadastrado nesta clínica.");
        }
    }

    private boolean valorNovoEmUso(String novo, String atual) {
        return novo != null && !novo.isBlank() && !novo.equals(atual);
    }

    private Dentista salvar(Dentista dentista) {
        try {
            return dentistaRepository.save(dentista);
        } catch (DuplicateKeyException e) {
            boolean cpf = e.getMessage() != null && e.getMessage().contains("cpf");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    (cpf ? "CPF" : "CRO") + " já cadastrado nesta clínica.");
        }
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
        FormatadorTexto.normalizar(dadosAtualizados);
        String clinicaId = AuthContextHelper.currentClinicaId();
        Dentista dentistaExistente = dentistaRepository.findByIdAndClinicaId(id, clinicaId)
                .filter(Dentista::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dentista não encontrado."));

        validarDuplicidade(dadosAtualizados, dentistaExistente, clinicaId);

        dentistaExistente.setNomeCompleto(dadosAtualizados.getNomeCompleto());
        dentistaExistente.setCro(dadosAtualizados.getCro());
        dentistaExistente.setCpf(dadosAtualizados.getCpf());
        dentistaExistente.setSexo(dadosAtualizados.getSexo());
        dentistaExistente.setEspecialidades(dadosAtualizados.getEspecialidades());
        dentistaExistente.setEmail(dadosAtualizados.getEmail());
        dentistaExistente.setTelefoneCelular(dadosAtualizados.getTelefoneCelular());
        dentistaExistente.setEndereco(dadosAtualizados.getEndereco());
        dentistaExistente.setUpdatedAt(LocalDateTime.now());

        return salvar(dentistaExistente);
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

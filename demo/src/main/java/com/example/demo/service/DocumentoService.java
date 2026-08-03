package com.example.demo.service;

import com.example.demo.model.Documento;
import com.example.demo.model.Paciente;
import com.example.demo.repository.DocumentoRepository;
import com.example.demo.repository.PacienteRepository;
import com.example.demo.security.AuthContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DocumentoService {

    @Autowired
    private DocumentoRepository documentoRepository;

    @Autowired
    private PacienteRepository pacienteRepository;

    public List<Documento> listarTodos() {
        return documentoRepository.findByClinicaId(
                AuthContextHelper.currentClinicaId(),
                Sort.by(Sort.Direction.DESC, "dataUpload"));
    }

    public Documento criar(Documento documento) {
        String clinicaId = AuthContextHelper.currentClinicaId();
        if (documento.getPacienteId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID do paciente não pode ser nulo.");
        }

        pacienteRepository.findByIdAndClinicaId(documento.getPacienteId(), clinicaId)
                .filter(Paciente::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paciente não encontrado ou inativo."));

        documento.setClinicaId(clinicaId);
        documento.setDataUpload(LocalDateTime.now());
        return documentoRepository.save(documento);
    }

    public Documento buscarPorId(String id) {
        return documentoRepository
                .findByIdAndClinicaId(id, AuthContextHelper.currentClinicaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento não encontrado."));
    }

    public List<Documento> listarPorPaciente(String pacienteId) {
        return documentoRepository.findByClinicaIdAndPacienteId(
                AuthContextHelper.currentClinicaId(), pacienteId);
    }

    public void deletar(String id) {
        documentoRepository.delete(buscarPorId(id));
    }
}

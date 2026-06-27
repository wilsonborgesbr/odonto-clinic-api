package com.example.demo.service;

import com.example.demo.model.Documento;
import com.example.demo.model.Paciente;
import com.example.demo.repository.DocumentoRepository;
import com.example.demo.repository.PacienteRepository;
import org.springframework.beans.factory.annotation.Autowired;
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

    public Documento criar(Documento documento) {
        if (documento.getPacienteId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID do paciente não pode ser nulo.");
        }

        // Valida se o paciente existe e está ativo
        pacienteRepository.findById(documento.getPacienteId())
                .filter(Paciente::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paciente não encontrado ou inativo."));

        documento.setDataUpload(LocalDateTime.now());
        return documentoRepository.save(documento);
    }

    public Documento buscarPorId(String id) {
        return documentoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento não encontrado."));
    }

    public List<Documento> listarPorPaciente(String pacienteId) {
        return documentoRepository.findByPacienteId(pacienteId);
    }

    public void deletar(String id) {
        Documento documento = documentoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento não encontrado."));
        documentoRepository.delete(documento);
    }
}

package com.example.demo.service;

import com.example.demo.model.Odontograma;
import com.example.demo.repository.OdontogramaRepository;
import com.example.demo.repository.PacienteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class OdontogramaService {

    @Autowired
    private OdontogramaRepository odontogramaRepository;

    @Autowired
    private PacienteRepository pacienteRepository;

    public Odontograma criar(Odontograma odontograma) {
        // Valida se o pacienteId existe e está ativo no PacienteRepository
        if (odontograma.getPacienteId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID do paciente não pode ser nulo.");
        }

        var pacienteOpt = pacienteRepository.findById(odontograma.getPacienteId());
        if (pacienteOpt.isEmpty() || !Boolean.TRUE.equals(pacienteOpt.get().getAtivo())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente não encontrado.");
        }

        // Preenche campos automaticamente
        odontograma.setDataAvaliacao(LocalDate.now());
        odontograma.setCreatedAt(LocalDateTime.now());

        return odontogramaRepository.save(odontograma);
    }

    public List<Odontograma> listarPorPacienteId(String pacienteId) {
        return odontogramaRepository.findByPacienteId(pacienteId);
    }

    public Odontograma buscarMaisRecentePorPacienteId(String pacienteId) {
        return odontogramaRepository.findTopByPacienteIdOrderByCreatedAtDesc(pacienteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nenhum odontograma encontrado para o paciente informado."));
    }

    public Odontograma buscarPorId(String id) {
        return odontogramaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Odontograma não encontrado."));
    }
}

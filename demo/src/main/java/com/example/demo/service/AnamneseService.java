package com.example.demo.service;

import com.example.demo.model.Anamnese;
import com.example.demo.repository.AnamneseRepository;
import com.example.demo.repository.PacienteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AnamneseService {

    @Autowired
    private AnamneseRepository anamneseRepository;

    @Autowired
    private PacienteRepository pacienteRepository;

    public Anamnese criar(Anamnese anamnese) {
        // Valida se o pacienteId existe e está ativo no PacienteRepository
        if (anamnese.getPacienteId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID do paciente não pode ser nulo.");
        }

        var pacienteOpt = pacienteRepository.findById(anamnese.getPacienteId());
        if (pacienteOpt.isEmpty() || !Boolean.TRUE.equals(pacienteOpt.get().getAtivo())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente não encontrado.");
        }

        // Preenche campos automaticamente
        anamnese.setDataPreenchimento(LocalDate.now());
        anamnese.setCreatedAt(LocalDateTime.now());

        return anamneseRepository.save(anamnese);
    }

    public List<Anamnese> listarPorPacienteId(String pacienteId) {
        return anamneseRepository.findByPacienteId(pacienteId);
    }

    public Anamnese buscarMaisRecentePorPacienteId(String pacienteId) {
        return anamneseRepository.findTopByPacienteIdOrderByCreatedAtDesc(pacienteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nenhuma anamnese encontrada para o paciente informado."));
    }

    public Anamnese buscarPorId(String id) {
        return anamneseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Anamnese não encontrada."));
    }
}

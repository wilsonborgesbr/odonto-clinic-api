package com.example.demo.service;

import com.example.demo.enums.StatusAgendamentoEnum;
import com.example.demo.model.Agendamento;
import com.example.demo.repository.AgendamentoRepository;
import com.example.demo.repository.DentistaRepository;
import com.example.demo.repository.PacienteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AgendamentoService {

    @Autowired
    private AgendamentoRepository agendamentoRepository;

    @Autowired
    private PacienteRepository pacienteRepository;

    @Autowired
    private DentistaRepository dentistaRepository;

    public Agendamento criar(Agendamento agendamento) {
        pacienteRepository.findById(agendamento.getPacienteId())
                .filter(p -> Boolean.TRUE.equals(p.getAtivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paciente não encontrado."));

        dentistaRepository.findById(agendamento.getDentistaId())
                .filter(d -> Boolean.TRUE.equals(d.getAtivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dentista não encontrado."));

        List<Agendamento> conflitos = agendamentoRepository.findByDentistaIdAndDataHoraInicioBetween(
                agendamento.getDentistaId(),
                agendamento.getDataHoraInicio(),
                agendamento.getDataHoraFim()
        );

        if (!conflitos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dentista já possui agendamento neste horário.");
        }

        agendamento.setStatus(StatusAgendamentoEnum.AGENDADO);
        agendamento.setCreatedAt(LocalDateTime.now());
        agendamento.setUpdatedAt(LocalDateTime.now());

        return agendamentoRepository.save(agendamento);
    }

    public List<Agendamento> listarTodos() {
        return agendamentoRepository.findAll();
    }

    public List<Agendamento> listarPorPaciente(String pacienteId) {
        return agendamentoRepository.findByPacienteId(pacienteId);
    }

    public List<Agendamento> listarPorDentista(String dentistaId) {
        return agendamentoRepository.findByDentistaId(dentistaId);
    }

    public List<Agendamento> listarPorStatus(StatusAgendamentoEnum status) {
        return agendamentoRepository.findByStatus(status);
    }

    public Agendamento buscarPorId(String id) {
        return agendamentoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agendamento não encontrado."));
    }

    public Agendamento atualizar(String id, Agendamento dadosAtualizados) {
        Agendamento agendamentoExistente = agendamentoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agendamento não encontrado."));

        agendamentoExistente.setPacienteId(dadosAtualizados.getPacienteId());
        agendamentoExistente.setDentistaId(dadosAtualizados.getDentistaId());
        agendamentoExistente.setProcedimentoId(dadosAtualizados.getProcedimentoId());
        agendamentoExistente.setDataHoraInicio(dadosAtualizados.getDataHoraInicio());
        agendamentoExistente.setDataHoraFim(dadosAtualizados.getDataHoraFim());
        agendamentoExistente.setStatus(dadosAtualizados.getStatus());
        agendamentoExistente.setObservacoes(dadosAtualizados.getObservacoes());
        agendamentoExistente.setUpdatedAt(LocalDateTime.now());

        return agendamentoRepository.save(agendamentoExistente);
    }

    public void deletar(String id) {
        Agendamento agendamentoExistente = agendamentoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agendamento não encontrado."));

        agendamentoRepository.delete(agendamentoExistente);
    }
}

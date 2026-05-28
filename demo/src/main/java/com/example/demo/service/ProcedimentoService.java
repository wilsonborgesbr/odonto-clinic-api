package com.example.demo.service;

import com.example.demo.enums.StatusProcedimentoEnum;
import com.example.demo.model.Procedimento;
import com.example.demo.repository.PacienteRepository;
import com.example.demo.repository.ProcedimentoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProcedimentoService {

    @Autowired
    private ProcedimentoRepository procedimentoRepository;

    @Autowired
    private PacienteRepository pacienteRepository;

    public Procedimento criar(Procedimento procedimento) {
        // Valida se o pacienteId informado existe e está ativo no banco
        var pacienteOpt = pacienteRepository.findById(procedimento.getPacienteId());
        if (pacienteOpt.isEmpty() || !Boolean.TRUE.equals(pacienteOpt.get().getAtivo())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente não encontrado.");
        }

        // Configura valores padrão caso não informados
        if (procedimento.getStatus() == null) {
            procedimento.setStatus(StatusProcedimentoEnum.ORCADO);
        }
        if (procedimento.getSessaoAtual() == null) {
            procedimento.setSessaoAtual(1);
        }

        // Auditoria
        procedimento.setCreatedAt(LocalDateTime.now());
        procedimento.setUpdatedAt(LocalDateTime.now());

        return procedimentoRepository.save(procedimento);
    }

    public List<Procedimento> listarPorPaciente(String pacienteId) {
        return procedimentoRepository.findByPacienteId(pacienteId);
    }

    public List<Procedimento> listarPorPacienteEStatus(String pacienteId, StatusProcedimentoEnum status) {
        return procedimentoRepository.findByPacienteIdAndStatus(pacienteId, status);
    }

    public Procedimento buscarPorId(String id) {
        return procedimentoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Procedimento não encontrado."));
    }

    public Procedimento atualizar(String id, Procedimento dadosAtualizados) {
        Procedimento procedimentoExistente = procedimentoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Procedimento não encontrado."));

        // Se o pacienteId for alterado, valida a existência e atividade do novo paciente
        if (dadosAtualizados.getPacienteId() != null && !procedimentoExistente.getPacienteId().equals(dadosAtualizados.getPacienteId())) {
            var pacienteOpt = pacienteRepository.findById(dadosAtualizados.getPacienteId());
            if (pacienteOpt.isEmpty() || !Boolean.TRUE.equals(pacienteOpt.get().getAtivo())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente não encontrado.");
            }
            procedimentoExistente.setPacienteId(dadosAtualizados.getPacienteId());
        }

        // Atualização de campos clínicos e administrativos
        procedimentoExistente.setNomeProcedimento(dadosAtualizados.getNomeProcedimento());
        procedimentoExistente.setDescricao(dadosAtualizados.getDescricao());
        procedimentoExistente.setDente(dadosAtualizados.getDente());
        procedimentoExistente.setRegiao(dadosAtualizados.getRegiao());
        procedimentoExistente.setStatus(dadosAtualizados.getStatus());
        procedimentoExistente.setDataRealizacao(dadosAtualizados.getDataRealizacao());
        procedimentoExistente.setValor(dadosAtualizados.getValor());
        procedimentoExistente.setNumeroDeSessoes(dadosAtualizados.getNumeroDeSessoes());
        procedimentoExistente.setSessaoAtual(dadosAtualizados.getSessaoAtual());
        procedimentoExistente.setObservacoesTecnicas(dadosAtualizados.getObservacoesTecnicas());

        // Atualiza a data de modificação
        procedimentoExistente.setUpdatedAt(LocalDateTime.now());

        return procedimentoRepository.save(procedimentoExistente);
    }

    public void deletar(String id) {
        if (!procedimentoRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Procedimento não encontrado.");
        }
        procedimentoRepository.deleteById(id);
    }
}

package com.example.demo.service;

import com.example.demo.enums.StatusAgendamentoEnum;
import com.example.demo.enums.StatusProcedimentoEnum;
import com.example.demo.enums.TipoAgendamentoEnum;
import com.example.demo.model.Agendamento;
import com.example.demo.model.Procedimento;
import com.example.demo.repository.AgendamentoRepository;
import com.example.demo.repository.DentistaRepository;
import com.example.demo.repository.PacienteRepository;
import com.example.demo.security.AuthContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Autowired
    private ProcedimentoService procedimentoService;

    public Agendamento criar(Agendamento agendamento) {
        String clinicaId = AuthContextHelper.currentClinicaId();

        pacienteRepository.findByIdAndClinicaId(agendamento.getPacienteId(), clinicaId)
                .filter(p -> Boolean.TRUE.equals(p.getAtivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paciente não encontrado."));

        dentistaRepository.findByIdAndClinicaId(agendamento.getDentistaId(), clinicaId)
                .filter(d -> Boolean.TRUE.equals(d.getAtivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dentista não encontrado."));

        List<Agendamento> conflitos = agendamentoRepository.findConflitos(
                clinicaId,
                agendamento.getDentistaId(),
                agendamento.getDataHoraInicio(),
                agendamento.getDataHoraFim()
        );

        if (!conflitos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dentista já possui agendamento neste horário.");
        }

        agendamento.setClinicaId(clinicaId);
        agendamento.setStatus(StatusAgendamentoEnum.AGENDADO);
        agendamento.setCreatedAt(LocalDateTime.now());
        agendamento.setUpdatedAt(LocalDateTime.now());

        if (agendamento.getTipoAgendamento() == null) {
            agendamento.setTipoAgendamento(TipoAgendamentoEnum.AVALIACAO);
        }

        Agendamento salvo = agendamentoRepository.save(agendamento);

        // Integração: agendamento tipo PROCEDIMENTO gera Procedimento automático.
        if (salvo.getTipoAgendamento() == TipoAgendamentoEnum.PROCEDIMENTO
                && salvo.getNomeProcedimento() != null
                && salvo.getProcedimentoId() == null) {
            Procedimento proc = new Procedimento();
            proc.setPacienteId(salvo.getPacienteId());
            proc.setNomeProcedimento(salvo.getNomeProcedimento());
            proc.setStatus(StatusProcedimentoEnum.AGENDADO);
            proc.setDataRealizacao(salvo.getDataHoraInicio().toLocalDate());
            proc.setNumeroDeSessoes(1);
            proc.setSessaoAtual(1);
            proc.setValor(salvo.getValor());
            proc.setTipoPagamento(salvo.getTipoPagamento());
            proc.setNumeroParcelas(salvo.getNumeroParcelas());
            proc.setDataPrimeiroPagamento(salvo.getDataPrimeiroPagamento());

            Procedimento procSalvo = procedimentoService.criar(proc);
            salvo.setProcedimentoId(procSalvo.getId());
            salvo = agendamentoRepository.save(salvo);
        }

        return salvo;
    }

    public Page<Agendamento> listarTodos(Pageable pageable) {
        return agendamentoRepository.findByClinicaId(AuthContextHelper.currentClinicaId(), pageable);
    }

    public List<Agendamento> listarPorPaciente(String pacienteId) {
        return agendamentoRepository.findByClinicaIdAndPacienteId(
                AuthContextHelper.currentClinicaId(), pacienteId);
    }

    public List<Agendamento> listarPorDentista(String dentistaId) {
        return agendamentoRepository.findByClinicaIdAndDentistaId(
                AuthContextHelper.currentClinicaId(), dentistaId);
    }

    public List<Agendamento> listarPorStatus(StatusAgendamentoEnum status) {
        return agendamentoRepository.findByClinicaIdAndStatus(
                AuthContextHelper.currentClinicaId(), status);
    }

    public Agendamento buscarPorId(String id) {
        return agendamentoRepository.findByIdAndClinicaId(id, AuthContextHelper.currentClinicaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agendamento não encontrado."));
    }

    public Agendamento atualizar(String id, Agendamento dadosAtualizados) {
        Agendamento existente = agendamentoRepository
                .findByIdAndClinicaId(id, AuthContextHelper.currentClinicaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agendamento não encontrado."));

        existente.setPacienteId(dadosAtualizados.getPacienteId());
        existente.setDentistaId(dadosAtualizados.getDentistaId());
        existente.setProcedimentoId(dadosAtualizados.getProcedimentoId());
        existente.setDataHoraInicio(dadosAtualizados.getDataHoraInicio());
        existente.setDataHoraFim(dadosAtualizados.getDataHoraFim());
        existente.setStatus(dadosAtualizados.getStatus());
        existente.setObservacoes(dadosAtualizados.getObservacoes());
        existente.setUpdatedAt(LocalDateTime.now());

        return agendamentoRepository.save(existente);
    }

    public void deletar(String id) {
        Agendamento existente = agendamentoRepository
                .findByIdAndClinicaId(id, AuthContextHelper.currentClinicaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agendamento não encontrado."));
        agendamentoRepository.delete(existente);
    }
}

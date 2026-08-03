package com.example.demo.service;

import com.example.demo.enums.FormaPagamentoEnum;
import com.example.demo.enums.StatusFinanceiroEnum;
import com.example.demo.enums.StatusProcedimentoEnum;
import com.example.demo.enums.TipoPagamentoProcedimentoEnum;
import com.example.demo.model.ContaReceber;
import com.example.demo.model.Paciente;
import com.example.demo.model.Procedimento;
import com.example.demo.repository.ContaReceberRepository;
import com.example.demo.repository.OdontogramaRepository;
import com.example.demo.repository.PacienteRepository;
import com.example.demo.repository.ProcedimentoRepository;
import com.example.demo.security.AuthContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProcedimentoService {

    @Autowired
    private ProcedimentoRepository procedimentoRepository;

    @Autowired
    private PacienteRepository pacienteRepository;

    @Autowired
    private ContaReceberRepository contaReceberRepository;

    @Autowired
    private OdontogramaRepository odontogramaRepository;

    public Procedimento criar(Procedimento procedimento) {
        String clinicaId = AuthContextHelper.currentClinicaId();

        var pacienteOpt = pacienteRepository.findByIdAndClinicaId(procedimento.getPacienteId(), clinicaId);
        if (pacienteOpt.isEmpty() || !Boolean.TRUE.equals(pacienteOpt.get().getAtivo())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente não encontrado.");
        }
        Paciente paciente = pacienteOpt.get();

        // Se veio um odontogramaId, valida que ele existe na mesma clínica e é do mesmo paciente
        if (procedimento.getOdontogramaId() != null && !procedimento.getOdontogramaId().isBlank()) {
            var odonto = odontogramaRepository
                    .findByIdAndClinicaId(procedimento.getOdontogramaId(), clinicaId);
            if (odonto.isEmpty() || !procedimento.getPacienteId().equals(odonto.get().getPacienteId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Odontograma vinculado inválido para este paciente.");
            }
        } else {
            procedimento.setOdontogramaId(null);
        }

        procedimento.setClinicaId(clinicaId);

        if (procedimento.getStatus() == null) {
            procedimento.setStatus(StatusProcedimentoEnum.ORCADO);
        }
        if (procedimento.getSessaoAtual() == null) {
            procedimento.setSessaoAtual(1);
        }

        procedimento.setCreatedAt(LocalDateTime.now());
        procedimento.setUpdatedAt(LocalDateTime.now());

        Procedimento salvo = procedimentoRepository.save(procedimento);

        // Integração automática com Contas a Receber:
        // se o procedimento tem valor > 0, data de primeiro pagamento e o
        // paciente NÃO é conveniado, gera 1..N ContaReceber (uma por parcela)
        // com vencimentos mensais a partir da data escolhida.
        if (deveGerarContasReceber(salvo, paciente)) {
            gerarContasReceber(salvo);
        }

        return salvo;
    }

    private boolean deveGerarContasReceber(Procedimento p, Paciente paciente) {
        if (p.getValor() == null || p.getValor() <= 0) return false;
        if (p.getDataPrimeiroPagamento() == null) return false;
        // Conveniado cobra via convênio — não geramos ContaReceber automática
        if (paciente.getTipoPaciente() != null
                && paciente.getTipoPaciente() == Paciente.TipoPaciente.CONVENIO) {
            return false;
        }
        return true;
    }

    private void gerarContasReceber(Procedimento p) {
        int parcelas = (p.getTipoPagamento() == TipoPagamentoProcedimentoEnum.PARCELADO
                && p.getNumeroParcelas() != null && p.getNumeroParcelas() > 1)
                ? p.getNumeroParcelas()
                : 1;

        double valorParcela = Math.round((p.getValor() / parcelas) * 100.0) / 100.0;
        LocalDate primeiroVencimento = p.getDataPrimeiroPagamento();

        for (int i = 0; i < parcelas; i++) {
            ContaReceber cr = new ContaReceber();
            cr.setClinicaId(p.getClinicaId());
            cr.setPacienteId(p.getPacienteId());
            cr.setProcedimentoId(p.getId());
            cr.setDescricao(parcelas > 1
                    ? String.format("Procedimento %s · parcela %d/%d",
                            p.getNomeProcedimento() != null ? p.getNomeProcedimento().name() : "",
                            i + 1, parcelas)
                    : "Procedimento " + (p.getNomeProcedimento() != null ? p.getNomeProcedimento().name() : ""));
            cr.setValorTotal(valorParcela);
            cr.setValorPago(0.0);
            cr.setFormaPagamento(FormaPagamentoEnum.DINHEIRO);
            cr.setTipoPagamento(p.getTipoPagamento() != null
                    ? p.getTipoPagamento()
                    : TipoPagamentoProcedimentoEnum.A_VISTA);
            cr.setNumeroParcelas(parcelas);
            cr.setParcelaAtual(i + 1);
            cr.setDataVencimento(primeiroVencimento.plusMonths(i));
            cr.setStatus(StatusFinanceiroEnum.PENDENTE);
            cr.setCreatedAt(LocalDateTime.now());
            cr.setUpdatedAt(LocalDateTime.now());
            contaReceberRepository.save(cr);
        }
    }

    public List<Procedimento> listarTodos() {
        return procedimentoRepository.findByClinicaId(AuthContextHelper.currentClinicaId());
    }

    public List<Procedimento> listarPorPaciente(String pacienteId) {
        return procedimentoRepository.findByClinicaIdAndPacienteId(
                AuthContextHelper.currentClinicaId(), pacienteId);
    }

    public List<Procedimento> listarPorPacienteEStatus(String pacienteId, StatusProcedimentoEnum status) {
        return procedimentoRepository.findByClinicaIdAndPacienteIdAndStatus(
                AuthContextHelper.currentClinicaId(), pacienteId, status);
    }

    public Procedimento buscarPorId(String id) {
        return procedimentoRepository
                .findByIdAndClinicaId(id, AuthContextHelper.currentClinicaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Procedimento não encontrado."));
    }

    public Procedimento atualizar(String id, Procedimento dadosAtualizados) {
        String clinicaId = AuthContextHelper.currentClinicaId();
        Procedimento procedimentoExistente = procedimentoRepository
                .findByIdAndClinicaId(id, clinicaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Procedimento não encontrado."));

        if (dadosAtualizados.getPacienteId() != null && !procedimentoExistente.getPacienteId().equals(dadosAtualizados.getPacienteId())) {
            var pacienteOpt = pacienteRepository.findByIdAndClinicaId(dadosAtualizados.getPacienteId(), clinicaId);
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
        procedimentoExistente.setTipoPagamento(dadosAtualizados.getTipoPagamento());
        procedimentoExistente.setNumeroParcelas(dadosAtualizados.getNumeroParcelas());
        procedimentoExistente.setDataPrimeiroPagamento(dadosAtualizados.getDataPrimeiroPagamento());
        procedimentoExistente.setNumeroDeSessoes(dadosAtualizados.getNumeroDeSessoes());
        procedimentoExistente.setSessaoAtual(dadosAtualizados.getSessaoAtual());
        procedimentoExistente.setObservacoesTecnicas(dadosAtualizados.getObservacoesTecnicas());

        // Vínculo opcional com odontograma
        if (dadosAtualizados.getOdontogramaId() != null && !dadosAtualizados.getOdontogramaId().isBlank()) {
            var odonto = odontogramaRepository
                    .findByIdAndClinicaId(dadosAtualizados.getOdontogramaId(), clinicaId);
            if (odonto.isEmpty()
                    || !procedimentoExistente.getPacienteId().equals(odonto.get().getPacienteId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Odontograma vinculado inválido para este paciente.");
            }
            procedimentoExistente.setOdontogramaId(dadosAtualizados.getOdontogramaId());
        } else {
            procedimentoExistente.setOdontogramaId(null);
        }

        // Atualiza a data de modificação
        procedimentoExistente.setUpdatedAt(LocalDateTime.now());

        return procedimentoRepository.save(procedimentoExistente);
    }

    public void deletar(String id) {
        Procedimento proc = procedimentoRepository
                .findByIdAndClinicaId(id, AuthContextHelper.currentClinicaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Procedimento não encontrado."));
        procedimentoRepository.delete(proc);
    }
}

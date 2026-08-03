package com.example.demo.service;

import com.example.demo.enums.StatusFinanceiroEnum;
import com.example.demo.enums.TipoPagamentoProcedimentoEnum;
import com.example.demo.model.ContaReceber;
import com.example.demo.model.Paciente;
import com.example.demo.repository.ContaReceberRepository;
import com.example.demo.repository.PacienteRepository;
import com.example.demo.security.AuthContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ContaReceberService {

    @Autowired
    private ContaReceberRepository contaReceberRepository;

    @Autowired
    private PacienteRepository pacienteRepository;

    public ContaReceber criar(ContaReceber conta) {
        String clinicaId = AuthContextHelper.currentClinicaId();

        if (conta.getPacienteId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID do paciente é obrigatório.");
        }

        pacienteRepository.findByIdAndClinicaId(conta.getPacienteId(), clinicaId)
                .filter(Paciente::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente não encontrado."));

        conta.setClinicaId(clinicaId);
        conta.setValorPago(0.0);
        conta.setCreatedAt(LocalDateTime.now());
        conta.setUpdatedAt(LocalDateTime.now());

        if (conta.getStatus() == null) {
            conta.setStatus(StatusFinanceiroEnum.PENDENTE);
        }
        if (conta.getNumeroParcelas() == null) {
            conta.setNumeroParcelas(1);
        }

        boolean deveDividir = conta.getTipoPagamento() == TipoPagamentoProcedimentoEnum.PARCELADO
                && conta.getNumeroParcelas() != null
                && conta.getNumeroParcelas() > 1
                && conta.getValorTotal() != null
                && conta.getDataVencimento() != null;

        if (deveDividir) {
            int parcelas = conta.getNumeroParcelas();
            double valorParcela = Math.round((conta.getValorTotal() / parcelas) * 100.0) / 100.0;
            LocalDate primeiroVencimento = conta.getDataVencimento();

            ContaReceber primeira = null;
            for (int i = 0; i < parcelas; i++) {
                ContaReceber cr = new ContaReceber();
                cr.setClinicaId(clinicaId);
                cr.setPacienteId(conta.getPacienteId());
                cr.setProcedimentoId(conta.getProcedimentoId());
                cr.setDescricao(String.format("%s · parcela %d/%d",
                        conta.getDescricao(), i + 1, parcelas));
                cr.setValorTotal(valorParcela);
                cr.setValorPago(0.0);
                cr.setFormaPagamento(conta.getFormaPagamento());
                cr.setTipoPagamento(conta.getTipoPagamento());
                cr.setNumeroParcelas(parcelas);
                cr.setParcelaAtual(i + 1);
                cr.setDataVencimento(primeiroVencimento.plusMonths(i));
                cr.setStatus(StatusFinanceiroEnum.PENDENTE);
                cr.setObservacoes(conta.getObservacoes());
                cr.setCreatedAt(LocalDateTime.now());
                cr.setUpdatedAt(LocalDateTime.now());
                ContaReceber saved = contaReceberRepository.save(cr);
                if (i == 0) primeira = saved;
            }
            return primeira;
        }

        return contaReceberRepository.save(conta);
    }

    public List<ContaReceber> listarTodas() {
        return contaReceberRepository.findByClinicaId(AuthContextHelper.currentClinicaId());
    }

    public List<ContaReceber> buscarPorPaciente(String pacienteId) {
        return contaReceberRepository.findByClinicaIdAndPacienteId(
                AuthContextHelper.currentClinicaId(), pacienteId);
    }

    public List<ContaReceber> buscarPorStatus(StatusFinanceiroEnum status) {
        return contaReceberRepository.findByClinicaIdAndStatus(
                AuthContextHelper.currentClinicaId(), status);
    }

    public ContaReceber buscarPorId(String id) {
        return contaReceberRepository
                .findByIdAndClinicaId(id, AuthContextHelper.currentClinicaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta a receber não encontrada."));
    }

    public ContaReceber registrarPagamento(String id, Double valorRecebido) {
        ContaReceber conta = buscarPorId(id);
        if (valorRecebido == null || valorRecebido <= 0.0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor recebido deve ser maior que zero.");
        }
        double novoValorPago = conta.getValorPago() + valorRecebido;
        conta.setValorPago(novoValorPago);
        conta.setUpdatedAt(LocalDateTime.now());
        if (novoValorPago >= conta.getValorTotal()) {
            conta.setStatus(StatusFinanceiroEnum.PAGO);
            conta.setDataPagamento(LocalDate.now());
        }
        return contaReceberRepository.save(conta);
    }

    public ContaReceber atualizar(String id, ContaReceber dadosAtualizados) {
        String clinicaId = AuthContextHelper.currentClinicaId();
        ContaReceber contaExistente = buscarPorId(id);

        if (dadosAtualizados.getPacienteId() != null
                && !dadosAtualizados.getPacienteId().equals(contaExistente.getPacienteId())) {
            pacienteRepository.findByIdAndClinicaId(dadosAtualizados.getPacienteId(), clinicaId)
                    .filter(Paciente::getAtivo)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente não encontrado."));
            contaExistente.setPacienteId(dadosAtualizados.getPacienteId());
        }

        contaExistente.setProcedimentoId(dadosAtualizados.getProcedimentoId());
        contaExistente.setDescricao(dadosAtualizados.getDescricao());
        contaExistente.setValorTotal(dadosAtualizados.getValorTotal());
        contaExistente.setFormaPagamento(dadosAtualizados.getFormaPagamento());
        contaExistente.setNumeroParcelas(
                dadosAtualizados.getNumeroParcelas() != null ? dadosAtualizados.getNumeroParcelas() : 1);
        contaExistente.setDataVencimento(dadosAtualizados.getDataVencimento());
        contaExistente.setObservacoes(dadosAtualizados.getObservacoes());

        if (dadosAtualizados.getStatus() != null) {
            if (dadosAtualizados.getStatus() == StatusFinanceiroEnum.PAGO
                    && contaExistente.getStatus() != StatusFinanceiroEnum.PAGO) {
                contaExistente.setDataPagamento(LocalDate.now());
                if (contaExistente.getValorPago() < contaExistente.getValorTotal()) {
                    contaExistente.setValorPago(contaExistente.getValorTotal());
                }
            } else if (dadosAtualizados.getStatus() != StatusFinanceiroEnum.PAGO) {
                contaExistente.setDataPagamento(null);
            }
            contaExistente.setStatus(dadosAtualizados.getStatus());
        }

        contaExistente.setUpdatedAt(LocalDateTime.now());
        return contaReceberRepository.save(contaExistente);
    }

    public void deletar(String id) {
        ContaReceber conta = buscarPorId(id);
        contaReceberRepository.delete(conta);
    }
}

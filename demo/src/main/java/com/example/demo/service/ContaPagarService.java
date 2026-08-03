package com.example.demo.service;

import com.example.demo.enums.StatusFinanceiroEnum;
import com.example.demo.enums.TipoPagamentoProcedimentoEnum;
import com.example.demo.model.ContaPagar;
import com.example.demo.repository.ContaPagarRepository;
import com.example.demo.security.AuthContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ContaPagarService {

    @Autowired
    private ContaPagarRepository contaPagarRepository;

    public ContaPagar criar(ContaPagar conta) {
        String clinicaId = AuthContextHelper.currentClinicaId();
        conta.setClinicaId(clinicaId);
        conta.setCreatedAt(LocalDateTime.now());
        conta.setUpdatedAt(LocalDateTime.now());

        if (conta.getStatus() == null) {
            conta.setStatus(StatusFinanceiroEnum.PENDENTE);
        }

        // Se o status for PAGO e data de pagamento não informada, assume a data atual
        if (conta.getStatus() == StatusFinanceiroEnum.PAGO && conta.getDataPagamento() == null) {
            conta.setDataPagamento(LocalDate.now());
        }

        // Auto-split PARCELADO: gera N contas com vencimentos mensais
        boolean deveDividir = conta.getTipoPagamento() == TipoPagamentoProcedimentoEnum.PARCELADO
                && conta.getNumeroParcelas() != null
                && conta.getNumeroParcelas() > 1
                && conta.getValor() != null
                && conta.getDataVencimento() != null
                && conta.getStatus() != StatusFinanceiroEnum.PAGO;

        if (deveDividir) {
            int parcelas = conta.getNumeroParcelas();
            double valorParcela = Math.round((conta.getValor() / parcelas) * 100.0) / 100.0;
            LocalDate primeiroVencimento = conta.getDataVencimento();

            ContaPagar primeira = null;
            for (int i = 0; i < parcelas; i++) {
                ContaPagar cp = new ContaPagar();
                cp.setClinicaId(clinicaId);
                cp.setDescricao(String.format("%s · parcela %d/%d",
                        conta.getDescricao(), i + 1, parcelas));
                cp.setCategoria(conta.getCategoria());
                cp.setFornecedor(conta.getFornecedor());
                cp.setValor(valorParcela);
                cp.setTipoPagamento(conta.getTipoPagamento());
                cp.setNumeroParcelas(parcelas);
                cp.setParcelaAtual(i + 1);
                cp.setDataVencimento(primeiroVencimento.plusMonths(i));
                cp.setStatus(StatusFinanceiroEnum.PENDENTE);
                cp.setObservacoes(conta.getObservacoes());
                cp.setCreatedAt(LocalDateTime.now());
                cp.setUpdatedAt(LocalDateTime.now());
                ContaPagar saved = contaPagarRepository.save(cp);
                if (i == 0) primeira = saved;
            }
            return primeira;
        }

        return contaPagarRepository.save(conta);
    }

    public List<ContaPagar> listarTodas() {
        return contaPagarRepository.findByClinicaId(AuthContextHelper.currentClinicaId());
    }

    public List<ContaPagar> buscarPorStatus(StatusFinanceiroEnum status) {
        return contaPagarRepository.findByClinicaIdAndStatus(
                AuthContextHelper.currentClinicaId(), status);
    }

    public ContaPagar buscarPorId(String id) {
        return contaPagarRepository
                .findByIdAndClinicaId(id, AuthContextHelper.currentClinicaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta a pagar não encontrada."));
    }

    public ContaPagar atualizar(String id, ContaPagar dadosAtualizados) {
        ContaPagar contaExistente = buscarPorId(id);

        contaExistente.setDescricao(dadosAtualizados.getDescricao());
        contaExistente.setCategoria(dadosAtualizados.getCategoria());
        contaExistente.setFornecedor(dadosAtualizados.getFornecedor());
        contaExistente.setValor(dadosAtualizados.getValor());
        contaExistente.setDataVencimento(dadosAtualizados.getDataVencimento());
        contaExistente.setObservacoes(dadosAtualizados.getObservacoes());

        if (dadosAtualizados.getStatus() != null) {
            if (dadosAtualizados.getStatus() == StatusFinanceiroEnum.PAGO && contaExistente.getStatus() != StatusFinanceiroEnum.PAGO) {
                // Ao quitar, se não foi fornecida data de pagamento, define como hoje
                contaExistente.setDataPagamento(dadosAtualizados.getDataPagamento() != null ? dadosAtualizados.getDataPagamento() : LocalDate.now());
            } else if (dadosAtualizados.getStatus() != StatusFinanceiroEnum.PAGO) {
                contaExistente.setDataPagamento(null);
            } else {
                contaExistente.setDataPagamento(dadosAtualizados.getDataPagamento());
            }
            contaExistente.setStatus(dadosAtualizados.getStatus());
        } else {
            contaExistente.setDataPagamento(dadosAtualizados.getDataPagamento());
        }

        contaExistente.setUpdatedAt(LocalDateTime.now());

        return contaPagarRepository.save(contaExistente);
    }

    public void deletar(String id) {
        ContaPagar conta = buscarPorId(id); // Lança 404 se não existir
        contaPagarRepository.delete(conta);
    }
}

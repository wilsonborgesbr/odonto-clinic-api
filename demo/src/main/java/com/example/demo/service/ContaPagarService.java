package com.example.demo.service;

import com.example.demo.enums.StatusFinanceiroEnum;
import com.example.demo.model.ContaPagar;
import com.example.demo.repository.ContaPagarRepository;
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
        conta.setCreatedAt(LocalDateTime.now());
        conta.setUpdatedAt(LocalDateTime.now());

        if (conta.getStatus() == null) {
            conta.setStatus(StatusFinanceiroEnum.PENDENTE);
        }

        // Se o status for PAGO e data de pagamento não informada, assume a data atual
        if (conta.getStatus() == StatusFinanceiroEnum.PAGO && conta.getDataPagamento() == null) {
            conta.setDataPagamento(LocalDate.now());
        }

        return contaPagarRepository.save(conta);
    }

    public List<ContaPagar> listarTodas() {
        return contaPagarRepository.findAll();
    }

    public List<ContaPagar> buscarPorStatus(StatusFinanceiroEnum status) {
        return contaPagarRepository.findByStatus(status);
    }

    public ContaPagar buscarPorId(String id) {
        return contaPagarRepository.findById(id)
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

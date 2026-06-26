package com.example.demo.service;

import com.example.demo.enums.StatusFinanceiroEnum;
import com.example.demo.model.ContaReceber;
import com.example.demo.model.Paciente;
import com.example.demo.repository.ContaReceberRepository;
import com.example.demo.repository.PacienteRepository;
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
        if (conta.getPacienteId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID do paciente é obrigatório.");
        }

        // Valida se o paciente existe e está ativo
        Paciente paciente = pacienteRepository.findById(conta.getPacienteId())
                .filter(Paciente::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente não encontrado."));

        // Preenche campos automáticos/padrão
        conta.setValorPago(0.0);
        conta.setCreatedAt(LocalDateTime.now());
        conta.setUpdatedAt(LocalDateTime.now());

        if (conta.getStatus() == null) {
            conta.setStatus(StatusFinanceiroEnum.PENDENTE);
        }
        if (conta.getNumeroParcelas() == null) {
            conta.setNumeroParcelas(1);
        }

        return contaReceberRepository.save(conta);
    }

    public List<ContaReceber> listarTodas() {
        return contaReceberRepository.findAll();
    }

    public List<ContaReceber> buscarPorPaciente(String pacienteId) {
        return contaReceberRepository.findByPacienteId(pacienteId);
    }

    public List<ContaReceber> buscarPorStatus(StatusFinanceiroEnum status) {
        return contaReceberRepository.findByStatus(status);
    }

    public ContaReceber buscarPorId(String id) {
        return contaReceberRepository.findById(id)
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
        ContaReceber contaExistente = buscarPorId(id);

        // Se pacienteId foi alterado, valida o novo paciente
        if (dadosAtualizados.getPacienteId() != null && !dadosAtualizados.getPacienteId().equals(contaExistente.getPacienteId())) {
            pacienteRepository.findById(dadosAtualizados.getPacienteId())
                    .filter(Paciente::getAtivo)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente não encontrado."));
            contaExistente.setPacienteId(dadosAtualizados.getPacienteId());
        }

        contaExistente.setProcedimentoId(dadosAtualizados.getProcedimentoId());
        contaExistente.setDescricao(dadosAtualizados.getDescricao());
        contaExistente.setValorTotal(dadosAtualizados.getValorTotal());
        contaExistente.setFormaPagamento(dadosAtualizados.getFormaPagamento());
        contaExistente.setNumeroParcelas(dadosAtualizados.getNumeroParcelas() != null ? dadosAtualizados.getNumeroParcelas() : 1);
        contaExistente.setDataVencimento(dadosAtualizados.getDataVencimento());
        contaExistente.setObservacoes(dadosAtualizados.getObservacoes());

        // Se o status foi atualizado manualmente para PAGO e não tinha data de pagamento
        if (dadosAtualizados.getStatus() != null) {
            if (dadosAtualizados.getStatus() == StatusFinanceiroEnum.PAGO && contaExistente.getStatus() != StatusFinanceiroEnum.PAGO) {
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
        ContaReceber conta = buscarPorId(id); // Lança 404 se não existir
        contaReceberRepository.delete(conta);
    }
}

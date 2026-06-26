package com.example.demo.controller;

import com.example.demo.enums.StatusFinanceiroEnum;
import com.example.demo.model.ContaReceber;
import com.example.demo.service.ContaReceberService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contas-receber")
public class ContaReceberController {

    @Autowired
    private ContaReceberService contaReceberService;

    @PostMapping
    public ResponseEntity<ContaReceber> criar(@RequestBody @Valid ContaReceber conta) {
        ContaReceber novaConta = contaReceberService.criar(conta);
        return ResponseEntity.status(HttpStatus.CREATED).body(novaConta);
    }

    @GetMapping
    public ResponseEntity<List<ContaReceber>> listarTodas() {
        List<ContaReceber> contas = contaReceberService.listarTodas();
        return ResponseEntity.ok(contas);
    }

    @GetMapping("/paciente/{pacienteId}")
    public ResponseEntity<List<ContaReceber>> buscarPorPaciente(@PathVariable String pacienteId) {
        List<ContaReceber> contas = contaReceberService.buscarPorPaciente(pacienteId);
        return ResponseEntity.ok(contas);
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<ContaReceber>> buscarPorStatus(@PathVariable StatusFinanceiroEnum status) {
        List<ContaReceber> contas = contaReceberService.buscarPorStatus(status);
        return ResponseEntity.ok(contas);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContaReceber> buscarPorId(@PathVariable String id) {
        ContaReceber conta = contaReceberService.buscarPorId(id);
        return ResponseEntity.ok(conta);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContaReceber> atualizar(@PathVariable String id, @RequestBody @Valid ContaReceber conta) {
        ContaReceber contaAtualizada = contaReceberService.atualizar(id, conta);
        return ResponseEntity.ok(contaAtualizada);
    }

    @PatchMapping("/{id}/pagamento")
    public ResponseEntity<ContaReceber> registrarPagamento(
            @PathVariable String id,
            @RequestParam("valor") Double valor) {
        ContaReceber contaAtualizada = contaReceberService.registrarPagamento(id, valor);
        return ResponseEntity.ok(contaAtualizada);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable String id) {
        contaReceberService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}

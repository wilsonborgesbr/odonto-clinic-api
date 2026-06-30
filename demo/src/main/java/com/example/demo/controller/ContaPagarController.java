package com.example.demo.controller;

import com.example.demo.enums.StatusFinanceiroEnum;
import com.example.demo.model.ContaPagar;
import com.example.demo.service.ContaPagarService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contas-pagar")
@Tag(name = "Contas a Pagar", description = "Endpoints para controle de despesas e contas a pagar")
public class ContaPagarController {

    @Autowired
    private ContaPagarService contaPagarService;

    @PostMapping
    public ResponseEntity<ContaPagar> criar(@RequestBody @Valid ContaPagar conta) {
        ContaPagar novaConta = contaPagarService.criar(conta);
        return ResponseEntity.status(HttpStatus.CREATED).body(novaConta);
    }

    @GetMapping
    public ResponseEntity<List<ContaPagar>> listarTodas() {
        List<ContaPagar> contas = contaPagarService.listarTodas();
        return ResponseEntity.ok(contas);
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<ContaPagar>> buscarPorStatus(@PathVariable StatusFinanceiroEnum status) {
        List<ContaPagar> contas = contaPagarService.buscarPorStatus(status);
        return ResponseEntity.ok(contas);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContaPagar> buscarPorId(@PathVariable String id) {
        ContaPagar conta = contaPagarService.buscarPorId(id);
        return ResponseEntity.ok(conta);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContaPagar> atualizar(@PathVariable String id, @RequestBody @Valid ContaPagar conta) {
        ContaPagar contaAtualizada = contaPagarService.atualizar(id, conta);
        return ResponseEntity.ok(contaAtualizada);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable String id) {
        contaPagarService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}

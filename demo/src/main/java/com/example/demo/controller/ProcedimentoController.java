package com.example.demo.controller;

import com.example.demo.enums.StatusProcedimentoEnum;
import com.example.demo.model.Procedimento;
import com.example.demo.service.ProcedimentoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/procedimentos")
public class ProcedimentoController {

    @Autowired
    private ProcedimentoService procedimentoService;

    @PostMapping
    public ResponseEntity<Procedimento> criar(@RequestBody @Valid Procedimento procedimento) {
        Procedimento novoProcedimento = procedimentoService.criar(procedimento);
        return ResponseEntity.status(HttpStatus.CREATED).body(novoProcedimento);
    }

    @GetMapping("/paciente/{pacienteId}")
    public ResponseEntity<List<Procedimento>> listarPorPaciente(@PathVariable String pacienteId) {
        List<Procedimento> procedimentos = procedimentoService.listarPorPaciente(pacienteId);
        return ResponseEntity.ok(procedimentos);
    }

    @GetMapping("/paciente/{pacienteId}/status/{status}")
    public ResponseEntity<List<Procedimento>> listarPorPacienteEStatus(
            @PathVariable String pacienteId,
            @PathVariable StatusProcedimentoEnum status) {
        List<Procedimento> procedimentos = procedimentoService.listarPorPacienteEStatus(pacienteId, status);
        return ResponseEntity.ok(procedimentos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Procedimento> buscarPorId(@PathVariable String id) {
        Procedimento procedimento = procedimentoService.buscarPorId(id);
        return ResponseEntity.ok(procedimento);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Procedimento> atualizar(
            @PathVariable String id,
            @RequestBody @Valid Procedimento procedimento) {
        Procedimento procedimentoAtualizado = procedimentoService.atualizar(id, procedimento);
        return ResponseEntity.ok(procedimentoAtualizado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable String id) {
        procedimentoService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}

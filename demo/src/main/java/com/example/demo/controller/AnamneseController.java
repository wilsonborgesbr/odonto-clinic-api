package com.example.demo.controller;

import com.example.demo.model.Anamnese;
import com.example.demo.service.AnamneseService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/anamneses")
@Tag(name = "Anamnese", description = "Endpoints para gerenciamento de fichas de anamnese dos pacientes")
public class AnamneseController {

    @Autowired
    private AnamneseService anamneseService;

    @PostMapping
    public ResponseEntity<Anamnese> criar(@RequestBody @Valid Anamnese anamnese) {
        Anamnese novaAnamnese = anamneseService.criar(anamnese);
        return ResponseEntity.status(HttpStatus.CREATED).body(novaAnamnese);
    }

    @GetMapping("/paciente/{pacienteId}")
    public ResponseEntity<List<Anamnese>> listarPorPaciente(@PathVariable String pacienteId) {
        List<Anamnese> historico = anamneseService.listarPorPacienteId(pacienteId);
        return ResponseEntity.ok(historico);
    }

    @GetMapping("/paciente/{pacienteId}/recente")
    public ResponseEntity<Anamnese> buscarMaisRecentePorPaciente(@PathVariable String pacienteId) {
        Anamnese recente = anamneseService.buscarMaisRecentePorPacienteId(pacienteId);
        return ResponseEntity.ok(recente);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Anamnese> buscarPorId(@PathVariable String id) {
        Anamnese anamnese = anamneseService.buscarPorId(id);
        return ResponseEntity.ok(anamnese);
    }
}

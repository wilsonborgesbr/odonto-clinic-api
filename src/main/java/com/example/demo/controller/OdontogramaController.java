package com.example.demo.controller;

import com.example.demo.model.Odontograma;
import com.example.demo.service.OdontogramaService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/odontogramas")
@Tag(name = "Odontograma", description = "Endpoints para acompanhamento do histórico de odontogramas dos pacientes")
public class OdontogramaController {

    @Autowired
    private OdontogramaService odontogramaService;

    @PostMapping
    public ResponseEntity<Odontograma> criar(@RequestBody @Valid Odontograma odontograma) {
        Odontograma novoOdontograma = odontogramaService.criar(odontograma);
        return ResponseEntity.status(HttpStatus.CREATED).body(novoOdontograma);
    }

    @GetMapping("/paciente/{pacienteId}")
    public ResponseEntity<List<Odontograma>> listarPorPaciente(@PathVariable String pacienteId) {
        List<Odontograma> historico = odontogramaService.listarPorPacienteId(pacienteId);
        return ResponseEntity.ok(historico);
    }

    @GetMapping("/paciente/{pacienteId}/recente")
    public ResponseEntity<Odontograma> buscarMaisRecentePorPaciente(@PathVariable String pacienteId) {
        Odontograma recente = odontogramaService.buscarMaisRecentePorPacienteId(pacienteId);
        return ResponseEntity.ok(recente);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Odontograma> buscarPorId(@PathVariable String id) {
        Odontograma odontograma = odontogramaService.buscarPorId(id);
        return ResponseEntity.ok(odontograma);
    }
}

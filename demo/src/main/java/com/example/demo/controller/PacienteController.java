package com.example.demo.controller;

import com.example.demo.model.Paciente;
import com.example.demo.dto.PacienteListagemDTO;
import com.example.demo.service.PacienteService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pacientes")
@Tag(name = "Paciente", description = "Endpoints para gerenciamento de pacientes")
public class PacienteController {

    @Autowired
    private PacienteService pacienteService;

    @PostMapping
    public ResponseEntity<Paciente> criar(@RequestBody @Valid Paciente paciente) {
        Paciente novoPaciente = pacienteService.criar(paciente);
        return ResponseEntity.status(HttpStatus.CREATED).body(novoPaciente);
    }

    @GetMapping
    public ResponseEntity<Page<PacienteListagemDTO>> listarTodos(
            @PageableDefault(size = 10, sort = "nomeCompleto") Pageable pageable) {
        Page<PacienteListagemDTO> pacientes = pacienteService.listarTodos(pageable);
        return ResponseEntity.ok(pacientes);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Paciente> buscarPorId(@PathVariable String id) {
        Paciente paciente = pacienteService.buscarPorId(id);
        return ResponseEntity.ok(paciente);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Paciente> atualizar(@PathVariable String id, @RequestBody @Valid Paciente paciente) {
        Paciente pacienteAtualizado = pacienteService.atualizar(id, paciente);
        return ResponseEntity.ok(pacienteAtualizado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable String id) {
        pacienteService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}

package com.example.demo.controller;

import com.example.demo.model.Documento;
import com.example.demo.service.DocumentoService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documentos")
@Tag(name = "Documento", description = "Endpoints para gerenciamento de documentos e arquivos dos pacientes")
public class DocumentoController {

    @Autowired
    private DocumentoService documentoService;

    @PostMapping
    public ResponseEntity<Documento> criar(@RequestBody @Valid Documento documento) {
        Documento novoDocumento = documentoService.criar(documento);
        return ResponseEntity.status(HttpStatus.CREATED).body(novoDocumento);
    }

    @GetMapping("/paciente/{pacienteId}")
    public ResponseEntity<List<Documento>> listarPorPaciente(@PathVariable String pacienteId) {
        List<Documento> documentos = documentoService.listarPorPaciente(pacienteId);
        return ResponseEntity.ok(documentos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Documento> buscarPorId(@PathVariable String id) {
        Documento documento = documentoService.buscarPorId(id);
        return ResponseEntity.ok(documento);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable String id) {
        documentoService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}

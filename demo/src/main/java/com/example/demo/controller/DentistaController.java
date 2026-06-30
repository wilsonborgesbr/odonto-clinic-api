package com.example.demo.controller;

import com.example.demo.model.Dentista;
import com.example.demo.service.DentistaService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dentistas")
@Tag(name = "Dentista", description = "Endpoints para gerenciamento de dentistas")
public class DentistaController {

    @Autowired
    private DentistaService dentistaService;

    @PostMapping
    public ResponseEntity<Dentista> criar(@RequestBody @Valid Dentista dentista) {
        Dentista novoDentista = dentistaService.criar(dentista);
        return ResponseEntity.status(HttpStatus.CREATED).body(novoDentista);
    }

    @GetMapping
    public ResponseEntity<List<Dentista>> listarTodos() {
        List<Dentista> dentistas = dentistaService.listarTodos();
        return ResponseEntity.ok(dentistas);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Dentista> buscarPorId(@PathVariable String id) {
        Dentista dentista = dentistaService.buscarPorId(id);
        return ResponseEntity.ok(dentista);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Dentista> atualizar(@PathVariable String id, @RequestBody @Valid Dentista dentista) {
        Dentista dentistaAtualizado = dentistaService.atualizar(id, dentista);
        return ResponseEntity.ok(dentistaAtualizado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable String id) {
        dentistaService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}

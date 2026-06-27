package com.example.demo.controller;

import com.example.demo.model.Convenio;
import com.example.demo.service.ConvenioService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/convenios")
public class ConvenioController {

    @Autowired
    private ConvenioService convenioService;

    @PostMapping
    public ResponseEntity<Convenio> criar(@RequestBody @Valid Convenio convenio) {
        Convenio novoConvenio = convenioService.criar(convenio);
        return ResponseEntity.status(HttpStatus.CREATED).body(novoConvenio);
    }

    @GetMapping
    public ResponseEntity<List<Convenio>> listarTodos() {
        List<Convenio> convenios = convenioService.listarTodos();
        return ResponseEntity.ok(convenios);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Convenio> buscarPorId(@PathVariable String id) {
        Convenio convenio = convenioService.buscarPorId(id);
        return ResponseEntity.ok(convenio);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Convenio> atualizar(@PathVariable String id, @RequestBody @Valid Convenio convenio) {
        Convenio convenioAtualizado = convenioService.atualizar(id, convenio);
        return ResponseEntity.ok(convenioAtualizado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable String id) {
        convenioService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}

package com.example.demo.controller;

import com.example.demo.enums.CategoriaEstoqueEnum;
import com.example.demo.model.Estoque;
import com.example.demo.service.EstoqueService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/estoque")
@Tag(name = "Estoque", description = "Endpoints para controle de estoque de insumos e materiais")
public class EstoqueController {

    @Autowired
    private EstoqueService estoqueService;

    @PostMapping
    public ResponseEntity<Estoque> criar(@RequestBody @Valid Estoque estoque) {
        Estoque novoEstoque = estoqueService.criar(estoque);
        return ResponseEntity.status(HttpStatus.CREATED).body(novoEstoque);
    }

    @GetMapping
    public ResponseEntity<List<Estoque>> listarTodos() {
        List<Estoque> itens = estoqueService.listarTodos();
        return ResponseEntity.ok(itens);
    }

    @GetMapping("/abaixo-minimo")
    public ResponseEntity<List<Estoque>> listarAbaixoDoMinimo() {
        List<Estoque> itens = estoqueService.listarAbaixoDoMinimo();
        return ResponseEntity.ok(itens);
    }

    @GetMapping("/categoria/{categoria}")
    public ResponseEntity<List<Estoque>> buscarPorCategoria(@PathVariable CategoriaEstoqueEnum categoria) {
        List<Estoque> itens = estoqueService.buscarPorCategoria(categoria);
        return ResponseEntity.ok(itens);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Estoque> buscarPorId(@PathVariable String id) {
        Estoque estoque = estoqueService.buscarPorId(id);
        return ResponseEntity.ok(estoque);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Estoque> atualizar(@PathVariable String id, @RequestBody @Valid Estoque estoque) {
        Estoque estoqueAtualizado = estoqueService.atualizar(id, estoque);
        return ResponseEntity.ok(estoqueAtualizado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable String id) {
        estoqueService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}

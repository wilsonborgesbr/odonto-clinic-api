package com.example.demo.controller;

import com.example.demo.dto.PendenciasCadastroDTO;
import com.example.demo.service.PendenciaCadastroService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cadastros")
@Tag(name = "Cadastros", description = "Pendências de dados cadastrais a corrigir")
public class CadastroController {

    @Autowired
    private PendenciaCadastroService pendenciaCadastroService;

    @GetMapping("/pendencias")
    public ResponseEntity<PendenciasCadastroDTO> pendencias() {
        return ResponseEntity.ok(pendenciaCadastroService.listar());
    }
}

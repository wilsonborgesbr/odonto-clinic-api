package com.example.demo.controller;

import com.example.demo.dto.ProdutividadeDTO;
import com.example.demo.service.ProdutividadeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Indicadores agregados do painel")
public class ProdutividadeController {

    private final ProdutividadeService produtividadeService;

    public ProdutividadeController(ProdutividadeService produtividadeService) {
        this.produtividadeService = produtividadeService;
    }

    @GetMapping("/produtividade")
    public ResponseEntity<ProdutividadeDTO> produtividade(
            @RequestParam(value = "metrica", required = false) String metrica,
            @RequestParam(value = "periodo", required = false) String periodo,
            @RequestParam(value = "referencia", required = false) String referencia,
            @RequestParam(value = "dentistaId", required = false) String dentistaId) {
        return ResponseEntity.ok(produtividadeService.consultar(metrica, periodo, referencia, dentistaId));
    }
}

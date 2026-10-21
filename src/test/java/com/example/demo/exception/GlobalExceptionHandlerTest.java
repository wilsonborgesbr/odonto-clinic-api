package com.example.demo.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void campoInvalido_RetornaMapaCampoMensagemComoNaValidacaoDeBean() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/pacientes");

        ResponseEntity<GlobalExceptionHandler.ErroPadrao> resposta = handler.handleCampoInvalido(
                new CampoInvalidoException("responsavelCpf", "CPF do responsável inválido. Verifique os números digitados."),
                request);

        assertEquals(HttpStatus.BAD_REQUEST, resposta.getStatusCode());
        GlobalExceptionHandler.ErroPadrao erro = resposta.getBody();
        assertEquals(400, erro.status());
        assertEquals("Erro de Validação", erro.erro());
        assertEquals(Map.of("responsavelCpf", "CPF do responsável inválido. Verifique os números digitados."),
                erro.mensagem());
        assertEquals("/api/pacientes", erro.path());
    }
}

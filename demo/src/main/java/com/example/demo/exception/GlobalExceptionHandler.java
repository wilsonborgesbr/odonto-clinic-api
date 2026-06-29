package com.example.demo.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroPadrao> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> mensagens = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            mensagens.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ErroPadrao erro = new ErroPadrao(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Erro de Validação",
                mensagens,
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(erro);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErroPadrao> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        String nomeErro = "Erro na Requisição";
        try {
            nomeErro = HttpStatus.valueOf(ex.getStatusCode().value()).getReasonPhrase();
        } catch (Exception e) {
            nomeErro = ex.getStatusCode().toString();
        }

        ErroPadrao erro = new ErroPadrao(
                LocalDateTime.now(),
                ex.getStatusCode().value(),
                nomeErro,
                ex.getReason(),
                request.getRequestURI()
        );

        return ResponseEntity.status(ex.getStatusCode()).body(erro);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErroPadrao> handleRuntime(RuntimeException ex, HttpServletRequest request) {
        ErroPadrao erro = new ErroPadrao(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Erro na Requisição",
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(erro);
    }

    // Record para a resposta padrão de erro
    public record ErroPadrao(
            LocalDateTime timestamp,
            int status,
            String erro,
            Object mensagem,
            String path
    ) {}
}

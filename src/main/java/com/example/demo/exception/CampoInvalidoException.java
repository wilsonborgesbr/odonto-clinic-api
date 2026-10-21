package com.example.demo.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class CampoInvalidoException extends ResponseStatusException {

    private final String campo;

    public CampoInvalidoException(String campo, String mensagem) {
        super(HttpStatus.BAD_REQUEST, mensagem);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}

package com.example.demo.googlecalendar;

/**
 * A conexão não pode mais ser usada (acesso revogado, falha de autenticação, apagada ou pausada). Quem recebe
 * isto para de sincronizar essa conexão sem tratar como erro do sistema; o estado já foi registrado no banco.
 */
public class ConexaoIndisponivelException extends RuntimeException {

    public ConexaoIndisponivelException(String mensagem) {
        super(mensagem);
    }
}

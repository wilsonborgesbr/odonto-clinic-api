package com.example.demo.email;

public interface EmailService {

    /**
     * Envia um e-mail com versão HTML e texto simples. A categoria (ex.: {@code reset-senha}) vira tag
     * no provedor. Nunca lança exceção por falha de envio: o chamador não deve revelar ao usuário
     * se o e-mail saiu (anti-enumeração); a falha fica no log.
     */
    void enviar(String destinatario, String assunto, String html, String texto, String categoria);
}

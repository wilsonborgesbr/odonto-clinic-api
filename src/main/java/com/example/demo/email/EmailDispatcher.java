package com.example.demo.email;

import com.example.demo.config.EmailAsyncConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/**
 * Executa o trabalho de e-mail (emitir token + renderizar + enviar) fora da requisição e engole qualquer
 * falha: o usuário nunca fica sabendo se o e-mail saiu, e o tempo de resposta não revela se a conta existe.
 */
@Component
public class EmailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatcher.class);

    private final Executor executor;

    @Autowired
    public EmailDispatcher(@Qualifier(EmailAsyncConfig.EMAIL_EXECUTOR) Executor executor) {
        this.executor = executor;
    }

    public void executar(String descricao, Runnable tarefa) {
        try {
            executor.execute(() -> {
                try {
                    tarefa.run();
                } catch (Exception e) {
                    log.error("Falha no envio de e-mail ({}): {}", descricao, e.toString());
                }
            });
        } catch (Exception e) {
            log.error("Envio de e-mail não agendado ({}): {}", descricao, e.toString());
        }
    }
}

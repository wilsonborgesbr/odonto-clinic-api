package com.example.demo.googlecalendar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/** Repete chamadas ao Google que falharam por motivo transitório (rede, 429, 5xx), com espera crescente. */
@Component
public class TentativasGoogle {

    private static final Logger log = LoggerFactory.getLogger(TentativasGoogle.class);

    private final GoogleCalendarProperties props;

    @Autowired
    public TentativasGoogle(GoogleCalendarProperties props) {
        this.props = props;
    }

    public <T> T executar(Supplier<T> chamada) {
        int maximo = props.tentativasApi();
        for (int tentativa = 1; ; tentativa++) {
            try {
                return chamada.get();
            } catch (GoogleApiException e) {
                if (!e.transitorio() || tentativa >= maximo) {
                    throw e;
                }
                log.warn("Google Calendar falhou ({}), tentativa {}/{}.", e.getMessage(), tentativa, maximo);
                esperar(tentativa);
            }
        }
    }

    public void executar(Runnable chamada) {
        executar(() -> {
            chamada.run();
            return null;
        });
    }

    private void esperar(int tentativa) {
        long ms = props.esperaEntreTentativas().toMillis() * tentativa;
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GoogleApiException("Interrompido durante nova tentativa.", e);
        }
    }
}

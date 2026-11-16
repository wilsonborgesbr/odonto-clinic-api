package com.example.demo.googlecalendar;

import com.example.demo.config.GoogleCalendarAsyncConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Recebe o evento de domínio e sincroniza fora da requisição. Falha aqui nunca volta para quem salvou. */
@Component
public class AgendamentoGoogleListener {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoGoogleListener.class);

    @Autowired
    private SincronizacaoSaidaService saida;

    @Async(GoogleCalendarAsyncConfig.GOOGLE_CALENDAR_EXECUTOR)
    @EventListener
    public void aoMudarAgendamento(AgendamentoSincronizacaoEvent evento) {
        try {
            saida.sincronizar(evento.clinicaId(), evento.agendamentoId());
        } catch (RuntimeException e) {
            log.error("Falha na sincronização do agendamento {} ({}): {}", evento.agendamentoId(), evento.tipo(), e.getMessage());
        }
    }
}

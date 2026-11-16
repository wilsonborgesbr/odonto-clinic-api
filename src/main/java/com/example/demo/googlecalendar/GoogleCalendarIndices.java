package com.example.demo.googlecalendar;

import com.example.demo.model.GoogleCalendarConexao;
import com.example.demo.model.GoogleCalendarEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.stereotype.Component;

/**
 * O projeto não liga a criação automática de índices, então os {@code @CompoundIndex} destas coleções viram
 * letra morta. Aqui eles são criados na subida (idempotente) porque a unicidade de conexões e de elos
 * é garantia de integridade, não só desempenho.
 */
@Component
public class GoogleCalendarIndices {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarIndices.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void criar() {
        removerIndiceLegado();
        for (Class<?> tipo : new Class<?>[]{GoogleCalendarConexao.class, GoogleCalendarEvento.class}) {
            try {
                IndexOperations ops = mongoTemplate.indexOps(tipo);
                var resolver = new MongoPersistentEntityIndexResolver(mongoTemplate.getConverter().getMappingContext());
                for (IndexDefinition indice : resolver.resolveIndexFor(tipo)) {
                    ops.createIndex(indice);
                }
            } catch (RuntimeException e) {
                log.warn("Não foi possível criar os índices de {}: {}", tipo.getSimpleName(), e.getMessage());
            }
        }
    }

    /** O índice único parcial da antiga "agenda geral da clínica" não existe mais; sai de bancos que o tenham. */
    private void removerIndiceLegado() {
        try {
            mongoTemplate.indexOps(GoogleCalendarConexao.class).dropIndex("clinica_geral_unique");
        } catch (RuntimeException e) {
            // não existe (banco novo): nada a remover
        }
    }
}

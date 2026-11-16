package com.example.demo.googlecalendar;

import com.example.demo.model.GoogleCalendarConexao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Registra na conexão o momento da última sincronização concluída sem falhas (exibido no status). */
@Component
public class UltimaSincronizacao {

    @Autowired
    private MongoTemplate mongoTemplate;

    public void registrarSucesso(String conexaoId) {
        mongoTemplate.updateFirst(new Query(Criteria.where("id").is(conexaoId)),
                new Update().set("ultimaSincronizacaoEm", LocalDateTime.now()).set("ultimaSincronizacaoFalhou", false),
                GoogleCalendarConexao.class);
    }
}

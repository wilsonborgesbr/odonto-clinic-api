package com.example.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Migração destrutiva: dropa todas as coleções na inicialização quando
 * {@code bokka.migration.drop-all-on-startup=true} está setado.
 *
 * <p>Racional: a mudança pra multi-tenancy adicionou {@code clinicaId} em todos os
 * models. Documentos antigos ficariam órfãos (sem tenant), então é mais seguro
 * apagar tudo e recriar do zero via /auth/register-clinica.
 *
 * <p>Depois de rodar UMA vez, ajuste a propriedade pra {@code false} — senão o
 * banco será apagado a cada restart.
 */
@Component
@Order(1)
public class CleanDbOnStartup implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CleanDbOnStartup.class);

    private static final List<String> COLLECTIONS = List.of(
            "users", "clinicas",
            "pacientes", "dentistas", "funcionarios",
            "agendamentos", "procedimentos",
            "anamneses", "odontogramas",
            "contas_receber", "contas_pagar",
            "estoque", "convenios", "documentos"
    );

    @Value("${bokka.migration.drop-all-on-startup:false}")
    private boolean dropAll;

    private final MongoTemplate mongoTemplate;

    public CleanDbOnStartup(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(String... args) {
        if (!dropAll) return;

        log.warn("=".repeat(70));
        log.warn("[bokka] bokka.migration.drop-all-on-startup=true — LIMPANDO BANCO");
        log.warn("=".repeat(70));

        for (String coll : COLLECTIONS) {
            if (mongoTemplate.collectionExists(coll)) {
                mongoTemplate.dropCollection(coll);
                log.warn("[bokka] coleção dropada: {}", coll);
            }
        }

        log.warn("[bokka] Cadastre uma nova clínica em POST /auth/register-clinica");
        log.warn("[bokka] Lembre de setar drop-all-on-startup=false depois desta execução");
        log.warn("=".repeat(70));
    }
}

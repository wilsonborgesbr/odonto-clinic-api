package com.example.demo.googlecalendar;

import com.example.demo.config.GoogleCalendarAsyncConfig;
import com.example.demo.dto.GoogleCalendarDTOs;
import com.example.demo.dto.GoogleCalendarDTOs.SincronizarResponse;
import com.example.demo.enums.SyncStatusGoogleEnum;
import com.example.demo.model.GoogleCalendarConexao;
import com.example.demo.model.User;
import com.example.demo.repository.GoogleCalendarConexaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * "Sincronizar agora": busca primeiro o que mudou no Google (se houver) e depois reenvia ao Google os agendamentos
 * futuros da clínica. Roda em segundo plano; o frontend acompanha por {@code GET /api/google-calendar/status}
 * ({@code conexao.sincronizando}, {@code ultimaSincronizacaoEm}, {@code ultimaSincronizacaoFalhou}).
 */
@Service
public class SincronizacaoManualService {

    private static final Logger log = LoggerFactory.getLogger(SincronizacaoManualService.class);

    @Autowired
    private GoogleCalendarConexaoRepository conexaoRepository;
    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private SincronizacaoSaidaService saida;
    @Autowired
    private SincronizacaoEntradaService entrada;
    @Autowired
    private UltimaSincronizacao ultimaSincronizacao;
    @Autowired
    @Qualifier(GoogleCalendarAsyncConfig.GOOGLE_CALENDAR_EXECUTOR)
    private Executor executor;

    /** Inicia a sincronização manual da conexão do próprio usuário. */
    public SincronizarResponse iniciar(User user) {
        GoogleCalendarConexao conexao = conexaoRepository.findByClinicaIdAndUserId(user.getClinicaId(), user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Você não tem conexão com o Google Calendar."));
        if (conexao.getSyncStatus() != SyncStatusGoogleEnum.ATIVO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A conexão não está ativa (" + conexao.getSyncStatus() + "). Reconecte para sincronizar.");
        }

        LocalDateTime agora = LocalDateTime.now();
        var reivindicada = mongoTemplate.updateFirst(
                new Query(Criteria.where("id").is(conexao.getId()).and("syncStatus").is(SyncStatusGoogleEnum.ATIVO)
                        .orOperator(Criteria.where("sincronizandoDesde").exists(false),
                                Criteria.where("sincronizandoDesde").is(null),
                                Criteria.where("sincronizandoDesde").lt(agora.minus(GoogleCalendarDTOs.JANELA_MANUAL)))),
                new Update().set("sincronizandoDesde", agora),
                GoogleCalendarConexao.class);
        if (reivindicada.getModifiedCount() == 0) {
            GoogleCalendarConexao atual = conexaoRepository.findById(conexao.getId()).orElse(conexao);
            if (atual.getSyncStatus() != SyncStatusGoogleEnum.ATIVO) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A conexão não está mais ativa.");
            }
            return new SincronizarResponse(false, true, atual.getSincronizandoDesde());
        }

        try {
            executor.execute(() -> executar(conexao.getId()));
        } catch (RejectedExecutionException e) {
            liberar(conexao.getId());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Sincronização ocupada. Tente novamente em instantes.");
        }
        return new SincronizarResponse(true, false, agora);
    }

    private void executar(String conexaoId) {
        boolean sucesso = false;
        try {
            GoogleCalendarConexao conexao = conexaoRepository.findById(conexaoId).orElse(null);
            if (conexao == null || conexao.getSyncStatus() != SyncStatusGoogleEnum.ATIVO) {
                return;
            }
            boolean entradaOk = entrada.sincronizar(conexaoId, false);
            var resultado = saida.reenviar(conexao);
            sucesso = entradaOk && resultado.falhas() == 0;
            log.info("Sincronização manual da conexão {}: {} agendamento(s) verificado(s), {} falha(s), busca no Google {}.",
                    conexaoId, resultado.total(), resultado.falhas(), entradaOk ? "ok" : "com falha");
        } catch (RuntimeException e) {
            log.error("Sincronização manual da conexão {} interrompida: {}", conexaoId, e.getMessage());
        } finally {
            if (sucesso) {
                ultimaSincronizacao.registrarSucesso(conexaoId);
            } else {
                mongoTemplate.updateFirst(new Query(Criteria.where("id").is(conexaoId)),
                        new Update().set("ultimaSincronizacaoFalhou", true), GoogleCalendarConexao.class);
            }
            liberar(conexaoId);
        }
    }

    private void liberar(String conexaoId) {
        mongoTemplate.updateFirst(new Query(Criteria.where("id").is(conexaoId)),
                new Update().unset("sincronizandoDesde"), GoogleCalendarConexao.class);
    }
}

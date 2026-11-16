package com.example.demo.googlecalendar;

import com.example.demo.enums.StatusAgendamentoEnum;
import com.example.demo.enums.SyncStatusGoogleEnum;
import com.example.demo.model.Agendamento;
import com.example.demo.model.GoogleCalendarConexao;
import com.example.demo.model.GoogleCalendarEvento;
import com.example.demo.repository.AgendamentoRepository;
import com.example.demo.repository.GoogleCalendarConexaoRepository;
import com.example.demo.repository.GoogleCalendarEventoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Google → Bokka. Quando o Google avisa que a agenda mudou, busca só o que mudou (syncToken) e aplica
 * em agendamentos que JÁ existem no Bokka:
 * <ul>
 *   <li>evento movido de horário → atualiza o horário (se o dentista estiver livre; se não, o Bokka vence e
 *       o evento volta ao horário certo);</li>
 *   <li>evento excluído → só remove o vínculo local; o agendamento no Bokka não muda de status (fica registrado em log).</li>
 * </ul>
 * Nunca cria agendamento: só age sobre eventos que o próprio Bokka criou (elo gravado em
 * {@code google_calendar_eventos}). Eventos pessoais, cópias de eventos do Bokka e eventos sem elo são ignorados.
 */
@Service
public class SincronizacaoEntradaService {

    private static final Logger log = LoggerFactory.getLogger(SincronizacaoEntradaService.class);
    private static final int MAX_PAGINAS = 200;

    @Autowired
    private GoogleCalendarConexaoRepository conexaoRepository;
    @Autowired
    private GoogleCalendarEventoRepository eventoRepository;
    @Autowired
    private AgendamentoRepository agendamentoRepository;
    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private GoogleCalendarApi api;
    @Autowired
    private UltimaSincronizacao ultimaSincronizacao;
    @Autowired
    private GoogleTokenService tokenService;
    @Autowired
    private TentativasGoogle tentativas;
    @Autowired
    private ApplicationEventPublisher publisher;

    private Clock relogio = Clock.systemUTC();

    private final ConcurrentHashMap<String, Object> travas = new ConcurrentHashMap<>();

    void usarRelogio(Clock relogio) {
        this.relogio = relogio;
    }

    /**
     * Ponto de entrada do webhook e da sincronização manual. Serializado por conexão: notificações em rajada viram
     * execuções em fila.
     *
     * @return false se a busca falhou ou a conexão não está ativa
     */
    public boolean sincronizar(String conexaoId) {
        return sincronizar(conexaoId, true);
    }

    /** @param registrarSucesso false na sincronização manual: ela só registra o horário se também o reenvio der certo */
    public boolean sincronizar(String conexaoId, boolean registrarSucesso) {
        synchronized (travas.computeIfAbsent(conexaoId, k -> new Object())) {
            GoogleCalendarConexao conexao = conexaoRepository.findById(conexaoId).orElse(null);
            if (conexao == null || conexao.getSyncStatus() != SyncStatusGoogleEnum.ATIVO) {
                return false;
            }
            try {
                executar(conexao, registrarSucesso);
                return true;
            } catch (ConexaoIndisponivelException e) {
                log.info("Sincronização de entrada interrompida: {}", e.getMessage());
                return false;
            } catch (RuntimeException e) {
                log.error("Falha na sincronização de entrada da conexão {}: {}", conexaoId, e.getMessage());
                return false;
            }
        }
    }

    private void executar(GoogleCalendarConexao conexao, boolean registrarSucesso) {
        if (conexao.getSyncToken() == null) {
            iniciarSyncToken(conexao);
            return;
        }
        String token = tokenService.accessTokenValido(conexao);
        String pagina = null;
        String proximoToken = null;
        for (int i = 0; i < MAX_PAGINAS; i++) {
            String paginaAtual = pagina;
            GoogleCalendarApi.PaginaEventos resultado;
            try {
                resultado = tentativas.executar(() -> api.listarEventos(
                        token, conexao.getGoogleCalendarId(), conexao.getSyncToken(), paginaAtual, null));
            } catch (GoogleApiException e) {
                if (e.desaparecido()) {
                    log.warn("syncToken da conexão {} venceu; recomeçando a partir de agora.", conexao.getId());
                    gravarSyncToken(conexao.getId(), null);
                    iniciarSyncToken(conexao);
                    return;
                }
                throw e;
            }
            for (GoogleCalendarApi.EventoRemoto remoto : resultado.eventos()) {
                try {
                    processar(conexao, remoto);
                } catch (RuntimeException e) {
                    log.error("Falha ao processar evento {} da conexão {}: {}", remoto.id(), conexao.getId(), e.getMessage());
                }
            }
            if (resultado.proximaPagina() == null) {
                proximoToken = resultado.proximoSyncToken();
                break;
            }
            pagina = resultado.proximaPagina();
        }
        if (proximoToken != null) {
            gravarSyncToken(conexao.getId(), proximoToken);
            if (registrarSucesso) {
                ultimaSincronizacao.registrarSucesso(conexao.getId());
            }
        }
    }

    /**
     * Carga inicial: percorre a agenda só para obter o syncToken. Eventos já existentes não são importados
     * (nada é criado a partir do Google).
     */
    public void iniciarSyncToken(GoogleCalendarConexao conexao) {
        String token = tokenService.accessTokenValido(conexao);
        Instant desde = relogio.instant();
        String pagina = null;
        for (int i = 0; i < MAX_PAGINAS; i++) {
            String paginaAtual = pagina;
            GoogleCalendarApi.PaginaEventos resultado = tentativas.executar(() -> api.listarEventos(
                    token, conexao.getGoogleCalendarId(), null, paginaAtual, desde));
            if (resultado.proximaPagina() == null) {
                if (resultado.proximoSyncToken() != null) {
                    gravarSyncToken(conexao.getId(), resultado.proximoSyncToken());
                }
                return;
            }
            pagina = resultado.proximaPagina();
        }
        log.warn("Carga inicial da conexão {} excedeu {} páginas; syncToken não obtido.", conexao.getId(), MAX_PAGINAS);
    }

    private void gravarSyncToken(String conexaoId, String syncToken) {
        Update update = new Update().set("updatedAt", LocalDateTime.now());
        if (syncToken == null) {
            update.unset("syncToken");
        } else {
            update.set("syncToken", syncToken);
        }
        mongoTemplate.updateFirst(new Query(Criteria.where("id").is(conexaoId)), update, GoogleCalendarConexao.class);
    }

    void processar(GoogleCalendarConexao conexao, GoogleCalendarApi.EventoRemoto remoto) {
        Optional<GoogleCalendarEvento> eloOpt = eventoRepository.findByConexaoIdAndGoogleEventId(conexao.getId(), remoto.id());
        if (eloOpt.isEmpty()) {
            return;
        }
        GoogleCalendarEvento elo = eloOpt.get();
        if (!remoto.cancelado()
                && !elo.getAgendamentoId().equals(remoto.propriedadesPrivadas().get(SincronizacaoSaidaService.PROP_AGENDAMENTO))) {
            return;
        }

        if (remoto.cancelado()) {
            eventoRepository.deleteById(elo.getId());
            if (Boolean.TRUE.equals(elo.getRemovendo())) {
                return;
            }
            log.warn("Agendamento {} ficou sem vínculo com o Google Calendar: o evento {} foi excluído no Google (conexão {}). O agendamento no Bokka não foi alterado.",
                    elo.getAgendamentoId(), elo.getGoogleEventId(), conexao.getId());
            return;
        }
        if (Boolean.TRUE.equals(elo.getRemovendo())) {
            return;
        }

        LocalDateTime inicio = remoto.inicio();
        LocalDateTime fim = remoto.fim();
        if (inicio == null || fim == null) {
            return;
        }
        if (SincronizacaoSaidaService.horario(inicio, fim).equals(elo.getUltimoHorarioEnviado())) {
            return;
        }

        Agendamento agendamento = agendamentoRepository.findByIdAndClinicaId(elo.getAgendamentoId(), conexao.getClinicaId()).orElse(null);
        if (agendamento == null) {
            eventoRepository.deleteById(elo.getId());
            return;
        }
        if (!editavel(agendamento.getStatus())) {
            return;
        }
        if (inicio.equals(agendamento.getDataHoraInicio()) && fim.equals(agendamento.getDataHoraFim())) {
            atualizarElo(elo, inicio, fim, false);
            return;
        }
        if (!fim.isAfter(inicio) || temConflito(agendamento, inicio, fim)) {
            log.info("Horário vindo do Google recusado para o agendamento {} (inválido ou em conflito); o Bokka prevalece.",
                    agendamento.getId());
            atualizarElo(elo, null, null, true);
            publicar(agendamento);
            return;
        }

        var resultado = mongoTemplate.updateFirst(
                new Query(Criteria.where("id").is(agendamento.getId()).and("clinicaId").is(conexao.getClinicaId())
                        .and("status").in(StatusAgendamentoEnum.AGENDADO, StatusAgendamentoEnum.CONFIRMADO)),
                new Update().set("dataHoraInicio", inicio).set("dataHoraFim", fim).set("updatedAt", LocalDateTime.now()),
                Agendamento.class);
        if (resultado.getModifiedCount() > 0) {
            log.info("Agendamento {} reagendado a partir do Google Calendar.", agendamento.getId());
            atualizarElo(elo, inicio, fim, true);
            publicar(agendamento);
        }
    }


    private boolean temConflito(Agendamento agendamento, LocalDateTime inicio, LocalDateTime fim) {
        List<Agendamento> conflitos = agendamentoRepository.findConflitos(
                agendamento.getClinicaId(), agendamento.getDentistaId(), inicio, fim);
        return conflitos.stream().anyMatch(c -> !c.getId().equals(agendamento.getId()));
    }

    private static boolean editavel(StatusAgendamentoEnum status) {
        return status == StatusAgendamentoEnum.AGENDADO || status == StatusAgendamentoEnum.CONFIRMADO;
    }

    /**
     * @param novoHorario true: o Bokka passou a ter esse horário (eco futuro é ignorado) e o conteúdo enviado é
     *                    invalidado para o evento ser reenviado se preciso; com {@code inicio} nulo (recusa),
     *                    só invalida o conteúdo para o Bokka reescrever o evento.
     */
    private void atualizarElo(GoogleCalendarEvento elo, LocalDateTime inicio, LocalDateTime fim, boolean invalidarConteudo) {
        Update update = new Update().set("atualizadoEm", LocalDateTime.now());
        if (inicio != null) {
            update.set("ultimoHorarioEnviado", SincronizacaoSaidaService.horario(inicio, fim));
        }
        if (invalidarConteudo) {
            update.unset("ultimoConteudoEnviado");
        }
        mongoTemplate.updateFirst(new Query(Criteria.where("id").is(elo.getId())), update, GoogleCalendarEvento.class);
    }

    private void publicar(Agendamento a) {
        publicar(a.getClinicaId(), a.getId());
    }

    private void publicar(String clinicaId, String agendamentoId) {
        publisher.publishEvent(new AgendamentoSincronizacaoEvent(clinicaId, agendamentoId,
                AgendamentoSincronizacaoEvent.Tipo.ATUALIZADO));
    }
}

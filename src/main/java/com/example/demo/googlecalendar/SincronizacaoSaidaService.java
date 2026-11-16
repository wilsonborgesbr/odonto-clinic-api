package com.example.demo.googlecalendar;

import com.example.demo.enums.EscopoGoogleCalendarEnum;
import com.example.demo.enums.StatusAgendamentoEnum;
import com.example.demo.enums.SyncStatusGoogleEnum;
import com.example.demo.enums.TipoAgendamentoEnum;
import com.example.demo.model.Agendamento;
import com.example.demo.model.Dentista;
import com.example.demo.model.GoogleCalendarConexao;
import com.example.demo.model.GoogleCalendarEvento;
import com.example.demo.model.Paciente;
import com.example.demo.repository.AgendamentoRepository;
import com.example.demo.repository.DentistaRepository;
import com.example.demo.repository.GoogleCalendarConexaoRepository;
import com.example.demo.repository.GoogleCalendarEventoRepository;
import com.example.demo.repository.PacienteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Bokka → Google. Leva o estado ATUAL de um agendamento para cada conexão ativa que se aplica a ele
 * (criando, atualizando ou excluindo o evento). Idempotente: repetir converge para o mesmo resultado.
 *
 * <p>O evento mostra sempre o nome completo do paciente e o procedimento, mais um link para o prontuário
 * (que exige login no Bokka). Observações do agendamento nunca vão para o Google.
 */
@Service
public class SincronizacaoSaidaService {

    public static final String PROP_AGENDAMENTO = "bokkaAgendamentoId";
    static final String PREFIXO_EVENTO = "bk";
    private static final int LOCKS = 64;

    private static final Logger log = LoggerFactory.getLogger(SincronizacaoSaidaService.class);

    @Autowired
    private AgendamentoRepository agendamentoRepository;
    @Autowired
    private PacienteRepository pacienteRepository;
    @Autowired
    private DentistaRepository dentistaRepository;
    @Autowired
    private GoogleCalendarConexaoRepository conexaoRepository;
    @Autowired
    private GoogleCalendarEventoRepository eventoRepository;
    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private GoogleCalendarApi api;
    @Autowired
    private GoogleTokenService tokenService;
    @Autowired
    private TentativasGoogle tentativas;
    @Autowired
    private GoogleCalendarProperties props;
    @Autowired
    private UltimaSincronizacao ultimaSincronizacao;

    /** Serializa o trabalho por agendamento (instância única): criar e editar em seguida não se atropelam. */
    private final Object[] travas = novasTravas();

    private static Object[] novasTravas() {
        Object[] t = new Object[LOCKS];
        for (int i = 0; i < LOCKS; i++) {
            t[i] = new Object();
        }
        return t;
    }

    public void sincronizar(String clinicaId, String agendamentoId) {
        List<GoogleCalendarConexao> conexoes = conexaoRepository.findByClinicaId(clinicaId).stream()
                .filter(c -> c.getSyncStatus() == SyncStatusGoogleEnum.ATIVO)
                .toList();
        sincronizarEm(clinicaId, agendamentoId, conexoes);
    }

    /** Resultado de um reenvio completo de uma conexão. */
    public record ResultadoReenvio(int total, int falhas) {}

    /**
     * Reenvio forçado (botão "sincronizar agora"): leva à conexão o estado atual de todos os agendamentos futuros
     * da clínica e dos que já têm evento nela (para limpar o que foi cancelado ou excluído). Mesma lógica do
     * automático; o que já está igual no Google não é reenviado. Só termina com sucesso se nenhuma falha ocorrer.
     */
    public ResultadoReenvio reenviar(GoogleCalendarConexao conexao) {
        Query futuros = new Query(Criteria.where("clinicaId").is(conexao.getClinicaId())
                .and("dataHoraInicio").gte(LocalDateTime.now().minusDays(1)));
        futuros.fields().include("_id");
        Set<String> ids = new LinkedHashSet<>();
        mongoTemplate.find(futuros, Agendamento.class).forEach(a -> ids.add(a.getId()));
        eventoRepository.findByConexaoId(conexao.getId()).forEach(e -> ids.add(e.getAgendamentoId()));

        int falhas = 0;
        for (String id : ids) {
            falhas += sincronizarEm(conexao.getClinicaId(), id, List.of(conexao), false);
        }
        return new ResultadoReenvio(ids.size(), falhas);
    }

    private int sincronizarEm(String clinicaId, String agendamentoId, List<GoogleCalendarConexao> conexoes) {
        return sincronizarEm(clinicaId, agendamentoId, conexoes, true);
    }

    /** @return quantas conexões falharam para este agendamento */
    private int sincronizarEm(String clinicaId, String agendamentoId, List<GoogleCalendarConexao> conexoes,
                              boolean registrarSucesso) {
        synchronized (travas[Math.floorMod(agendamentoId.hashCode(), LOCKS)]) {
            if (conexoes.isEmpty()) {
                return 0;
            }
            Optional<Agendamento> agendamento = agendamentoRepository.findByIdAndClinicaId(agendamentoId, clinicaId);

            Paciente paciente = null;
            Dentista dentista = null;
            if (agendamento.isPresent() && agendamento.get().getStatus() != StatusAgendamentoEnum.CANCELADO) {
                paciente = pacienteRepository.findByIdAndClinicaId(agendamento.get().getPacienteId(), clinicaId).orElse(null);
                dentista = dentistaRepository.findByIdAndClinicaId(agendamento.get().getDentistaId(), clinicaId).orElse(null);
                if (paciente == null) {
                    log.warn("Agendamento {} sem paciente encontrado: não sincronizado com o Google.", agendamentoId);
                    return 0;
                }
            }

            int falhas = 0;
            for (GoogleCalendarConexao conexao : conexoes) {
                try {
                    boolean relevante = sincronizarNaConexao(conexao, agendamentoId, agendamento.orElse(null), paciente, dentista);
                    if (registrarSucesso && relevante) {
                        ultimaSincronizacao.registrarSucesso(conexao.getId());
                    }
                } catch (ConexaoIndisponivelException e) {
                    falhas++;
                    log.info("Conexão {} ignorada na sincronização: {}", conexao.getId(), e.getMessage());
                } catch (RuntimeException e) {
                    falhas++;
                    log.error("Falha ao sincronizar o agendamento {} na conexão {}: {}", agendamentoId, conexao.getId(), e.getMessage());
                }
            }
            return falhas;
        }
    }

    /** @return false quando o agendamento não diz respeito a esta conexão (nada a fazer, nem a registrar) */
    private boolean sincronizarNaConexao(GoogleCalendarConexao conexao, String agendamentoId, Agendamento agendamento,
                                      Paciente paciente, Dentista dentista) {
        Optional<GoogleCalendarEvento> existente = eventoRepository.findByConexaoIdAndAgendamentoId(conexao.getId(), agendamentoId);
        boolean deveExistir = agendamento != null
                && agendamento.getStatus() != StatusAgendamentoEnum.CANCELADO
                && aplicaA(conexao, agendamento);

        if (!deveExistir) {
            if (existente.isPresent()) {
                excluir(conexao, existente.get());
                return true;
            }
            return false;
        }

        GoogleCalendarApi.Evento evento = montarEvento(conexao, agendamento, paciente, dentista);
        String conteudo = resumo(evento);
        if (existente.isPresent() && !Boolean.TRUE.equals(existente.get().getRemovendo())
                && conteudo.equals(existente.get().getUltimoConteudoEnviado())) {
            return true;
        }
        String token = tokenService.accessTokenValido(conexao);
        String calendario = conexao.getGoogleCalendarId();

        if (existente.isPresent()) {
            try {
                tentativas.executar(() -> api.atualizarEvento(token, calendario, evento));
            } catch (GoogleApiException e) {
                if (!e.naoEncontrado() && !e.desaparecido()) {
                    throw e;
                }
                tentativas.executar(() -> api.inserirEvento(token, calendario, evento));
            }
        } else {
            try {
                tentativas.executar(() -> api.inserirEvento(token, calendario, evento));
            } catch (GoogleApiException e) {
                if (!e.conflito()) {
                    throw e;
                }
                // O evento (mesmo id determinístico) já existe, ou foi cancelado antes: atualizar o restaura.
                tentativas.executar(() -> api.atualizarEvento(token, calendario, evento));
            }
        }
        gravarElo(conexao, agendamento, evento, conteudo);
        return true;
    }

    private void excluir(GoogleCalendarConexao conexao, GoogleCalendarEvento elo) {
        String token = tokenService.accessTokenValido(conexao);
        // Marca antes: o aviso de "evento cancelado" que o Google devolve é nosso e não pode cancelar o agendamento.
        mongoTemplate.updateFirst(new Query(Criteria.where("id").is(elo.getId())),
                new Update().set("removendo", true), GoogleCalendarEvento.class);
        tentativas.executar(() -> api.excluirEvento(token, conexao.getGoogleCalendarId(), elo.getGoogleEventId()));
        log.info("Evento {} do agendamento {} excluído do Google (conexão {}).", elo.getGoogleEventId(), elo.getAgendamentoId(), conexao.getId());
        eventoRepository.deleteById(elo.getId());
    }

    private void gravarElo(GoogleCalendarConexao conexao, Agendamento agendamento, GoogleCalendarApi.Evento evento,
                           String conteudo) {
        Query chave = new Query(Criteria.where("conexaoId").is(conexao.getId()).and("agendamentoId").is(agendamento.getId()));
        Update update = new Update()
                .set("clinicaId", conexao.getClinicaId())
                .set("googleEventId", evento.id())
                .set("ultimoHorarioEnviado", horario(evento.inicio(), evento.fim()))
                .set("ultimoConteudoEnviado", conteudo)
                .set("removendo", false)
                .set("atualizadoEm", LocalDateTime.now());
        mongoTemplate.upsert(chave, update, GoogleCalendarEvento.class);
    }

    /** TODOS_DA_CLINICA recebe tudo; SOMENTE_PROPRIO só os agendamentos do dentista vinculado ao dono da conexão. */
    static boolean aplicaA(GoogleCalendarConexao conexao, Agendamento agendamento) {
        if (conexao.getEscopo() == EscopoGoogleCalendarEnum.TODOS_DA_CLINICA) {
            return true;
        }
        return conexao.getDentistaId() != null && conexao.getDentistaId().equals(agendamento.getDentistaId());
    }

    GoogleCalendarApi.Evento montarEvento(GoogleCalendarConexao conexao, Agendamento a, Paciente paciente, Dentista dentista) {
        String procedimento = rotuloProcedimento(a);
        String nome = paciente.getNomeCompleto();
        String link = props.frontendUrl().replaceAll("/+$", "") + "/pacientes/" + paciente.getId();

        StringBuilder descricao = new StringBuilder()
                .append("Paciente: ").append(nome).append('\n')
                .append("Procedimento: ").append(procedimento).append('\n');
        if (dentista != null && dentista.getNomeCompleto() != null) {
            descricao.append("Dentista: ").append(dentista.getNomeCompleto()).append('\n');
        }
        descricao.append("Prontuário no Bokka (exige login): ").append(link);

        Map<String, String> privadas = new LinkedHashMap<>();
        privadas.put(PROP_AGENDAMENTO, a.getId());
        return new GoogleCalendarApi.Evento(idDoEvento(conexao.getId(), a.getId()), nome + " — " + procedimento,
                descricao.toString(), a.getDataHoraInicio(), a.getDataHoraFim(), privadas);
    }

    static String rotuloProcedimento(Agendamento a) {
        if (a.getNomeProcedimento() != null) {
            return a.getNomeProcedimento().getLabel();
        }
        TipoAgendamentoEnum tipo = a.getTipoAgendamento();
        if (tipo == null) {
            return "Consulta";
        }
        return switch (tipo) {
            case AVALIACAO -> "Avaliação";
            case CONSULTA -> "Consulta";
            case RETORNO -> "Retorno";
            case PROCEDIMENTO -> "Procedimento";
            case MANUTENCAO -> "Manutenção";
            case URGENCIA -> "Urgência";
            case DOCUMENTACAO -> "Documentação";
        };
    }

    /**
     * Id do evento no Google: 2 + 32 caracteres hexadecimais (dentro do alfabeto aceito, a-v e 0-9), derivados
     * de conexão + agendamento. Determinístico: criar duas vezes não duplica o evento.
     */
    static String idDoEvento(String conexaoId, String agendamentoId) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((conexaoId + "|" + agendamentoId).getBytes(StandardCharsets.UTF_8));
            return PREFIXO_EVENTO + HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String horario(LocalDateTime inicio, LocalDateTime fim) {
        return inicio + "|" + fim;
    }

    private static String resumo(GoogleCalendarApi.Evento e) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                    (e.titulo() + "\n" + e.descricao() + "\n" + horario(e.inicio(), e.fim())).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}

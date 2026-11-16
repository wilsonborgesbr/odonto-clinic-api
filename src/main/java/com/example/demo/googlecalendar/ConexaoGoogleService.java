package com.example.demo.googlecalendar;

import com.example.demo.config.GoogleCalendarAsyncConfig;
import com.example.demo.dto.GoogleCalendarDTOs.AuthUrlResponse;
import com.example.demo.dto.GoogleCalendarDTOs.ConexaoResponse;
import com.example.demo.dto.GoogleCalendarDTOs.StatusResponse;
import com.example.demo.enums.PermissaoEnum;
import com.example.demo.enums.SyncStatusGoogleEnum;
import com.example.demo.enums.EscopoGoogleCalendarEnum;
import com.example.demo.model.GoogleCalendarConexao;
import com.example.demo.model.GoogleCalendarEvento;
import com.example.demo.model.User;
import com.example.demo.repository.DentistaRepository;
import com.example.demo.repository.GoogleCalendarConexaoRepository;
import com.example.demo.repository.GoogleCalendarEventoRepository;
import com.example.demo.repository.UserRepository;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/** Ciclo de vida da conexão: autorizar (OAuth), consultar, desconectar e pausar quando o dono retira a permissão. */
@Service
public class ConexaoGoogleService {

    static final String URL_AUTORIZACAO = "https://accounts.google.com/o/oauth2/v2/auth";
    static final String ESCOPO_CALENDARIO = "https://www.googleapis.com/auth/calendar.events";
    static final String ESCOPOS = "openid email " + ESCOPO_CALENDARIO;
    static final String CALENDARIO_PRINCIPAL = "primary";

    /** Resultado do retorno do Google. {@code motivo} é um código estável para o frontend quando falha. */
    public record Resultado(boolean sucesso, String motivo) {
        static Resultado ok() { return new Resultado(true, null); }
        static Resultado erro(String motivo) { return new Resultado(false, motivo); }
    }

    private static final Logger log = LoggerFactory.getLogger(ConexaoGoogleService.class);

    @Autowired
    private GoogleCalendarConexaoRepository conexaoRepository;
    @Autowired
    private GoogleCalendarEventoRepository eventoRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DentistaRepository dentistaRepository;
    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private GoogleCalendarApi api;
    @Autowired
    private GoogleCalendarProperties props;
    @Autowired
    private EstadoOAuth estado;
    @Autowired
    private CriptografiaTokens criptografia;
    @Autowired
    private GoogleTokenService tokenService;
    @Autowired
    private CanalWatchService canalService;
    @Autowired
    private TentativasGoogle tentativas;
    @Autowired
    private SincronizacaoSaidaService saida;
    @Autowired
    @Qualifier(GoogleCalendarAsyncConfig.GOOGLE_CALENDAR_EXECUTOR)
    private Executor executor;

    private Clock relogio = Clock.systemUTC();

    void usarRelogio(Clock relogio) {
        this.relogio = relogio;
    }

    // ───────────── permissão ─────────────

    public static boolean temPermissao(User user) {
        return Boolean.TRUE.equals(user.getAtivo())
                && user.permissoesEfetivas().contains(PermissaoEnum.INTEGRACAO_GOOGLE_CALENDAR);
    }


    // ───────────── autorizar ─────────────

    /** O escopo não é escolhido por quem conecta: vem do cadastro do usuário, definido pelo proprietário/sócio. */
    public AuthUrlResponse gerarUrlAutorizacao(User user) {
        validarElegibilidade(user);
        EscopoGoogleCalendarEnum escopo = user.escopoGoogleEfetivo();
        String url = UriComponentsBuilder.fromUriString(URL_AUTORIZACAO)
                .queryParam("client_id", props.clientId())
                .queryParam("redirect_uri", props.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", ESCOPOS)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("include_granted_scopes", "true")
                .queryParam("state", estado.gerar(user.getId(), user.getClinicaId(), escopo))
                .build().encode().toUriString();
        return new AuthUrlResponse(url, escopo, relogio.instant().plus(EstadoOAuth.VALIDADE));
    }

    static final String SEM_PERMISSAO = "SEM_PERMISSAO";
    static final String SEM_DENTISTA_VINCULADO = "SEM_DENTISTA_VINCULADO";
    static final String DENTISTA_INATIVO = "DENTISTA_INATIVO";
    static final String JA_CONECTADO = "JA_CONECTADO";

    /** Código do que impede o usuário de conectar agora, ou null se pode. */
    String motivoBloqueio(User user) {
        if (!temPermissao(user)) {
            return SEM_PERMISSAO;
        }
        if (user.escopoGoogleEfetivo().exigeDentista()) {
            if (user.getDentistaId() == null || user.getDentistaId().isBlank()) {
                return SEM_DENTISTA_VINCULADO;
            }
            boolean dentistaAtivo = dentistaRepository.findByIdAndClinicaId(user.getDentistaId(), user.getClinicaId())
                    .filter(d -> Boolean.TRUE.equals(d.getAtivo())).isPresent();
            if (!dentistaAtivo) {
                return DENTISTA_INATIVO;
            }
        }
        Optional<GoogleCalendarConexao> propria = conexaoRepository.findByClinicaIdAndUserId(user.getClinicaId(), user.getId());
        if (propria.isPresent() && propria.get().getSyncStatus() == SyncStatusGoogleEnum.ATIVO) {
            return JA_CONECTADO;
        }
        return null;
    }

    /**
     * Regras de quem pode conectar; vale na geração da URL e de novo no retorno do Google.
     * Conexão existente só bloqueia se estiver ATIVA (as demais podem ser refeitas).
     */
    void validarElegibilidade(User user) {
        String motivo = motivoBloqueio(user);
        if (motivo == null) {
            return;
        }
        switch (motivo) {
            case SEM_PERMISSAO -> throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Sem permissão para a integração com o Google Calendar.");
            case SEM_DENTISTA_VINCULADO -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Seu usuário não está vinculado a um dentista. Peça ao proprietário para liberar \"todos os agendamentos da clínica\" para você.");
            case DENTISTA_INATIVO -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "O dentista vinculado ao seu usuário não está ativo.");
            default -> throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Você já tem uma conexão ativa. Desconecte antes de conectar de novo.");
        }
    }

    /** Retorno do Google. Nunca lança: qualquer falha vira um {@link Resultado} com código para o frontend. */
    public Resultado concluirAutorizacao(String codigo, String state, String erroDoGoogle) {
        Optional<EstadoOAuth.Dados> dados = estado.validar(state);
        if (dados.isEmpty()) {
            return Resultado.erro("estado_invalido");
        }
        if (erroDoGoogle != null && !erroDoGoogle.isBlank()) {
            return Resultado.erro("acesso_negado");
        }
        if (codigo == null || codigo.isBlank()) {
            return Resultado.erro("codigo_ausente");
        }
        try {
            User user = userRepository.findById(dados.get().userId())
                    .filter(u -> dados.get().clinicaId().equals(u.getClinicaId()))
                    .orElse(null);
            if (user == null) {
                return Resultado.erro("usuario_invalido");
            }
            validarElegibilidade(user);

            GoogleCalendarApi.Tokens tokens = api.trocarCodigo(codigo);
            if (tokens.refreshToken() == null) {
                return Resultado.erro("sem_refresh_token");
            }
            if (!escopoDeAgendaConcedido(tokens.scope())) {
                return Resultado.erro("escopo_insuficiente");
            }
            String email = api.emailDaConta(tokens.accessToken());
            String id = salvar(user, tokens, email);
            executor.execute(() -> canalService.ativar(id));
            return Resultado.ok();
        } catch (ResponseStatusException e) {
            return Resultado.erro(switch (e.getStatusCode().value()) {
                case 403 -> "sem_permissao";
                case 409 -> "ja_conectado";
                default -> "dados_invalidos";
            });
        } catch (GoogleApiException e) {
            log.warn("Falha ao concluir a autorização do Google: {}", e.getMessage());
            return Resultado.erro("falha_google");
        } catch (RuntimeException e) {
            log.error("Erro ao concluir a autorização do Google: {}", e.getMessage());
            return Resultado.erro("erro_interno");
        }
    }

    private String salvar(User user, GoogleCalendarApi.Tokens tokens, String email) {
        String clinicaId = user.getClinicaId();
        Optional<GoogleCalendarConexao> existente = conexaoRepository.findByClinicaIdAndUserId(clinicaId, user.getId());
        String id = existente.map(GoogleCalendarConexao::getId).orElseGet(() -> new ObjectId().toHexString());

        GoogleCalendarConexao.Tokens cifrados = GoogleCalendarConexao.Tokens.builder()
                .accessTokenEncrypted(criptografia.cifrar(tokens.accessToken(),
                        CriptografiaTokens.contexto(clinicaId, user.getId(), GoogleTokenService.CAMPO_ACCESS)))
                .refreshTokenEncrypted(criptografia.cifrar(tokens.refreshToken(),
                        CriptografiaTokens.contexto(clinicaId, user.getId(), GoogleTokenService.CAMPO_REFRESH)))
                .expiresAt(tokens.expiraEm())
                .scope(tokens.scope())
                .build();
        EscopoGoogleCalendarEnum escopo = user.escopoGoogleEfetivo();
        String dentistaId = escopo.exigeDentista() ? user.getDentistaId() : null;

        try {
            if (existente.isPresent()) {
                // Reconexão (acesso revogado, erro ou pausa): troca tudo e recomeça a sincronização do zero.
                canalService.encerrar(existente.get());
                Update update = new Update()
                        .set("escopo", escopo).set("dentistaId", dentistaId)
                        .set("googleEmail", email).set("googleCalendarId", CALENDARIO_PRINCIPAL)
                        .set("tokens", cifrados).set("syncStatus", SyncStatusGoogleEnum.ATIVO)
                        .unset("webhookWatch").unset("syncToken")
                        .set("updatedAt", LocalDateTime.now());
                mongoTemplate.updateFirst(new Query(Criteria.where("id").is(id)), update, GoogleCalendarConexao.class);
                eventoRepository.deleteByConexaoId(id);
            } else {
                LocalDateTime agora = LocalDateTime.now();
                conexaoRepository.insert(GoogleCalendarConexao.builder()
                        .id(id).clinicaId(clinicaId).userId(user.getId()).dentistaId(dentistaId)
                        .escopo(escopo).googleEmail(email).googleCalendarId(CALENDARIO_PRINCIPAL)
                        .tokens(cifrados).syncStatus(SyncStatusGoogleEnum.ATIVO)
                        .createdAt(agora).updatedAt(agora).build());
            }
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Conexão já existe.");
        }
        return id;
    }

    // ───────────── consultar ─────────────

    public StatusResponse status(User user) {
        Optional<GoogleCalendarConexao> conexao = conexaoRepository.findByClinicaIdAndUserId(user.getClinicaId(), user.getId());
        String motivo = motivoBloqueio(user);
        return new StatusResponse(temPermissao(user), !props.credenciaisPlaceholder(), conexao.isPresent(),
                conexao.map(c -> ConexaoResponse.de(c, user.getName())).orElse(null),
                user.escopoGoogleEfetivo(), motivo == null, motivo);
    }

    /** Todas as conexões da clínica, para a tela do dono. */
    public List<ConexaoResponse> listar(String clinicaId) {
        List<GoogleCalendarConexao> conexoes = conexaoRepository.findByClinicaId(clinicaId);
        Map<String, String> nomes = userRepository.findAllById(conexoes.stream().map(GoogleCalendarConexao::getUserId).toList())
                .stream().filter(u -> clinicaId.equals(u.getClinicaId()))
                .collect(Collectors.toMap(User::getId, u -> u.getName() == null ? "" : u.getName(), (a, b) -> a));
        return conexoes.stream().map(c -> ConexaoResponse.de(c, nomes.get(c.getUserId()))).toList();
    }

    // ───────────── desconectar / pausar ─────────────

    /**
     * Desconectar remove TUDO da clínica da conta Google: primeiro apaga no Google cada evento criado pelo Bokka
     * (síncrono; 404/410 contam como já removido; uma falha é registrada e não interrompe as demais), depois
     * os vínculos e a conexão, e por fim para o canal e revoga o token em segundo plano. É o único fluxo que
     * remove a conexão (além de inativar o usuário): pausar (dono retira a permissão) mantém os eventos.
     */
    public void desconectar(User user) {
        GoogleCalendarConexao conexao = conexaoRepository.findByClinicaIdAndUserId(user.getClinicaId(), user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Você não tem conexão com o Google Calendar."));
        desconectar(conexao);
    }

    /**
     * Mesma limpeza para quando o dono inativa o usuário (ex-funcionário não mantém dados da clínica no Google dele).
     * Nunca lança: a inativação do usuário não pode falhar por causa do Google.
     *
     * @return true se havia conexão e ela foi removida
     */
    public boolean desconectarDoUsuario(String clinicaId, String userId) {
        try {
            Optional<GoogleCalendarConexao> conexao = conexaoRepository.findByClinicaIdAndUserId(clinicaId, userId);
            if (conexao.isEmpty()) {
                return false;
            }
            desconectar(conexao.get());
            return true;
        } catch (RuntimeException e) {
            log.error("Falha ao desconectar o Google Calendar do usuário {}: {}", userId, e.getMessage());
            return false;
        }
    }

    private void desconectar(GoogleCalendarConexao conexao) {
        // Fora de ATIVO a sincronização deixa de criar eventos novos enquanto a limpeza roda.
        mongoTemplate.updateFirst(new Query(Criteria.where("id").is(conexao.getId())),
                new Update().set("syncStatus", SyncStatusGoogleEnum.PAUSADO_PELO_DONO).set("updatedAt", LocalDateTime.now()),
                GoogleCalendarConexao.class);

        var token = tokenService.accessTokenParaEncerrar(conexao);
        int[] resultado = apagarEventosNoGoogle(conexao, token.orElse(null));

        conexaoRepository.deleteById(conexao.getId());
        // Varredura final: um agendamento sincronizado em paralelo pode ter gravado vínculo durante a limpeza.
        apagarEventosNoGoogle(conexao, token.orElse(null));
        eventoRepository.deleteByConexaoId(conexao.getId());
        if (resultado[1] > 0) {
            log.warn("Desconexão {}: {} evento(s) removido(s) no Google, {} não puderam ser removidos.",
                    conexao.getId(), resultado[0], resultado[1]);
        } else {
            log.info("Desconexão {}: {} evento(s) removido(s) no Google.", conexao.getId(), resultado[0]);
        }
        executor.execute(() -> encerrarNoGoogle(conexao));
    }

    /** @return {removidos, falhas} */
    private int[] apagarEventosNoGoogle(GoogleCalendarConexao conexao, String accessToken) {
        int removidos = 0;
        int falhas = 0;
        for (GoogleCalendarEvento elo : eventoRepository.findByConexaoId(conexao.getId())) {
            if (accessToken == null) {
                falhas++;
                log.warn("Evento {} da conexão {} não removido do Google: sem token válido.", elo.getGoogleEventId(), conexao.getId());
                continue;
            }
            try {
                tentativas.executar(() -> api.excluirEvento(accessToken, conexao.getGoogleCalendarId(), elo.getGoogleEventId()));
                eventoRepository.deleteById(elo.getId());
                removidos++;
            } catch (RuntimeException e) {
                falhas++;
                log.warn("Evento {} da conexão {} não removido do Google: {}", elo.getGoogleEventId(), conexao.getId(), e.getMessage());
            }
        }
        return new int[]{removidos, falhas};
    }

    private void encerrarNoGoogle(GoogleCalendarConexao conexao) {
        try {
            var token = tokenService.accessTokenParaEncerrar(conexao);
            var watch = conexao.getWebhookWatch();
            if (watch != null && watch.getChannelId() != null && token.isPresent()) {
                try {
                    api.pararCanal(token.get(), watch.getChannelId(), watch.getResourceId());
                } catch (RuntimeException e) {
                    log.warn("Não foi possível parar o canal {}: {}", watch.getChannelId(), e.getMessage());
                }
            }
            // Revogar derruba a concessão inteira da conta; se outra conexão usa a mesma conta, ela ficaria sem acesso.
            boolean contaCompartilhada = conexao.getGoogleEmail() != null
                    && conexaoRepository.existsByGoogleEmailAndIdNot(conexao.getGoogleEmail(), conexao.getId());
            if (!contaCompartilhada && conexao.getTokens() != null) {
                api.revogar(tokenService.refreshToken(conexao));
            }
        } catch (RuntimeException e) {
            log.warn("Encerramento no Google da conexão {} incompleto: {}", conexao.getId(), e.getMessage());
        }
    }

    /**
     * Chamado depois que o proprietário/sócio muda o escopo do usuário. A conexão passa a seguir o novo escopo.
     * Ao RESTRINGIR (todos → só os meus) os eventos que deixaram de valer saem da agenda Google antes de responder
     * (privacidade); ao ampliar, o envio dos demais roda em segundo plano. Nunca lança.
     */
    public void aplicarNovoEscopo(User user) {
        try {
            Optional<GoogleCalendarConexao> existente = conexaoRepository.findByClinicaIdAndUserId(user.getClinicaId(), user.getId());
            if (existente.isEmpty()) {
                return;
            }
            EscopoGoogleCalendarEnum novo = user.escopoGoogleEfetivo();
            EscopoGoogleCalendarEnum atual = existente.get().getEscopo() != null
                    ? existente.get().getEscopo() : EscopoGoogleCalendarEnum.SOMENTE_PROPRIO;
            if (novo == atual) {
                return;
            }
            Update update = new Update().set("escopo", novo).set("updatedAt", LocalDateTime.now());
            if (novo.exigeDentista()) {
                update.set("dentistaId", user.getDentistaId());
            } else {
                update.unset("dentistaId");
            }
            mongoTemplate.updateFirst(new Query(Criteria.where("id").is(existente.get().getId())), update, GoogleCalendarConexao.class);
            log.info("Escopo da conexão {} alterado de {} para {}.", existente.get().getId(), atual, novo);
            GoogleCalendarConexao atualizada = conexaoRepository.findById(existente.get().getId()).orElse(null);
            if (atualizada == null || atualizada.getSyncStatus() != SyncStatusGoogleEnum.ATIVO) {
                return;
            }
            if (novo.exigeDentista()) {
                saida.reenviar(atualizada);
            } else {
                executor.execute(() -> saida.reenviar(atualizada));
            }
        } catch (RuntimeException e) {
            log.error("Falha ao aplicar o novo escopo do usuário {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Chamado quando o dono retira a permissão (ou inativa o usuário): a conexão passa a PAUSADO_PELO_DONO na hora
     * e o canal de notificação é parado no Google em segundo plano.
     *
     * @return true se havia uma conexão ativa e ela foi pausada
     */
    public boolean pausarPorPerdaDePermissao(String clinicaId, String userId) {
        Optional<GoogleCalendarConexao> conexao = conexaoRepository.findByClinicaIdAndUserId(clinicaId, userId);
        if (conexao.isEmpty() || conexao.get().getSyncStatus() != SyncStatusGoogleEnum.ATIVO) {
            return false;
        }
        var resultado = mongoTemplate.updateFirst(
                new Query(Criteria.where("id").is(conexao.get().getId()).and("syncStatus").is(SyncStatusGoogleEnum.ATIVO)),
                new Update().set("syncStatus", SyncStatusGoogleEnum.PAUSADO_PELO_DONO).set("updatedAt", LocalDateTime.now()),
                GoogleCalendarConexao.class);
        if (resultado.getModifiedCount() == 0) {
            return false;
        }
        log.info("Conexão Google Calendar do usuário {} pausada pelo dono.", userId);
        GoogleCalendarConexao paraEncerrar = conexao.get();
        executor.execute(() -> canalService.encerrar(paraEncerrar));
        return true;
    }

    /** O usuário pode desmarcar a permissão de agenda na tela do Google; sem ela não há o que sincronizar. */
    static boolean escopoDeAgendaConcedido(String scope) {
        if (scope == null) {
            return false;
        }
        for (String s : scope.split(" ")) {
            if (s.equals(ESCOPO_CALENDARIO) || s.equals("https://www.googleapis.com/auth/calendar")) {
                return true;
            }
        }
        return false;
    }
}

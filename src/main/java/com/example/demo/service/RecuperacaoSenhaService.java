package com.example.demo.service;

import com.example.demo.dto.MensagemDTO;
import com.example.demo.dto.RecuperarSenhaDTO;
import com.example.demo.dto.RedefinirSenhaDTO;
import com.example.demo.dto.ValidarCodigoRecuperacaoDTO;
import com.example.demo.dto.ValidarCodigoResponseDTO;
import com.example.demo.dto.ValidarTokenRequestDTO;
import com.example.demo.dto.ValidarTokenResponseDTO;
import com.example.demo.email.EmailDispatcher;
import com.example.demo.email.EmailService;
import com.example.demo.email.EmailTemplateService;
import com.example.demo.enums.TipoTokenAuthEnum;
import com.example.demo.model.AuthToken;
import com.example.demo.model.Clinica;
import com.example.demo.model.User;
import com.example.demo.repository.ClinicaRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.util.FormatadorTexto;
import com.example.demo.util.FusoBrasil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class RecuperacaoSenhaService {

    static final String MSG_NEUTRA = "Se os dados informados estiverem corretos, enviamos as instruções para o seu e-mail.";
    static final Duration VALIDADE = Duration.ofMinutes(30);
    static final String TEXTO_VALIDADE = "30 minutos";
    static final Duration VALIDADE_CODIGO = Duration.ofMinutes(15);
    static final String TEXTO_VALIDADE_CODIGO = "15 minutos";
    static final Duration VALIDADE_TICKET = Duration.ofMinutes(5);
    static final int MAX_VALIDACOES_POR_EMAIL = 5;
    static final int MAX_VALIDACOES_POR_IP = 10;
    static final String MSG_CODIGO_INVALIDO = "Código inválido ou expirado. Verifique os dados ou solicite um novo.";
    static final String MSG_LIMITE_CODIGO = "Muitas tentativas. Aguarde alguns minutos e tente novamente.";
    static final String ASSUNTO_RESET = "Redefinir sua senha no Bokka";
    static final String ASSUNTO_ALERTA = "Sua senha foi alterada - Bokka";
    static final int MAX_PEDIDOS_POR_EMAIL = 3;
    static final int MAX_PEDIDOS_POR_IP = 10;
    static final Duration JANELA = Duration.ofMinutes(15);

    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR"));

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Autowired
    private AuthTokenService authTokenService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private EmailTemplateService templateService;

    @Autowired
    private EmailDispatcher dispatcher;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClinicaRepository clinicaRepository;

    @Autowired
    private UserLookupService userLookupService;

    @Autowired
    private RateLimitService rateLimitService;

    /** Piso de tempo da validação do código: toda resposta (certa ou errada) leva pelo menos isto. */
    @Value("${recuperacao.codigo.tempo-minimo-ms:250}")
    private long tempoMinimoValidacaoMs = 250;

    /**
     * Sempre devolve a mesma mensagem, exista a conta ou não, e mesmo com o limite estourado. O que depende
     * de a conta existir (emitir token e enviar) roda em segundo plano para não alterar o tempo de resposta.
     */
    public MensagemDTO solicitar(RecuperarSenhaDTO dto, String ip) {
        String email = FormatadorTexto.email(dto.getEmail());
        boolean porEmail = rateLimitService.registrarSePermitido("recuperar:email:" + email, MAX_PEDIDOS_POR_EMAIL, JANELA);
        boolean porIp = rateLimitService.registrarSePermitido("recuperar:ip:" + ip, MAX_PEDIDOS_POR_IP, JANELA);
        if (!porEmail || !porIp) {
            return new MensagemDTO(MSG_NEUTRA);
        }

        for (Alvo alvo : localizarContas(email, dto.getClinicaCodigo())) {
            enviarReset(alvo, ip);
        }
        return new MensagemDTO(MSG_NEUTRA);
    }

    /**
     * Passo 1 do fluxo por código: confere o código de 6 dígitos e devolve um ticket de 5 minutos para o
     * {@code /redefinir-senha}. Conta inexistente, clínica inexistente ou inativa, código errado, vencido,
     * bloqueado ou já usado dão a mesma resposta (400), e o tempo é nivelado por um piso fixo.
     */
    public ValidarCodigoResponseDTO validarCodigo(ValidarCodigoRecuperacaoDTO dto, String ip) {
        String email = FormatadorTexto.email(dto.getEmail());
        boolean porIp = rateLimitService.registrarSePermitido("validar-codigo:ip:" + ip, MAX_VALIDACOES_POR_IP, JANELA);
        boolean porEmail = rateLimitService.registrarSePermitido("validar-codigo:email:" + email, MAX_VALIDACOES_POR_EMAIL, JANELA);
        if (!porIp || !porEmail) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, MSG_LIMITE_CODIGO);
        }

        long inicio = System.nanoTime();
        try {
            List<Alvo> alvos = localizarContas(email, dto.getClinicaCodigo());
            if (alvos.size() != 1) {
                authTokenService.simularConferencia(dto.getCodigo());
                throw codigoInvalido();
            }
            Alvo alvo = alvos.get(0);
            AuthToken consumido = authTokenService
                    .consumirPorCodigo(alvo.user().getId(), TipoTokenAuthEnum.RESET_SENHA, dto.getCodigo())
                    .orElseThrow(this::codigoInvalido);
            var ticket = authTokenService.emitir(alvo.user().getId(), consumido.getClinicaId(),
                    TipoTokenAuthEnum.RESET_SENHA, VALIDADE_TICKET, ip);
            return new ValidarCodigoResponseDTO(true, ticket.token(), alvo.user().getName(),
                    alvo.clinica().getNome(), alvo.clinica().getCodigo());
        } finally {
            aguardarPisoDeTempo(inicio);
        }
    }

    private void aguardarPisoDeTempo(long inicioNanos) {
        long faltaMs = tempoMinimoValidacaoMs - (System.nanoTime() - inicioNanos) / 1_000_000;
        if (faltaMs > 0) {
            try {
                Thread.sleep(faltaMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public ValidarTokenResponseDTO validarToken(ValidarTokenRequestDTO dto) {
        TipoTokenAuthEnum tipo = parseTipo(dto.getTipo());
        AuthToken token = authTokenService.validar(dto.getToken(), tipo);
        User user = userRepository.findById(token.getUserId()).orElseThrow(this::invalido);
        Clinica clinica = clinicaRepository.findById(user.getClinicaId()).orElseThrow(this::invalido);
        return new ValidarTokenResponseDTO(true, user.getName(), clinica.getNome(), clinica.getCodigo());
    }

    public MensagemDTO redefinir(RedefinirSenhaDTO dto) {
        AuthToken consumido = authTokenService.consumir(dto.getToken(), TipoTokenAuthEnum.RESET_SENHA);
        User user = userRepository.findById(consumido.getUserId())
                .filter(this::podeEntrar)
                .orElseThrow(this::invalido);

        user.setPassword(encoder.encode(dto.getNovaSenha()));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        authTokenService.invalidarPendentes(user.getId(), TipoTokenAuthEnum.RESET_SENHA);

        clinicaRepository.findById(user.getClinicaId()).ifPresent(clinica -> enviarAlerta(user, clinica));
        return new MensagemDTO("Senha alterada com sucesso.");
    }

    private record Alvo(User user, Clinica clinica) {}

    private List<Alvo> localizarContas(String email, String clinicaCodigo) {
        if (email == null) {
            return List.of();
        }
        String codigo = clinicaCodigo == null ? "" : clinicaCodigo.trim().toLowerCase();
        List<Alvo> alvos = new ArrayList<>();
        if (!codigo.isEmpty()) {
            Optional<Clinica> clinica = clinicaRepository.findByCodigo(codigo).filter(c -> Boolean.TRUE.equals(c.getAtivo()));
            clinica.flatMap(c -> userLookupService.buscarPorEmail(email, c.getId())
                            .filter(this::podeEntrar)
                            .map(u -> new Alvo(u, c)))
                    .ifPresent(alvos::add);
            return alvos;
        }
        List<User> contas = userLookupService.buscarContasPorEmail(email).stream().filter(this::podeEntrar).toList();
        if (contas.isEmpty()) {
            return alvos;
        }
        Map<String, Clinica> clinicas = clinicaRepository.findAllById(
                        contas.stream().map(User::getClinicaId).distinct().toList()).stream()
                .filter(c -> Boolean.TRUE.equals(c.getAtivo()))
                .collect(Collectors.toMap(Clinica::getId, c -> c));
        for (User conta : contas) {
            Clinica clinica = clinicas.get(conta.getClinicaId());
            if (clinica != null) {
                alvos.add(new Alvo(conta, clinica));
            }
        }
        return alvos;
    }

    private void enviarReset(Alvo alvo, String ip) {
        String userId = alvo.user().getId();
        String clinicaId = alvo.clinica().getId();
        String codigo = alvo.clinica().getCodigo();
        String nome = alvo.user().getName();
        String nomeClinica = alvo.clinica().getNome();
        String destino = alvo.user().getEmail();
        dispatcher.executar("reset-senha", () -> {
            var emitido = authTokenService.emitirComCodigo(userId, clinicaId, TipoTokenAuthEnum.RESET_SENHA,
                    VALIDADE, VALIDADE_CODIGO, ip);
            String link = templateService.linkFrontend("/redefinir-senha", "token", emitido.token(), "c", codigo);
            Map<String, String> variaveis = Map.of(
                    "nome", vazioSeNulo(nome),
                    "nomeClinica", vazioSeNulo(nomeClinica),
                    "link", link,
                    "validade", TEXTO_VALIDADE,
                    "codigoFormatado", emitido.codigo().substring(0, 3) + " " + emitido.codigo().substring(3),
                    "validadeCodigo", TEXTO_VALIDADE_CODIGO);
            emailService.enviar(destino, ASSUNTO_RESET,
                    templateService.renderizar("reset-senha", variaveis),
                    templateService.renderizarTexto("reset-senha", variaveis),
                    "reset-senha");
        });
    }

    private void enviarAlerta(User user, Clinica clinica) {
        String nome = vazioSeNulo(user.getName());
        String nomeClinica = vazioSeNulo(clinica.getNome());
        String destino = user.getEmail();
        String codigo = clinica.getCodigo();
        String dataHora = DATA_HORA.format(java.time.ZonedDateTime.now(FusoBrasil.ZONA));
        dispatcher.executar("senha-alterada-alerta", () -> {
            String link = templateService.linkFrontend("/recuperar-senha", "c", codigo);
            Map<String, String> variaveis = Map.of(
                    "nome", nome,
                    "nomeClinica", nomeClinica,
                    "link", link,
                    "dataHora", dataHora);
            emailService.enviar(destino, ASSUNTO_ALERTA,
                    templateService.renderizar("senha-alterada-alerta", variaveis),
                    templateService.renderizarTexto("senha-alterada-alerta", variaveis),
                    "senha-alterada");
        });
    }

    private TipoTokenAuthEnum parseTipo(String tipo) {
        try {
            return TipoTokenAuthEnum.valueOf(tipo.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw invalido();
        }
    }

    private boolean podeEntrar(User u) {
        return Boolean.TRUE.equals(u.getAtivo()) && !u.contaBloqueada();
    }

    private String vazioSeNulo(String valor) {
        return valor == null ? "" : valor;
    }

    private ResponseStatusException codigoInvalido() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, MSG_CODIGO_INVALIDO);
    }

    private ResponseStatusException invalido() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, AuthTokenService.MENSAGEM_INVALIDO);
    }
}

package com.example.demo.service;

import com.example.demo.dto.MensagemDTO;
import com.example.demo.dto.RecuperarSenhaDTO;
import com.example.demo.dto.RedefinirSenhaDTO;
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
            var emitido = authTokenService.emitir(userId, clinicaId, TipoTokenAuthEnum.RESET_SENHA, VALIDADE, ip);
            String link = templateService.linkFrontend("/redefinir-senha", "token", emitido.token(), "c", codigo);
            String html = templateService.renderizar("reset-senha", Map.of(
                    "nome", vazioSeNulo(nome),
                    "nomeClinica", vazioSeNulo(nomeClinica),
                    "link", link,
                    "validade", TEXTO_VALIDADE));
            emailService.enviar(destino, "Recuperação de senha - " + vazioSeNulo(nomeClinica) + " (Bokka)", html);
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
            String html = templateService.renderizar("senha-alterada-alerta", Map.of(
                    "nome", nome,
                    "nomeClinica", nomeClinica,
                    "link", link,
                    "dataHora", dataHora));
            emailService.enviar(destino, "Sua senha foi alterada - Bokka", html);
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

    private ResponseStatusException invalido() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, AuthTokenService.MENSAGEM_INVALIDO);
    }
}

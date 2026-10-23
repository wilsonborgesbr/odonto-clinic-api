package com.example.demo.service;

import com.example.demo.dto.MensagemDTO;
import com.example.demo.email.EmailDispatcher;
import com.example.demo.email.EmailService;
import com.example.demo.email.EmailTemplateService;
import com.example.demo.enums.RoleEnum;
import com.example.demo.enums.TipoTokenAuthEnum;
import com.example.demo.model.AuthToken;
import com.example.demo.model.Clinica;
import com.example.demo.model.User;
import com.example.demo.repository.ClinicaRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.AuthContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class ConfirmacaoEmailService {

    static final Duration VALIDADE = Duration.ofHours(48);
    static final String TEXTO_VALIDADE = "48 horas";
    static final int MAX_REENVIOS_POR_USUARIO = 3;
    static final int MAX_REENVIOS_POR_IP = 10;
    static final Duration JANELA = Duration.ofMinutes(15);

    static final String MSG_LIMITE = "Muitos pedidos de reenvio. Aguarde alguns minutos e tente novamente.";
    static final String MSG_REENVIADO = "Enviamos um novo link de confirmação para o seu e-mail.";
    static final String MSG_JA_CONFIRMADO = "Seu e-mail já está confirmado.";

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
    private RateLimitService rateLimitService;

    /** Emite o token e envia o e-mail de confirmação em segundo plano; falha de envio nunca chega ao usuário. */
    public void enviarConfirmacao(User dono, Clinica clinica, String ip) {
        String userId = dono.getId();
        String clinicaId = clinica.getId();
        String nome = dono.getName();
        String email = dono.getEmail();
        String nomeClinica = clinica.getNome();
        String codigo = clinica.getCodigo();
        dispatcher.executar("confirmar-email-dono", () -> {
            var emitido = authTokenService.emitir(userId, clinicaId, TipoTokenAuthEnum.CONFIRMAR_EMAIL_DONO, VALIDADE, ip);
            String link = templateService.linkFrontend("/confirmar-email", "token", emitido.token(), "c", codigo);
            String html = templateService.renderizar("confirmar-email-dono", Map.of(
                    "nome", nome == null ? "" : nome,
                    "nomeClinica", nomeClinica == null ? "" : nomeClinica,
                    "link", link,
                    "validade", TEXTO_VALIDADE));
            emailService.enviar(email, "Confirme seu e-mail - Bokka", html);
        });
    }

    /**
     * Consome o token e marca o e-mail do dono como confirmado. Devolve {@code true} se o e-mail já estava
     * confirmado antes (token válido de usuário já confirmado), {@code false} se acabou de confirmar.
     */
    public boolean confirmar(String token) {
        AuthToken consumido = authTokenService.consumir(token, TipoTokenAuthEnum.CONFIRMAR_EMAIL_DONO);
        User user = userRepository.findById(consumido.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, AuthTokenService.MENSAGEM_INVALIDO));
        boolean jaConfirmado = user.emailEstaConfirmado();
        if (!Boolean.TRUE.equals(user.getEmailConfirmado())) {
            user.setEmailConfirmado(true);
            if (!jaConfirmado) {
                user.setEmailConfirmadoEm(LocalDateTime.now());
            }
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
        }
        return jaConfirmado;
    }

    public MensagemDTO reenviar(String ip) {
        User atual = AuthContextHelper.currentUser();
        if (atual.getRole() != RoleEnum.PROPRIETARIO) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas o proprietário pode pedir a confirmação do e-mail.");
        }
        if (atual.emailEstaConfirmado()) {
            return new MensagemDTO(MSG_JA_CONFIRMADO);
        }

        boolean porUsuario = rateLimitService.registrarSePermitido("reenvio:user:" + atual.getId(), MAX_REENVIOS_POR_USUARIO, JANELA);
        boolean porIp = rateLimitService.registrarSePermitido("reenvio:ip:" + ip, MAX_REENVIOS_POR_IP, JANELA);
        if (!porUsuario || !porIp) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, MSG_LIMITE);
        }

        Clinica clinica = clinicaRepository.findById(atual.getClinicaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Clínica não encontrada."));
        enviarConfirmacao(atual, clinica, ip);
        return new MensagemDTO(MSG_REENVIADO);
    }
}

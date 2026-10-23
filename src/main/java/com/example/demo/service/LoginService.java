package com.example.demo.service;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.example.demo.dto.AuthResponseDTO;
import com.example.demo.dto.LoginRequestDTO;
import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.SelecaoClinicaDTO;
import com.example.demo.dto.SelecaoClinicaDTO.ClinicaOpcao;
import com.example.demo.model.Clinica;
import com.example.demo.model.User;
import com.example.demo.repository.ClinicaRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.util.FormatadorTexto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LoginService {

    static final int MAX_FALHAS_POR_EMAIL = 10;
    static final int MAX_FALHAS_POR_IP = 30;
    static final Duration JANELA = Duration.ofMinutes(15);

    static final String MSG_CREDENCIAIS = "Credenciais inválidas.";
    static final String MSG_LIMITE = "Muitas tentativas. Aguarde alguns minutos e tente novamente.";
    static final String MSG_TICKET = "Ticket inválido ou expirado. Faça login novamente.";

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    /** Hash descartável para gastar o mesmo tempo de BCrypt quando o e-mail não existe. */
    private final String hashFalso = encoder.encode(UUID.randomUUID().toString());

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClinicaRepository clinicaRepository;

    @Autowired
    private UserLookupService userLookupService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private RateLimitService rateLimitService;

    public LoginResponse login(LoginRequestDTO data, String ip) {
        String email = FormatadorTexto.email(data.getEmail());
        String chaveIp = "login:ip:" + ip;
        String chaveEmail = "login:email:" + email;
        verificarLimite(chaveEmail, chaveIp);

        String codigo = data.getClinicaCodigo() == null ? "" : data.getClinicaCodigo().trim();
        LoginResponse resposta = codigo.isEmpty()
                ? loginSemCodigo(data, email, chaveEmail, chaveIp)
                : loginComCodigo(data, codigo, chaveEmail, chaveIp);
        rateLimitService.esquecer(chaveEmail);
        return resposta;
    }

    /** Fluxo original: (código da clínica + e-mail + senha). */
    private LoginResponse loginComCodigo(LoginRequestDTO data, String codigo, String chaveEmail, String chaveIp) {
        Clinica clinica = clinicaRepository.findByCodigo(codigo.toLowerCase())
                .filter(c -> Boolean.TRUE.equals(c.getAtivo()))
                .orElse(null);
        if (clinica == null) {
            encoder.matches(data.getPassword(), hashFalso);
            throw falhar(chaveEmail, chaveIp, "Clínica não encontrada.");
        }

        User user = userLookupService.buscarPorEmail(data.getEmail(), clinica.getId())
                .filter(this::podeEntrar)
                .orElse(null);
        if (user == null) {
            encoder.matches(data.getPassword(), hashFalso);
            throw falhar(chaveEmail, chaveIp, MSG_CREDENCIAIS);
        }
        if (!senhaConfere(data.getPassword(), user)) {
            throw falhar(chaveEmail, chaveIp, MSG_CREDENCIAIS);
        }
        return AuthResponseDTO.de(tokenService.generateToken(user, clinica.getCodigo(), clinica.getNome()), user);
    }

    /**
     * Sem código: procura o e-mail em todas as clínicas, mas só revela algo depois que a senha casa.
     * Senha errada, e-mail inexistente e conta inativa respondem igual (401 genérico, mesmo custo de BCrypt).
     */
    private LoginResponse loginSemCodigo(LoginRequestDTO data, String email, String chaveEmail, String chaveIp) {
        Map<String, Clinica> clinicasAtivas = new LinkedHashMap<>();
        List<User> candidatos = new ArrayList<>();
        List<User> contas = userLookupService.buscarContasPorEmail(email);
        if (!contas.isEmpty()) {
            Map<String, Clinica> porId = clinicaRepository.findAllById(
                            contas.stream().map(User::getClinicaId).distinct().toList()).stream()
                    .filter(c -> Boolean.TRUE.equals(c.getAtivo()))
                    .collect(Collectors.toMap(Clinica::getId, Function.identity()));
            for (User conta : contas) {
                if (podeEntrar(conta) && porId.containsKey(conta.getClinicaId())) {
                    candidatos.add(conta);
                    clinicasAtivas.put(conta.getClinicaId(), porId.get(conta.getClinicaId()));
                }
            }
        }

        if (candidatos.isEmpty()) {
            encoder.matches(data.getPassword(), hashFalso);
            throw falhar(chaveEmail, chaveIp, MSG_CREDENCIAIS);
        }

        List<User> casaram = new ArrayList<>();
        for (User candidato : candidatos) {
            if (senhaConfere(data.getPassword(), candidato)) {
                casaram.add(candidato);
            }
        }

        if (casaram.isEmpty()) {
            throw falhar(chaveEmail, chaveIp, MSG_CREDENCIAIS);
        }
        if (casaram.size() == 1) {
            User user = casaram.get(0);
            Clinica clinica = clinicasAtivas.get(user.getClinicaId());
            return AuthResponseDTO.de(tokenService.generateToken(user, clinica.getCodigo(), clinica.getNome()), user);
        }

        List<ClinicaOpcao> opcoes = casaram.stream()
                .map(u -> clinicasAtivas.get(u.getClinicaId()))
                .map(c -> new ClinicaOpcao(c.getId(), c.getCodigo(), c.getNome()))
                .sorted(Comparator.comparing(ClinicaOpcao::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();
        String ticket = tokenService.generateTicketSelecaoClinica(casaram.stream().map(User::getId).toList());
        return SelecaoClinicaDTO.de(ticket, opcoes);
    }

    public AuthResponseDTO selecionarClinica(String ticket, String clinicaId, String ip) {
        String chaveIp = "selecao:ip:" + ip;
        if (rateLimitService.atingiuLimite(chaveIp, MAX_FALHAS_POR_IP, JANELA)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, MSG_LIMITE);
        }

        List<String> userIds;
        try {
            userIds = tokenService.validarTicketSelecaoClinica(ticket);
        } catch (JWTVerificationException e) {
            rateLimitService.registrar(chaveIp, JANELA);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, MSG_TICKET);
        }

        User user = userRepository.findAllById(userIds).stream()
                .filter(u -> clinicaId.equals(u.getClinicaId()))
                .filter(this::podeEntrar)
                .findFirst()
                .orElse(null);
        Clinica clinica = user == null ? null : clinicaRepository.findById(user.getClinicaId())
                .filter(c -> Boolean.TRUE.equals(c.getAtivo()))
                .orElse(null);
        if (clinica == null) {
            rateLimitService.registrar(chaveIp, JANELA);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, MSG_TICKET);
        }
        return AuthResponseDTO.de(tokenService.generateToken(user, clinica.getCodigo(), clinica.getNome()), user);
    }

    private boolean podeEntrar(User u) {
        return Boolean.TRUE.equals(u.getAtivo()) && !u.contaBloqueada();
    }

    private boolean senhaConfere(String senha, User user) {
        return user.getPassword() != null && encoder.matches(senha, user.getPassword());
    }

    private void verificarLimite(String chaveEmail, String chaveIp) {
        if (rateLimitService.atingiuLimite(chaveEmail, MAX_FALHAS_POR_EMAIL, JANELA)
                || rateLimitService.atingiuLimite(chaveIp, MAX_FALHAS_POR_IP, JANELA)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, MSG_LIMITE);
        }
    }

    private ResponseStatusException falhar(String chaveEmail, String chaveIp, String mensagem) {
        rateLimitService.registrar(chaveEmail, JANELA);
        rateLimitService.registrar(chaveIp, JANELA);
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, mensagem);
    }
}

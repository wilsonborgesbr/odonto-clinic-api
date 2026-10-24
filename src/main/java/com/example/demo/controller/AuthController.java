package com.example.demo.controller;

import com.example.demo.dto.AuthResponseDTO;
import com.example.demo.dto.ConfirmarEmailDTO;
import com.example.demo.dto.ConfirmarEmailResponseDTO;
import com.example.demo.dto.LoginRequestDTO;
import com.example.demo.dto.LoginResponse;
import com.example.demo.dto.MensagemDTO;
import com.example.demo.dto.RecuperarSenhaDTO;
import com.example.demo.dto.RedefinirSenhaDTO;
import com.example.demo.dto.RegisterClinicaRequestDTO;
import com.example.demo.dto.SelecionarClinicaRequestDTO;
import com.example.demo.dto.UpdateProfileDTO;
import com.example.demo.dto.ValidarCodigoRecuperacaoDTO;
import com.example.demo.dto.ValidarCodigoResponseDTO;
import com.example.demo.dto.ValidarTokenRequestDTO;
import com.example.demo.dto.ValidarTokenResponseDTO;
import com.example.demo.enums.RoleEnum;
import com.example.demo.enums.StatusContaEnum;
import com.example.demo.enums.TipoTokenAuthEnum;
import com.example.demo.model.Clinica;
import com.example.demo.model.User;
import com.example.demo.repository.ClinicaRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.AuthContextHelper;
import com.example.demo.util.FormatadorTexto;
import com.example.demo.service.AuthTokenService;
import com.example.demo.service.ConfirmacaoEmailService;
import com.example.demo.service.LoginService;
import com.example.demo.service.RecuperacaoSenhaService;
import com.example.demo.service.UserLookupService;
import com.example.demo.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticação", description = "Login multi-tenant, registro de clínica e perfil")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClinicaRepository clinicaRepository;

    @Autowired
    private UserLookupService userLookupService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private LoginService loginService;

    @Autowired
    private RecuperacaoSenhaService recuperacaoSenhaService;

    @Autowired
    private ConfirmacaoEmailService confirmacaoEmailService;

    @Autowired
    private AuthTokenService authTokenService;

    /**
     * Login: (clinicaCodigo + email + senha). O código da clínica identifica o tenant; o email é único dentro
     * dela. Sem código, a senha precisa casar antes de qualquer informação sobre clínicas ser devolvida:
     * 200 com o token, ou 200 {status: SELECAO_CLINICA} quando a mesma senha casa em mais de uma clínica.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequestDTO data, HttpServletRequest request) {
        return ResponseEntity.ok(loginService.login(data, request.getRemoteAddr()));
    }

    @PostMapping("/login/selecionar-clinica")
    public ResponseEntity<AuthResponseDTO> selecionarClinica(
            @RequestBody @Valid SelecionarClinicaRequestDTO data, HttpServletRequest request) {
        return ResponseEntity.ok(loginService.selecionarClinica(data.getTicket(), data.getClinicaId(), request.getRemoteAddr()));
    }

    /** Resposta sempre 200 e igual, exista ou não a conta (anti-enumeração). */
    @PostMapping("/recuperar-senha")
    public ResponseEntity<MensagemDTO> recuperarSenha(
            @RequestBody @Valid RecuperarSenhaDTO data, HttpServletRequest request) {
        return ResponseEntity.ok(recuperacaoSenhaService.solicitar(data, request.getRemoteAddr()));
    }

    @PostMapping("/validar-token")
    public ResponseEntity<ValidarTokenResponseDTO> validarToken(@RequestBody @Valid ValidarTokenRequestDTO data) {
        return ResponseEntity.ok(recuperacaoSenhaService.validarToken(data));
    }

    /** Troca o código de 6 dígitos do e-mail por um ticket de 5 min aceito em {@code /redefinir-senha}. */
    @PostMapping("/validar-codigo-recuperacao")
    public ResponseEntity<ValidarCodigoResponseDTO> validarCodigoRecuperacao(
            @RequestBody @Valid ValidarCodigoRecuperacaoDTO data, HttpServletRequest request) {
        return ResponseEntity.ok(recuperacaoSenhaService.validarCodigo(data, request.getRemoteAddr()));
    }

    @PostMapping("/redefinir-senha")
    public ResponseEntity<MensagemDTO> redefinirSenha(@RequestBody @Valid RedefinirSenhaDTO data) {
        return ResponseEntity.ok(recuperacaoSenhaService.redefinir(data));
    }

    @PostMapping("/confirmar-email")
    public ResponseEntity<ConfirmarEmailResponseDTO> confirmarEmail(@RequestBody @Valid ConfirmarEmailDTO data) {
        return ResponseEntity.ok(new ConfirmarEmailResponseDTO(confirmacaoEmailService.confirmar(data.getToken())));
    }

    @PostMapping("/reenviar-confirmacao")
    public ResponseEntity<MensagemDTO> reenviarConfirmacao(HttpServletRequest request) {
        return ResponseEntity.ok(confirmacaoEmailService.reenviar(request.getRemoteAddr()));
    }

    /**
     * Auto-cadastro de nova clínica. Cria a Clínica + primeiro User (PROPRIETARIO)
     * numa única chamada. Único endpoint público de escrita além do login.
     */
    @PostMapping("/register-clinica")
    public ResponseEntity<AuthResponseDTO> registerClinica(@RequestBody @Valid RegisterClinicaRequestDTO data,
                                                           HttpServletRequest request) {
        String codigo = data.getClinicaCodigo().toLowerCase();

        if (clinicaRepository.existsByCodigo(codigo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código de clínica já em uso.");
        }

        Clinica clinica = Clinica.builder()
                .codigo(codigo)
                .nome(data.getClinicaNome())
                .cnpj(data.getCnpj())
                .telefone(data.getTelefone())
                .emailContato(data.getEmailContato())
                .endereco(data.getEndereco())
                .ativo(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        FormatadorTexto.normalizar(clinica);
        Clinica salva = clinicaRepository.save(clinica);

        String encrypted = new BCryptPasswordEncoder().encode(data.getAdminPassword());
        User proprietario = User.builder()
                .name(data.getAdminNome())
                .email(FormatadorTexto.email(data.getAdminEmail()))
                .password(encrypted)
                .clinicaId(salva.getId())
                .role(RoleEnum.PROPRIETARIO)
                .ativo(true)
                .emailConfirmado(false)
                .statusConta(StatusContaEnum.ATIVO)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        User salvo = userRepository.save(proprietario);

        confirmacaoEmailService.enviarConfirmacao(salvo, salva, request.getRemoteAddr());

        String token = tokenService.generateToken(proprietario, salva.getCodigo(), salva.getNome());
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponseDTO.de(token, proprietario));
    }

    /**
     * Atualiza nome e/ou e-mail do próprio usuário logado.
     * Novo token é emitido para refletir claims atualizadas.
     */
    @PutMapping("/profile")
    public ResponseEntity<AuthResponseDTO> updateProfile(@RequestBody @Valid UpdateProfileDTO data,
                                                         HttpServletRequest request) {
        User currentUser = AuthContextHelper.currentUser();

        currentUser.setName(data.getName());

        String novoEmail = FormatadorTexto.email(data.getEmail());
        boolean emailMudou = false;
        if (novoEmail != null && !novoEmail.equals(currentUser.getEmail())) {
            emailMudou = !novoEmail.equalsIgnoreCase(currentUser.getEmail());
            if (emailMudou && userLookupService.emailEmUso(novoEmail, currentUser.getClinicaId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "E-mail já cadastrado nesta clínica.");
            }
            currentUser.setEmail(novoEmail);
        }

        boolean reconfirmar = emailMudou && currentUser.getRole() == RoleEnum.PROPRIETARIO;
        if (reconfirmar) {
            currentUser.setEmailConfirmado(false);
            currentUser.setEmailConfirmadoEm(null);
            authTokenService.invalidarPendentes(currentUser.getId(), TipoTokenAuthEnum.CONFIRMAR_EMAIL_DONO);
        }

        currentUser.setUpdatedAt(LocalDateTime.now());
        userRepository.save(currentUser);

        Clinica clinica = clinicaRepository.findById(currentUser.getClinicaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Clínica não encontrada."));

        if (reconfirmar) {
            confirmacaoEmailService.enviarConfirmacao(currentUser, clinica, request.getRemoteAddr());
        }

        String newToken = tokenService.generateToken(currentUser, clinica.getCodigo(), clinica.getNome());
        return ResponseEntity.ok(AuthResponseDTO.de(newToken, currentUser));
    }
}

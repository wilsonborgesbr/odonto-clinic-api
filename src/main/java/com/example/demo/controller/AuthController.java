package com.example.demo.controller;

import com.example.demo.dto.AuthResponseDTO;
import com.example.demo.dto.LoginRequestDTO;
import com.example.demo.dto.RegisterClinicaRequestDTO;
import com.example.demo.dto.UpdateProfileDTO;
import com.example.demo.enums.RoleEnum;
import com.example.demo.model.Clinica;
import com.example.demo.model.User;
import com.example.demo.repository.ClinicaRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.AuthContextHelper;
import com.example.demo.service.TokenService;
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
    private TokenService tokenService;

    /**
     * Login: (clinicaCodigo + email + senha).
     * O código da clínica identifica o tenant; o email é único dentro dela.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@RequestBody @Valid LoginRequestDTO data) {
        Clinica clinica = clinicaRepository.findByCodigo(data.getClinicaCodigo().toLowerCase())
                .filter(c -> Boolean.TRUE.equals(c.getAtivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Clínica não encontrada."));

        User user = userRepository.findByEmailAndClinicaId(data.getEmail(), clinica.getId())
                .filter(u -> Boolean.TRUE.equals(u.getAtivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas."));

        boolean matches = new BCryptPasswordEncoder().matches(data.getPassword(), user.getPassword());
        if (!matches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas.");
        }

        String token = tokenService.generateToken(user, clinica.getCodigo());
        return ResponseEntity.ok(new AuthResponseDTO(token));
    }

    /**
     * Auto-cadastro de nova clínica. Cria a Clínica + primeiro User (PROPRIETARIO)
     * numa única chamada. Único endpoint público de escrita além do login.
     */
    @PostMapping("/register-clinica")
    public ResponseEntity<AuthResponseDTO> registerClinica(@RequestBody @Valid RegisterClinicaRequestDTO data) {
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
        Clinica salva = clinicaRepository.save(clinica);

        String encrypted = new BCryptPasswordEncoder().encode(data.getAdminPassword());
        User proprietario = User.builder()
                .name(data.getAdminNome())
                .email(data.getAdminEmail())
                .password(encrypted)
                .clinicaId(salva.getId())
                .role(RoleEnum.PROPRIETARIO)
                .ativo(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userRepository.save(proprietario);

        String token = tokenService.generateToken(proprietario, salva.getCodigo());
        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponseDTO(token));
    }

    /**
     * Atualiza nome e/ou e-mail do próprio usuário logado.
     * Novo token é emitido para refletir claims atualizadas.
     */
    @PutMapping("/profile")
    public ResponseEntity<AuthResponseDTO> updateProfile(@RequestBody @Valid UpdateProfileDTO data) {
        User currentUser = AuthContextHelper.currentUser();

        currentUser.setName(data.getName());

        String novoEmail = data.getEmail();
        if (novoEmail != null && !novoEmail.isBlank()
                && !novoEmail.equalsIgnoreCase(currentUser.getEmail())) {
            boolean exists = userRepository.existsByEmailAndClinicaId(novoEmail, currentUser.getClinicaId());
            if (exists) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "E-mail já cadastrado nesta clínica.");
            }
            currentUser.setEmail(novoEmail);
        }

        currentUser.setUpdatedAt(LocalDateTime.now());
        userRepository.save(currentUser);

        Clinica clinica = clinicaRepository.findById(currentUser.getClinicaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Clínica não encontrada."));

        String newToken = tokenService.generateToken(currentUser, clinica.getCodigo());
        return ResponseEntity.ok(new AuthResponseDTO(newToken));
    }
}

package com.example.demo.controller;

import com.example.demo.dto.AuthResponseDTO;
import com.example.demo.dto.LoginRequestDTO;
import com.example.demo.dto.RegisterRequestDTO;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.TokenService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenService tokenService;

    @PostMapping("/login")
    public ResponseEntity login(@RequestBody @Valid LoginRequestDTO data) {
        // Cria um token provisório de usuário/senha com os dados da requisição
        var usernamePassword = new UsernamePasswordAuthenticationToken(data.getEmail(), data.getPassword());
        
        // Pede para o Spring Security autenticar (ele vai chamar o AuthorizationService internamente)
        var auth = this.authenticationManager.authenticate(usernamePassword);

        // Se passar da linha acima, a senha estava correta. Geramos o JWT!
        var token = tokenService.generateToken((User) auth.getPrincipal());

        // Devolvemos o token para o cliente
        return ResponseEntity.ok(new AuthResponseDTO(token));
    }

    @PostMapping("/register")
    public ResponseEntity register(@RequestBody @Valid RegisterRequestDTO data) {
        // Verifica se o email já existe no banco
        if (this.userRepository.findByEmail(data.getEmail()) != null) {
            return ResponseEntity.badRequest().body("Erro: Email já cadastrado.");
        }

        // Hasheia a senha usando BCrypt antes de salvar
        String encryptedPassword = new BCryptPasswordEncoder().encode(data.getPassword());
        
        // Cria o novo usuário
        User newUser = User.builder()
                .name(data.getName())
                .email(data.getEmail())
                .password(encryptedPassword)
                .build();

        // Salva no MongoDB
        this.userRepository.save(newUser);

        return ResponseEntity.ok().build();
    }
}

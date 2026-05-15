package com.example.demo.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.example.demo.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class TokenService {

    // Lê a variável de ambiente ou usa um valor padrão (não faça isso em produção!)
    @Value("${api.security.token.secret:meu-segredo-super-secreto-123}")
    private String secret;

    public String generateToken(User user) {
        try {
            // Algoritmo de criptografia do Token
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.create()
                    .withIssuer("auth-api") // Quem emitiu o token
                    .withSubject(user.getEmail()) // O assunto (quem é o dono do token)
                    .withExpiresAt(genExpirationDate()) // Tempo de expiração
                    .sign(algorithm); // Assina com nossa chave secreta
        } catch (JWTCreationException exception) {
            throw new RuntimeException("Erro ao gerar token", exception);
        }
    }

    public String validateToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.require(algorithm)
                    .withIssuer("auth-api")
                    .build()
                    .verify(token)
                    .getSubject(); // Retorna o email se o token for válido
        } catch (JWTVerificationException exception) {
            return ""; // Se der erro (token inválido ou expirado), retorna vazio
        }
    }

    private Instant genExpirationDate() {
        // O token expira em 2 horas a partir de agora
        return LocalDateTime.now().plusHours(2).toInstant(ZoneOffset.of("-03:00"));
    }
}

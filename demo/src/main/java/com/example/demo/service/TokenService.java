package com.example.demo.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.demo.enums.PermissaoEnum;
import com.example.demo.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TokenService {

    @Value("${api.security.token.secret}")
    private String secret;

    private static final String ISSUER = "auth-api";

    /**
     * Gera JWT com todas as claims necessárias pro RBAC:
     * - sub (email), name
     * - clinicaId, clinicaCodigo (identifica o tenant)
     * - role
     * - permissoes (array — o frontend usa direto pra montar sidebar)
     */
    public String generateToken(User user, String clinicaCodigo) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            List<String> perms = user.permissoesEfetivas().stream()
                    .map(PermissaoEnum::name)
                    .collect(Collectors.toList());

            return JWT.create()
                    .withIssuer(ISSUER)
                    .withSubject(user.getEmail())
                    .withClaim("name", user.getName())
                    .withClaim("userId", user.getId())
                    .withClaim("clinicaId", user.getClinicaId())
                    .withClaim("clinicaCodigo", clinicaCodigo)
                    .withClaim("role", user.getRole() != null ? user.getRole().name() : null)
                    .withClaim("permissoes", perms)
                    .withExpiresAt(genExpirationDate())
                    .sign(algorithm);
        } catch (JWTCreationException exception) {
            throw new RuntimeException("Erro ao gerar token", exception);
        }
    }

    /** Retorna o subject (email) se o token for válido, senão string vazia. */
    public String validateToken(String token) {
        try {
            return decode(token).getSubject();
        } catch (JWTVerificationException exception) {
            return "";
        }
    }

    /** Decodifica o token retornando o objeto completo (para leitura de claims). */
    public DecodedJWT decode(String token) {
        Algorithm algorithm = Algorithm.HMAC256(secret);
        return JWT.require(algorithm).withIssuer(ISSUER).build().verify(token);
    }

    private Instant genExpirationDate() {
        return LocalDateTime.now().plusHours(24).toInstant(ZoneOffset.of("-03:00"));
    }
}

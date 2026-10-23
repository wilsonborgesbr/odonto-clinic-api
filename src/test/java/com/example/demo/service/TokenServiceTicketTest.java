package com.example.demo.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.example.demo.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTicketTest {

    private static final String SEGREDO = "segredo-so-de-teste";

    private TokenService servico() {
        TokenService s = new TokenService();
        ReflectionTestUtils.setField(s, "secret", SEGREDO);
        return s;
    }

    @Test
    void ticket_IdaEVoltaPreservaOsIdsDeUsuario() {
        TokenService s = servico();

        String ticket = s.generateTicketSelecaoClinica(List.of("u1", "u2"));

        assertEquals(List.of("u1", "u2"), s.validarTicketSelecaoClinica(ticket));
    }

    @Test
    void ticket_NaoServeComoTokenDeAcesso() {
        TokenService s = servico();
        String ticket = s.generateTicketSelecaoClinica(List.of("u1"));

        assertThrows(JWTVerificationException.class, () -> s.decode(ticket));
        assertEquals("", s.validateToken(ticket));
    }

    @Test
    void tokenDeAcesso_NaoServeComoTicket() {
        TokenService s = servico();
        String acesso = s.generateToken(User.builder().id("u1").clinicaId("c1").email("a@x.com").name("A").build(), "tainah", "Tainah");

        assertThrows(JWTVerificationException.class, () -> s.validarTicketSelecaoClinica(acesso));
    }

    @Test
    void ticket_ExpiradoEAdulteradoSaoRejeitados() {
        TokenService s = servico();
        String expirado = JWT.create().withIssuer("auth-api-selecao-clinica").withClaim("userIds", List.of("u1"))
                .withExpiresAt(Instant.now().minusSeconds(1)).sign(Algorithm.HMAC256(SEGREDO));
        String outraAssinatura = JWT.create().withIssuer("auth-api-selecao-clinica").withClaim("userIds", List.of("u1"))
                .withExpiresAt(Instant.now().plusSeconds(60)).sign(Algorithm.HMAC256("outro-segredo"));

        assertThrows(JWTVerificationException.class, () -> s.validarTicketSelecaoClinica(expirado));
        assertThrows(JWTVerificationException.class, () -> s.validarTicketSelecaoClinica(outraAssinatura));
        assertThrows(JWTVerificationException.class, () -> s.validarTicketSelecaoClinica("lixo"));
    }

    @Test
    void ticket_ValeCincoMinutos() {
        TokenService s = servico();
        String ticket = s.generateTicketSelecaoClinica(List.of("u1"));

        Instant expira = JWT.decode(ticket).getExpiresAtAsInstant();

        long segundos = expira.getEpochSecond() - Instant.now().getEpochSecond();
        assertTrue(segundos > 290 && segundos <= 300, "esperava ~300s, veio " + segundos);
    }
}

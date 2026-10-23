package com.example.demo.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.demo.enums.RoleEnum;
import com.example.demo.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceClaimsTest {

    private static final String SEGREDO = "segredo-so-de-teste";

    private TokenService servico() {
        TokenService s = new TokenService();
        ReflectionTestUtils.setField(s, "secret", SEGREDO);
        return s;
    }

    private static User usuario() {
        return User.builder().id("u1").clinicaId("c1").email("a@x.com").name("Ana").password("hash-bcrypt-secreto")
                .role(RoleEnum.PROPRIETARIO).build();
    }

    @Test
    void jwtDeAcessoTrazOCodigoEONomeDaClinica() {
        DecodedJWT jwt = JWT.decode(servico().generateToken(usuario(), "tainah", "Clínica Odontológica Tainah"));

        assertEquals("tainah", jwt.getClaim("clinicaCodigo").asString());
        assertEquals("Clínica Odontológica Tainah", jwt.getClaim("clinicaNome").asString());
        assertEquals("c1", jwt.getClaim("clinicaId").asString());
    }

    @Test
    void nomeComAcentoEEspacosViajaIntactoEOTokenContinuaValido() {
        TokenService s = servico();
        String token = s.generateToken(usuario(), "x", "Sorriso & Saúde — Dra. Ação");

        assertEquals("Sorriso & Saúde — Dra. Ação", JWT.decode(token).getClaim("clinicaNome").asString());
        assertEquals("a@x.com", s.validateToken(token));
    }

    @Test
    void clinicaSemNomeNaoQuebraAEmissao() {
        String token = servico().generateToken(usuario(), "tainah", null);

        assertTrue(JWT.decode(token).getClaim("clinicaNome").isNull() || JWT.decode(token).getClaim("clinicaNome").isMissing());
        assertEquals("tainah", JWT.decode(token).getClaim("clinicaCodigo").asString());
    }

    @Test
    void conjuntoDeClaimsNaoTemNadaSensivel_SoIdentificacaoPermissaoEExibicao() {
        DecodedJWT jwt = JWT.decode(servico().generateToken(usuario(), "tainah", "Tainah"));

        Set<String> claims = new TreeSet<>(jwt.getClaims().keySet());
        assertEquals(new TreeSet<>(Set.of("iss", "sub", "exp", "name", "userId", "clinicaId", "clinicaCodigo", "clinicaNome",
                "role", "permissoes")), claims);
        assertFalse(jwt.getToken().isEmpty());
        assertFalse(new String(java.util.Base64.getUrlDecoder().decode(jwt.getPayload())).contains("hash-bcrypt-secreto"));
    }
}

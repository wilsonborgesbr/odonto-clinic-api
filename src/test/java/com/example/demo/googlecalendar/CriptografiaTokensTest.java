package com.example.demo.googlecalendar;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CriptografiaTokensTest {

    private static byte[] chave() {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        return k;
    }

    private static GoogleCalendarProperties props(String chaveBase64) {
        return new GoogleCalendarProperties("id", "segredo", "http://localhost/cb", "http://localhost:5173", "/configuracoes",
                "", chaveBase64, java.time.Duration.ofMinutes(5), java.time.Duration.ofHours(48),
                java.time.Duration.ofDays(6), 3, java.time.Duration.ZERO);
    }

    @Test
    void cifraEDecifra_IdaEVolta() {
        CriptografiaTokens c = new CriptografiaTokens(chave());
        String ctx = CriptografiaTokens.contexto("clinica1", "user1", "access_token");

        String cifrado = c.cifrar("ya29.token-secreto", ctx);

        assertNotEquals("ya29.token-secreto", cifrado);
        assertFalse(cifrado.contains("ya29"), "o texto cifrado não pode carregar o token em claro");
        assertTrue(cifrado.startsWith("v1."));
        assertEquals("ya29.token-secreto", c.decifrar(cifrado, ctx));
    }

    @Test
    void mesmoTextoCifradoDuasVezes_DaResultadosDiferentes() {
        CriptografiaTokens c = new CriptografiaTokens(chave());

        assertNotEquals(c.cifrar("igual", "ctx"), c.cifrar("igual", "ctx"), "IV precisa ser aleatório a cada cifragem");
    }

    @Test
    void textoCifradoCopiadoParaOutroRegistroOuCampo_NaoDecifra() {
        CriptografiaTokens c = new CriptografiaTokens(chave());
        String cifrado = c.cifrar("token", CriptografiaTokens.contexto("clinica1", "user1", "access_token"));

        assertThrows(IllegalStateException.class,
                () -> c.decifrar(cifrado, CriptografiaTokens.contexto("clinica1", "user2", "access_token")));
        assertThrows(IllegalStateException.class,
                () -> c.decifrar(cifrado, CriptografiaTokens.contexto("clinica2", "user1", "access_token")));
        assertThrows(IllegalStateException.class,
                () -> c.decifrar(cifrado, CriptografiaTokens.contexto("clinica1", "user1", "refresh_token")));
    }

    @Test
    void textoAdulteradoOuComOutraChave_NaoDecifra() {
        CriptografiaTokens c = new CriptografiaTokens(chave());
        String cifrado = c.cifrar("token", "ctx");
        char ultimo = cifrado.charAt(cifrado.length() - 2);
        String adulterado = cifrado.substring(0, cifrado.length() - 2) + (ultimo == 'A' ? 'B' : 'A') + cifrado.charAt(cifrado.length() - 1);

        assertThrows(IllegalStateException.class, () -> c.decifrar(adulterado, "ctx"));
        assertThrows(IllegalStateException.class, () -> new CriptografiaTokens(chave()).decifrar(cifrado, "ctx"));
        assertThrows(IllegalStateException.class, () -> c.decifrar("sem-prefixo", "ctx"));
        assertThrows(IllegalStateException.class, () -> c.decifrar("v1.AAAA", "ctx"));
    }

    @Test
    void nuloContinuaNulo() {
        CriptografiaTokens c = new CriptografiaTokens(chave());

        assertNull(c.cifrar(null, "ctx"));
        assertNull(c.decifrar(null, "ctx"));
    }

    @Test
    void chaveDoAmbiente_PrecisaTer32BytesEmBase64() {
        String valida = Base64.getEncoder().encodeToString(chave());
        CriptografiaTokens c = new CriptografiaTokens(props(valida), "jwt");
        assertEquals("x", c.decifrar(c.cifrar("x", "ctx"), "ctx"));

        assertThrows(IllegalStateException.class,
                () -> new CriptografiaTokens(props(Base64.getEncoder().encodeToString(new byte[16])), "jwt"));
        assertThrows(IllegalStateException.class, () -> new CriptografiaTokens(props("isto não é base64!!"), "jwt"));
    }

    @Test
    void semChaveDedicada_DerivaDoSegredoDoJwt_DeFormaEstavelEDependenteDoSegredo() {
        CriptografiaTokens a1 = new CriptografiaTokens(props(""), "segredo-jwt-A");
        CriptografiaTokens a2 = new CriptografiaTokens(props(""), "segredo-jwt-A");
        CriptografiaTokens b = new CriptografiaTokens(props(""), "segredo-jwt-B");
        String cifrado = a1.cifrar("token", "ctx");

        assertEquals("token", a2.decifrar(cifrado, "ctx"), "mesma origem, mesma chave");
        assertThrows(IllegalStateException.class, () -> b.decifrar(cifrado, "ctx"));
    }
}

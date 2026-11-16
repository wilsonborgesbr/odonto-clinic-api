package com.example.demo.googlecalendar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM para os tokens do Google guardados no banco. A chave vem do ambiente
 * ({@code GOOGLE_CALENDAR_ENCRYPTION_KEY}, 32 bytes em Base64); nunca é gravada no banco.
 *
 * <p>Sem a variável, a chave é derivada do segredo do JWT por HMAC com um rótulo próprio (conveniência de
 * desenvolvimento, com aviso no log). Trocar o segredo do JWT invalida os tokens guardados — em produção,
 * definir a chave dedicada.
 *
 * <p>O texto cifrado leva "v1." + Base64URL(IV de 12 bytes + cifra + tag). Os dados autenticados (AAD)
 * amarram o valor à clínica, ao usuário e ao campo, então um texto cifrado copiado para outro registro
 * não decifra.
 */
@Component
public class CriptografiaTokens {

    private static final Logger log = LoggerFactory.getLogger(CriptografiaTokens.class);
    private static final String PREFIXO = "v1.";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String ROTULO_DERIVACAO = "bokka-google-calendar-token-encryption-v1";

    private final SecretKeySpec chave;
    private final SecureRandom aleatorio = new SecureRandom();

    @Autowired
    public CriptografiaTokens(GoogleCalendarProperties props,
                              @Value("${api.security.token.secret}") String segredoJwt) {
        this.chave = props.chaveCriptografia().isEmpty()
                ? derivar(segredoJwt)
                : decodificar(props.chaveCriptografia());
    }

    CriptografiaTokens(byte[] chave32) {
        this.chave = new SecretKeySpec(chave32, "AES");
    }

    private static SecretKeySpec decodificar(String base64) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("google.calendar.encryption-key não é Base64 válido.");
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("google.calendar.encryption-key deve ter 32 bytes (AES-256) em Base64.");
        }
        return new SecretKeySpec(bytes, "AES");
    }

    private static SecretKeySpec derivar(String segredoJwt) {
        log.warn("GOOGLE_CALENDAR_ENCRYPTION_KEY ausente: chave dos tokens do Google derivada do segredo do JWT. "
                + "Defina a chave dedicada em produção.");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredoJwt.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return new SecretKeySpec(mac.doFinal(ROTULO_DERIVACAO.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Não foi possível derivar a chave de criptografia.", e);
        }
    }

    public String cifrar(String texto, String contexto) {
        if (texto == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            aleatorio.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(contexto.getBytes(StandardCharsets.UTF_8));
            byte[] cifra = cipher.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            byte[] saida = new byte[IV_BYTES + cifra.length];
            System.arraycopy(iv, 0, saida, 0, IV_BYTES);
            System.arraycopy(cifra, 0, saida, IV_BYTES, cifra.length);
            return PREFIXO + Base64.getUrlEncoder().withoutPadding().encodeToString(saida);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar token.", e);
        }
    }

    /** Lança {@link IllegalStateException} se o valor foi adulterado, veio de outro contexto ou de outra chave. */
    public String decifrar(String cifrado, String contexto) {
        if (cifrado == null) {
            return null;
        }
        if (!cifrado.startsWith(PREFIXO)) {
            throw new IllegalStateException("Formato de token cifrado desconhecido.");
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(cifrado.substring(PREFIXO.length()));
            if (bytes.length <= IV_BYTES) {
                throw new IllegalStateException("Token cifrado inválido.");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES));
            cipher.updateAAD(contexto.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(bytes, IV_BYTES, bytes.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Não foi possível decifrar o token.");
        }
    }

    /** Contexto autenticado: clínica, usuário e campo. */
    public static String contexto(String clinicaId, String userId, String campo) {
        return clinicaId + "|" + userId + "|" + campo;
    }
}

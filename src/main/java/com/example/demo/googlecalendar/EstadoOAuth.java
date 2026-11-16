package com.example.demo.googlecalendar;

import com.example.demo.enums.EscopoGoogleCalendarEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Parâmetro {@code state} do OAuth: assinado por HMAC-SHA256 contra CSRF e contra adulteração. Carrega quem
 * iniciou o fluxo, a clínica, o escopo e a validade (10 min). O callback do Google chega sem JWT,
 * então é este valor que diz de quem é a conexão.
 *
 * <p>Formato: Base64URL(userId|clinicaId|escopo|nonce|expiraEpochSeg) + "." + Base64URL(HMAC).
 */
@Component
public class EstadoOAuth {

    static final Duration VALIDADE = Duration.ofMinutes(10);
    private static final String ROTULO_CHAVE = "bokka-google-calendar-oauth-state-v1";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    public record Dados(String userId, String clinicaId, EscopoGoogleCalendarEnum escopo) {}

    private final byte[] chave;
    private final Clock relogio;
    private final SecureRandom aleatorio = new SecureRandom();

    @Autowired
    public EstadoOAuth(@Value("${api.security.token.secret}") String segredoJwt) {
        this(segredoJwt, Clock.systemUTC());
    }

    EstadoOAuth(String segredoJwt, Clock relogio) {
        this.relogio = relogio;
        this.chave = hmac(segredoJwt.getBytes(StandardCharsets.UTF_8), ROTULO_CHAVE.getBytes(StandardCharsets.UTF_8));
    }

    public String gerar(String userId, String clinicaId, EscopoGoogleCalendarEnum escopo) {
        byte[] nonce = new byte[12];
        aleatorio.nextBytes(nonce);
        long expira = relogio.instant().plus(VALIDADE).getEpochSecond();
        String payload = userId + "|" + clinicaId + "|" + escopo.name() + "|" + B64.encodeToString(nonce) + "|" + expira;
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        return B64.encodeToString(bytes) + "." + B64.encodeToString(hmac(chave, bytes));
    }

    /** Vazio se a assinatura não confere, se venceu ou se o formato é inválido. */
    public Optional<Dados> validar(String state) {
        if (state == null || state.length() > 1024) {
            return Optional.empty();
        }
        int ponto = state.indexOf('.');
        if (ponto <= 0 || ponto == state.length() - 1) {
            return Optional.empty();
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(state.substring(0, ponto));
            byte[] assinatura = Base64.getUrlDecoder().decode(state.substring(ponto + 1));
            if (!MessageDigest.isEqual(assinatura, hmac(chave, payload))) {
                return Optional.empty();
            }
            String[] partes = new String(payload, StandardCharsets.UTF_8).split("\\|", -1);
            if (partes.length != 5) {
                return Optional.empty();
            }
            if (relogio.instant().getEpochSecond() > Long.parseLong(partes[4])) {
                return Optional.empty();
            }
            return Optional.of(new Dados(partes[0], partes[1], EscopoGoogleCalendarEnum.valueOf(partes[2])));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static byte[] hmac(byte[] chave, byte[] dados) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(chave, "HmacSHA256"));
            return mac.doFinal(dados);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC indisponível.", e);
        }
    }
}

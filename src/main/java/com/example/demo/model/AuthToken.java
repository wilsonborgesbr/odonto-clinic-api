package com.example.demo.model;

import com.example.demo.enums.TipoTokenAuthEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Token de uso único (reset de senha, confirmação de e-mail, ativação). Só o hash SHA-256 é gravado.
 * O token de reset pode carregar também um código de 6 dígitos, do qual só o HMAC com sal é gravado.
 *
 * <p>O projeto não cria índices automaticamente: o único em {@code tokenHash} e o TTL em
 * {@code expiraEm} são criados por {@code scripts/indices-auth-tokens.js}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "auth_tokens")
public class AuthToken {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String clinicaId;

    @Indexed(unique = true)
    private String tokenHash;

    private TipoTokenAuthEnum tipo;

    @Indexed(expireAfterSeconds = 0)
    private Instant expiraEm;

    private Instant usadoEm;

    private Instant criadoEm;

    private String ipSolicitacao;

    /** HMAC-SHA256 (chave do servidor, sal + código). Nulo se o token não tem código numérico. */
    private String codigoHash;

    /** Sal aleatório de 16 bytes, em hexadecimal. */
    private String codigoSalt;

    private Instant codigoExpiraEm;

    /** Palpites errados do código; incrementado de forma atômica com {@code $inc}. */
    private int codigoTentativas;

    private Boolean codigoBloqueado;
}

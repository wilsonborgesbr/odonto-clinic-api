package com.example.demo.dto;

import com.example.demo.model.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponseDTO implements LoginResponse {

    private String token;

    /** Legado sem o campo vem como true. */
    private boolean emailConfirmado = true;

    public AuthResponseDTO(String token) {
        this.token = token;
    }

    public static AuthResponseDTO de(String token, User user) {
        return new AuthResponseDTO(token, user.emailEstaConfirmado());
    }
}

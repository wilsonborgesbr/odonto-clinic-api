package com.example.demo.service;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.util.FormatadorTexto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Busca de usuário por e-mail tolerante a dados legados: o e-mail agora é gravado em minúsculo,
 * mas contas antigas ainda podem ter maiúsculas até a migração rodar.
 */
@Service
public class UserLookupService {

    @Autowired
    private UserRepository userRepository;

    public Optional<User> buscarPorEmail(String email, String clinicaId) {
        String normalizado = FormatadorTexto.email(email);
        if (normalizado == null) {
            return Optional.empty();
        }

        Optional<User> encontrado = userRepository.findByEmailAndClinicaId(normalizado, clinicaId);
        if (encontrado.isPresent()) {
            return encontrado;
        }

        if (!email.equals(normalizado)) {
            encontrado = userRepository.findByEmailAndClinicaId(email, clinicaId);
            if (encontrado.isPresent()) {
                return encontrado;
            }
        }

        List<User> candidatos = userRepository.findByClinicaIdAndEmailRegexIgnoreCase(
                clinicaId, regexExato(normalizado));
        return candidatos.size() == 1 ? Optional.of(candidatos.get(0)) : Optional.empty();
    }

    /** Todas as contas (de qualquer clínica) com este e-mail; tolera legado com maiúsculas. */
    public List<User> buscarContasPorEmail(String email) {
        String normalizado = FormatadorTexto.email(email);
        if (normalizado == null) {
            return List.of();
        }
        List<User> exatas = userRepository.findByEmail(normalizado);
        if (!exatas.isEmpty()) {
            return exatas;
        }
        return userRepository.findByEmailRegexIgnoreCase(regexExato(normalizado));
    }

    public boolean emailEmUso(String email, String clinicaId) {
        String normalizado = FormatadorTexto.email(email);
        if (normalizado == null) {
            return false;
        }
        return userRepository.existsByEmailAndClinicaId(normalizado, clinicaId)
                || !userRepository.findByClinicaIdAndEmailRegexIgnoreCase(clinicaId, regexExato(normalizado)).isEmpty();
    }

    private String regexExato(String texto) {
        StringBuilder regex = new StringBuilder("^");
        for (char c : texto.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                regex.append(c);
            } else {
                regex.append('\\').append(c);
            }
        }
        return regex.append('$').toString();
    }
}

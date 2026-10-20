package com.example.demo.service;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserLookupServiceTest {

    private static final String CLINICA = "clinica-1";

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserLookupService lookup;

    @Test
    void buscarPorEmail_TentaPrimeiroMinusculoETrim() {
        User user = User.builder().email("ana@x.com").build();
        when(userRepository.findByEmailAndClinicaId("ana@x.com", CLINICA)).thenReturn(Optional.of(user));

        assertSame(user, lookup.buscarPorEmail("  Ana@X.com ", CLINICA).orElseThrow());

        verify(userRepository, times(1)).findByEmailAndClinicaId(anyString(), any());
        verify(userRepository, never()).findByClinicaIdAndEmailRegexIgnoreCase(any(), any());
    }

    @Test
    void buscarPorEmail_SeNaoAchaMinusculoTentaOValorOriginalDigitado() {
        User legado = User.builder().email("Ana@X.com").build();
        when(userRepository.findByEmailAndClinicaId("ana@x.com", CLINICA)).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndClinicaId("Ana@X.com", CLINICA)).thenReturn(Optional.of(legado));

        assertSame(legado, lookup.buscarPorEmail("Ana@X.com", CLINICA).orElseThrow());

        verify(userRepository, never()).findByClinicaIdAndEmailRegexIgnoreCase(any(), any());
    }

    @Test
    void buscarPorEmail_LegadoComCaixaDiferenteDaDigitadaAchaPorBuscaSemCaixaQuandoUnico() {
        User legado = User.builder().email("ANA@X.COM").build();
        when(userRepository.findByEmailAndClinicaId(anyString(), any())).thenReturn(Optional.empty());
        when(userRepository.findByClinicaIdAndEmailRegexIgnoreCase(eq(CLINICA), anyString())).thenReturn(List.of(legado));

        assertSame(legado, lookup.buscarPorEmail("ana@x.com", CLINICA).orElseThrow());
    }

    @Test
    void buscarPorEmail_AmbiguidadeLegadaNaoAutenticaNinguem() {
        when(userRepository.findByEmailAndClinicaId(anyString(), any())).thenReturn(Optional.empty());
        when(userRepository.findByClinicaIdAndEmailRegexIgnoreCase(eq(CLINICA), anyString()))
                .thenReturn(List.of(User.builder().email("ANA@x.com").build(), User.builder().email("Ana@x.com").build()));

        assertTrue(lookup.buscarPorEmail("ana@x.com", CLINICA).isEmpty());
    }

    @Test
    void buscarPorEmail_InexistenteOuVazio() {
        when(userRepository.findByEmailAndClinicaId(anyString(), any())).thenReturn(Optional.empty());
        when(userRepository.findByClinicaIdAndEmailRegexIgnoreCase(any(), any())).thenReturn(List.of());

        assertTrue(lookup.buscarPorEmail("ninguem@x.com", CLINICA).isEmpty());
        assertTrue(lookup.buscarPorEmail(null, CLINICA).isEmpty());
        assertTrue(lookup.buscarPorEmail("   ", CLINICA).isEmpty());
    }

    @Test
    void buscarPorEmail_RegexDaBuscaLegadaEAncoradoEEscapado() {
        when(userRepository.findByEmailAndClinicaId(anyString(), any())).thenReturn(Optional.empty());
        when(userRepository.findByClinicaIdAndEmailRegexIgnoreCase(any(), any())).thenReturn(List.of());

        lookup.buscarPorEmail("a.b+c@x.com", CLINICA);

        ArgumentCaptor<String> regex = ArgumentCaptor.forClass(String.class);
        verify(userRepository).findByClinicaIdAndEmailRegexIgnoreCase(eq(CLINICA), regex.capture());
        Pattern p = Pattern.compile(regex.getValue(), Pattern.CASE_INSENSITIVE);
        assertTrue(p.matcher("A.B+C@X.COM").matches());
        assertFalse(p.matcher("aXb+c@x.com").matches());
        assertFalse(p.matcher("zz a.b+c@x.com").find());
    }

    @Test
    void emailEmUso_ConsideraDuplicidadeLegadaComOutraCaixa() {
        when(userRepository.existsByEmailAndClinicaId("ana@x.com", CLINICA)).thenReturn(false);
        when(userRepository.findByClinicaIdAndEmailRegexIgnoreCase(eq(CLINICA), anyString()))
                .thenReturn(List.of(User.builder().email("Ana@X.com").build()));

        assertTrue(lookup.emailEmUso(" ANA@x.com ", CLINICA));
    }

    @Test
    void emailEmUso_LivreOuNulo() {
        when(userRepository.existsByEmailAndClinicaId("novo@x.com", CLINICA)).thenReturn(false);
        when(userRepository.findByClinicaIdAndEmailRegexIgnoreCase(eq(CLINICA), anyString())).thenReturn(List.of());

        assertFalse(lookup.emailEmUso("novo@x.com", CLINICA));
        assertFalse(lookup.emailEmUso(null, CLINICA));
    }

    @Test
    void emailEmUso_MinusculoExistenteCurtoCircuita() {
        when(userRepository.existsByEmailAndClinicaId("ana@x.com", CLINICA)).thenReturn(true);

        assertTrue(lookup.emailEmUso("Ana@x.com", CLINICA));
        verify(userRepository, never()).findByClinicaIdAndEmailRegexIgnoreCase(any(), any());
    }
}

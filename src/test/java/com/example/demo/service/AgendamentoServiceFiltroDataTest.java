package com.example.demo.service;

import com.example.demo.exception.CampoInvalidoException;
import com.example.demo.model.Agendamento;
import com.example.demo.repository.AgendamentoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static com.example.demo.service.TenantTestSupport.TEST_CLINICA_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgendamentoServiceFiltroDataTest {

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @InjectMocks
    private AgendamentoService service;

    private final Pageable pagina = PageRequest.of(0, 300);
    private final Page<Agendamento> resposta = new PageImpl<>(List.of(new Agendamento()));

    @BeforeEach
    void setUp() {
        TenantTestSupport.loginTestUser();
    }

    @AfterEach
    void tearDown() {
        TenantTestSupport.logout();
    }

    @Test
    void semFiltros_ContinuaIgual() {
        when(agendamentoRepository.findByClinicaId(TEST_CLINICA_ID, pagina)).thenReturn(resposta);

        assertSame(resposta, service.listarTodos(pagina));
        assertSame(resposta, service.listarTodos(pagina, null, null));
        assertSame(resposta, service.listarTodos(pagina, "", "  "));

        verify(agendamentoRepository, times(3)).findByClinicaId(TEST_CLINICA_ID, pagina);
        verifyNoMoreInteractions(agendamentoRepository);
    }

    @Test
    void deEAte_SaoInclusivos_DiaDeAteEntraInteiro() {
        when(agendamentoRepository.findPorPeriodo(TEST_CLINICA_ID,
                LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 11, 1, 0, 0), pagina)).thenReturn(resposta);

        assertSame(resposta, service.listarTodos(pagina, "2026-10-01", "2026-10-31"));
    }

    @Test
    void mesmoDiaNosDoisLados_ViraUmDiaInteiro() {
        when(agendamentoRepository.findPorPeriodo(TEST_CLINICA_ID,
                LocalDateTime.of(2026, 10, 23, 0, 0), LocalDateTime.of(2026, 10, 24, 0, 0), pagina)).thenReturn(resposta);

        assertSame(resposta, service.listarTodos(pagina, "2026-10-23", "2026-10-23"));
    }

    @Test
    void ateNoUltimoDiaDoAno_FimExclusivoViraOPrimeiroDiaDoAnoSeguinte() {
        when(agendamentoRepository.findPorPeriodo(TEST_CLINICA_ID,
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2027, 1, 1, 0, 0), pagina)).thenReturn(resposta);

        assertSame(resposta, service.listarTodos(pagina, "2026-01-01", "2026-12-31"));
    }

    @Test
    void ateEmAnoBissexto_29DeFevereiroExiste() {
        when(agendamentoRepository.findPorPeriodo(TEST_CLINICA_ID,
                LocalDateTime.of(2028, 2, 1, 0, 0), LocalDateTime.of(2028, 3, 1, 0, 0), pagina)).thenReturn(resposta);

        assertSame(resposta, service.listarTodos(pagina, "2028-02-01", "2028-02-29"));
    }

    @Test
    void soDe_BuscaAPartirDoInicioDoDia() {
        when(agendamentoRepository.findAPartirDe(TEST_CLINICA_ID, LocalDateTime.of(2026, 10, 23, 0, 0), pagina))
                .thenReturn(resposta);

        assertSame(resposta, service.listarTodos(pagina, "2026-10-23", null));
    }

    @Test
    void soAte_BuscaAteOFimDoDia() {
        when(agendamentoRepository.findAte(TEST_CLINICA_ID, LocalDateTime.of(2026, 10, 24, 0, 0), pagina))
                .thenReturn(resposta);

        assertSame(resposta, service.listarTodos(pagina, "", "2026-10-23"));
    }

    @Test
    void espacosAoRedorSaoIgnorados() {
        when(agendamentoRepository.findPorPeriodo(TEST_CLINICA_ID,
                LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 11, 1, 0, 0), pagina)).thenReturn(resposta);

        assertSame(resposta, service.listarTodos(pagina, " 2026-10-01 ", " 2026-10-31 "));
    }

    @Test
    void formatoInvalido_400PorCampo() {
        for (String ruim : new String[]{"23/10/2026", "2026-10-1", "2026-13-01", "2026-02-30", "abc", "2026-10-23T10:00:00",
                "+99999-01-01", "20261023"}) {
            CampoInvalidoException de = assertThrows(CampoInvalidoException.class,
                    () -> service.listarTodos(pagina, ruim, null), ruim);
            assertEquals("de", de.getCampo());
            assertEquals("Data inicial inválida. Use o formato AAAA-MM-DD.", de.getReason());

            CampoInvalidoException ate = assertThrows(CampoInvalidoException.class,
                    () -> service.listarTodos(pagina, null, ruim), ruim);
            assertEquals("ate", ate.getCampo());
            assertEquals("Data final inválida. Use o formato AAAA-MM-DD.", ate.getReason());
        }
        verifyNoInteractions(agendamentoRepository);
    }

    @Test
    void ateAntesDeDe_400NoCampoAte() {
        CampoInvalidoException e = assertThrows(CampoInvalidoException.class,
                () -> service.listarTodos(pagina, "2026-10-24", "2026-10-23"));

        assertEquals("ate", e.getCampo());
        assertEquals("A data final não pode ser anterior à data inicial.", e.getReason());
        verifyNoInteractions(agendamentoRepository);
    }
}

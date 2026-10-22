package com.example.demo.controller;

import com.example.demo.exception.CampoInvalidoException;
import com.example.demo.exception.GlobalExceptionHandler;
import com.example.demo.model.Agendamento;
import com.example.demo.service.AgendamentoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AgendamentoControllerFiltroDataTest {

    @Mock
    private AgendamentoService agendamentoService;

    @InjectMocks
    private AgendamentoController controller;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        PageableHandlerMethodArgumentResolver resolver = new PageableHandlerMethodArgumentResolver();
        resolver.setPageParameterName("pagina");
        resolver.setSizeParameterName("tamanho");
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(resolver)
                .build();
    }

    @Test
    void semParametrosDeData_ChamaComNulosEOrdenacaoPadraoEstavel() throws Exception {
        Page<Agendamento> vazia = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(agendamentoService.listarTodos(any(Pageable.class), any(), any())).thenReturn(vazia);

        var resposta = mvc.perform(get("/api/agendamentos")).andReturn().getResponse();
        assertEquals(200, resposta.getStatus(), resposta.getContentAsString());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(agendamentoService).listarTodos(pageable.capture(), eq(null), eq(null));
        assertEquals(20, pageable.getValue().getPageSize());
        assertEquals("dataHoraInicio: ASC,id: ASC", pageable.getValue().getSort().toString());
    }

    @Test
    void comDeEAte_RepassaOsTextosEMantemPaginacao() throws Exception {
        when(agendamentoService.listarTodos(any(Pageable.class), any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc.perform(get("/api/agendamentos")
                        .param("de", "2026-10-01").param("ate", "2026-10-31")
                        .param("pagina", "2").param("tamanho", "50"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(agendamentoService).listarTodos(pageable.capture(), eq("2026-10-01"), eq("2026-10-31"));
        assertEquals(2, pageable.getValue().getPageNumber());
        assertEquals(50, pageable.getValue().getPageSize());
    }

    @Test
    void dataInvalida_400ComMensagemPorCampo() throws Exception {
        when(agendamentoService.listarTodos(any(Pageable.class), any(), any()))
                .thenThrow(new CampoInvalidoException("de", "Data inicial inválida. Use o formato AAAA-MM-DD."));

        mvc.perform(get("/api/agendamentos").param("de", "23/10/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem.de").value("Data inicial inválida. Use o formato AAAA-MM-DD."));
    }
}

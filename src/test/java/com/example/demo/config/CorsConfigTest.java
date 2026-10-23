package com.example.demo.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class CorsConfigTest {

    @Autowired
    MockMvc mvc;

    private MockHttpServletResponse preflight(String origem) throws Exception {
        return mvc.perform(options("/auth/login")
                .header("Origin", origem)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type")).andReturn().getResponse();
    }

    @Test
    void appBokkaEAceitoComCredenciais() throws Exception {
        MockHttpServletResponse r = preflight("https://app.bokka.com.br");

        assertEquals(200, r.getStatus());
        assertEquals("https://app.bokka.com.br", r.getHeader("Access-Control-Allow-Origin"));
        assertEquals("true", r.getHeader("Access-Control-Allow-Credentials"));
        assertTrue(r.getHeader("Access-Control-Allow-Methods").contains("POST"));
    }

    @Test
    void origensQueJaExistiamContinuamAceitas() throws Exception {
        for (String origem : List.of("https://bokka.com.br", "https://www.bokka.com.br",
                "https://odonto-clinic-web.pages.dev", "http://localhost:5173", "http://localhost:3000",
                "http://127.0.0.1:5173")) {
            MockHttpServletResponse r = preflight(origem);
            assertEquals(200, r.getStatus(), origem);
            assertEquals(origem, r.getHeader("Access-Control-Allow-Origin"), origem);
        }
    }

    @Test
    void origemEstranhaERejeitada() throws Exception {
        for (String origem : List.of("https://evil.com", "https://app.bokka.com.br.evil.com",
                "https://outro.bokka.com.br", "http://app.bokka.com.br", "https://bokka.com.br:8443", "null")) {
            MockHttpServletResponse r = preflight(origem);
            assertEquals(403, r.getStatus(), origem);
            assertNull(r.getHeader("Access-Control-Allow-Origin"), origem);
        }
    }

    @Test
    void requisicaoRealDeAppBokkaRecebeOCabecalhoCors() throws Exception {
        MockHttpServletResponse r = mvc.perform(post("/auth/login").header("Origin", "https://app.bokka.com.br")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse();

        assertEquals(400, r.getStatus());
        assertEquals("https://app.bokka.com.br", r.getHeader("Access-Control-Allow-Origin"));
    }

    @Test
    void requisicaoRealDeOrigemEstranhaNaoRecebeOCabecalho() throws Exception {
        MockHttpServletResponse r = mvc.perform(post("/auth/login").header("Origin", "https://evil.com")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse();

        assertEquals(403, r.getStatus());
        assertNull(r.getHeader("Access-Control-Allow-Origin"));
    }
}

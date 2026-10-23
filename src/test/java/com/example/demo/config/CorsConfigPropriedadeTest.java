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

@SpringBootTest(properties = "app.cors.allowed-origins=https://so-este.com, https://tambem.com")
@AutoConfigureMockMvc
class CorsConfigPropriedadeTest {

    @Autowired
    MockMvc mvc;

    private int preflight(String origem) throws Exception {
        return mvc.perform(options("/auth/login").header("Origin", origem)
                .header("Access-Control-Request-Method", "POST")).andReturn().getResponse().getStatus();
    }

    @Test
    void propriedadeSubstituiAListaInteiraEIgnoraEspacosAposAVirgula() throws Exception {
        assertEquals(200, preflight("https://so-este.com"));
        assertEquals(200, preflight("https://tambem.com"));
        assertEquals(403, preflight("https://app.bokka.com.br"));
        assertEquals(403, preflight("http://localhost:5173"));
    }
}

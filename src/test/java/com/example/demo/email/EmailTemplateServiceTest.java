package com.example.demo.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EmailTemplateServiceTest {

    private final EmailTemplateService service = new EmailTemplateService("http://localhost:5173/");

    private Map<String, String> variaveis() {
        Map<String, String> v = new HashMap<>();
        v.put("nome", "Maria");
        v.put("nomeClinica", "Consultório Tainah Santos");
        v.put("link", "http://localhost:5173/redefinir-senha?token=abc&c=tainah");
        v.put("validade", "30 minutos");
        v.put("dataHora", "22/10/2026 às 10:15");
        return v;
    }

    @ParameterizedTest
    @ValueSource(strings = {"reset-senha", "confirmar-email-dono", "senha-alterada-alerta"})
    void renderizar_TemplatesDoDesignerSemPlaceholderSobrando(String template) {
        String html = service.renderizar(template, variaveis());

        assertFalse(html.contains("${"), "sobrou placeholder em " + template);
        assertTrue(html.contains("Maria"));
        assertTrue(html.contains("Consultório Tainah Santos"));
    }

    @Test
    void renderizar_UsaOLinkInformadoNoTemplateDeReset() {
        String html = service.renderizar("reset-senha", variaveis());

        assertTrue(html.contains("http://localhost:5173/redefinir-senha?token=abc&amp;c=tainah"));
        assertTrue(html.contains("30 minutos"));
    }

    @Test
    void renderizar_EscapaHtmlNosValores() {
        Map<String, String> v = variaveis();
        v.put("nome", "<script>alert('x')</script> & \"aspas\"");
        v.put("nomeClinica", "<img src=x onerror=alert(1)>");

        String html = service.renderizar("reset-senha", v);

        assertFalse(html.contains("<script>alert"));
        assertFalse(html.contains("<img src=x"));
        assertTrue(html.contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; &quot;aspas&quot;"));
        assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;"));
    }

    @Test
    void renderizar_ValorComCaracteresDeSubstituicaoNaoQuebra() {
        Map<String, String> v = variaveis();
        v.put("nome", "Ana $1 \\ ${link}");

        String html = service.renderizar("reset-senha", v);

        assertTrue(html.contains("Ana $1 \\ ${link}"), "o valor entra literal, sem ser reinterpretado");
        assertFalse(html.contains("Ana $1 \\ http"));
    }

    @Test
    void renderizar_VariavelAusenteLancaExcecaoEmVezDeDeixarPlaceholder() {
        Map<String, String> v = variaveis();
        v.remove("link");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.renderizar("reset-senha", v));

        assertTrue(ex.getMessage().contains("link"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"../application", "reset-senha.html", "RESET", "a/b", ""})
    void renderizar_NomeDeTemplateInvalidoEBloqueado(String nome) {
        assertThrows(IllegalArgumentException.class, () -> service.renderizar(nome, variaveis()));
    }

    @Test
    void renderizar_TemplateInexistenteLancaExcecao() {
        assertThrows(RuntimeException.class, () -> service.renderizar("nao-existe", variaveis()));
    }

    @Test
    void linkFrontend_UsaSempreAUrlConfiguradaESemBarraDuplicada() {
        String link = service.linkFrontend("/redefinir-senha", linkedParams("tok-en_123", "tainah"));

        assertEquals("http://localhost:5173/redefinir-senha?token=tok-en_123&c=tainah", link);
    }

    @Test
    void linkFrontend_ProducaoComHttpsEParametrosCodificados() {
        EmailTemplateService producao = new EmailTemplateService("https://bokka.com.br");

        String link = producao.linkFrontend("confirmar-email", linkedParams("a b&c=d", "clínica"));

        assertTrue(link.startsWith("https://bokka.com.br/confirmar-email?"));
        assertTrue(link.contains("token=a%20b%26c%3Dd"));
        assertTrue(link.contains("c=cl%C3%ADnica"));
    }

    @Test
    void linkFrontend_VarargsMantemAOrdemDosParametrosEExigePares() {
        assertEquals("http://localhost:5173/x?token=t&c=tainah", service.linkFrontend("/x", "token", "t", "c", "tainah"));
        assertEquals("http://localhost:5173/x?c=tainah", service.linkFrontend("x", "c", "tainah"));
        assertThrows(IllegalArgumentException.class, () -> service.linkFrontend("/x", "token"));
    }

    private Map<String, String> linkedParams(String token, String c) {
        Map<String, String> p = new java.util.LinkedHashMap<>();
        p.put("token", token);
        p.put("c", c);
        return p;
    }
}

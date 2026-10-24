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
        v.put("codigoFormatado", "849 201");
        v.put("validadeCodigo", "15 minutos");
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

    @ParameterizedTest
    @ValueSource(strings = {"reset-senha", "confirmar-email-dono", "senha-alterada-alerta"})
    void renderizarTexto_VersaoTextoLimpaComUmLinkEmLinhaPropria(String template) {
        String texto = service.renderizarTexto(template, variaveis());
        String link = "http://localhost:5173/redefinir-senha?token=abc&c=tainah";

        assertFalse(texto.contains("${"), "sobrou placeholder em " + template);
        assertFalse(texto.contains("<"), "texto simples não deve ter HTML");
        assertFalse(texto.contains("&nbsp;") || texto.contains("&zwnj;") || texto.contains("\u200c"));
        assertTrue(texto.contains("Maria"));
        assertTrue(texto.contains("Consultório Tainah Santos"));
        assertTrue(texto.contains("\n" + link + "\n"), "link sem escape, em linha própria");
        assertEquals(texto.indexOf(link), texto.lastIndexOf(link), "link aparece uma única vez");
        String primeiraLinha = texto.lines().findFirst().orElse("");
        assertFalse(primeiraLinha.isBlank());
        assertNotEquals(primeiraLinha.toUpperCase(), primeiraLinha, "título não deve vir em maiúsculas");
    }

    @Test
    void renderizarTexto_ResetTemRodapeDeMotivoSemDadosInventados() {
        String texto = service.renderizarTexto("reset-senha", variaveis());

        assertTrue(texto.contains("Você recebeu este e-mail porque alguém solicitou a redefinição de senha da sua conta no Bokka."));
        assertFalse(texto.toLowerCase().contains("cnpj"));
    }

    @Test
    void renderizarTexto_NaoEscapaHtmlMasNaoDeixaValorCriarLinhas() {
        Map<String, String> v = variaveis();
        v.put("nome", "Ana & Cia\nBcc: x@y.com\r\nOutra");

        String texto = service.renderizarTexto("reset-senha", v);

        assertTrue(texto.contains("Olá, Ana & Cia Bcc: x@y.com Outra!"));
        assertFalse(texto.contains("&amp;"));
    }

    @Test
    void renderizarTexto_VariavelAusenteLancaExcecao() {
        Map<String, String> v = variaveis();
        v.remove("nomeClinica");

        assertThrows(IllegalStateException.class, () -> service.renderizarTexto("reset-senha", v));
    }

    @Test
    void renderizarTexto_NomeInvalidoEInexistenteLancamExcecao() {
        assertThrows(IllegalArgumentException.class, () -> service.renderizarTexto("../application", variaveis()));
        assertThrows(RuntimeException.class, () -> service.renderizarTexto("nao-existe", variaveis()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"reset-senha", "confirmar-email-dono", "senha-alterada-alerta"})
    void renderizar_HtmlSemPreheaderEscondidoNemImagemRemota(String template) {
        String html = service.renderizar(template, variaveis());

        assertFalse(html.contains("display:none"), "sem texto escondido");
        assertFalse(html.contains("&zwnj;"));
        assertFalse(html.contains("<img"));
        assertFalse(html.toLowerCase().contains("text-transform:uppercase"));
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

package com.example.demo.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EmailTemplateService {

    private static final String PASTA = "templates/email/";
    private static final Pattern NOME_VALIDO = Pattern.compile("[a-z0-9-]+");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z][A-Za-z0-9]*)}");

    private final String frontendUrl;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public EmailTemplateService(@Value("${app.frontend-url}") String frontendUrl) {
        this.frontendUrl = frontendUrl.trim().replaceAll("/+$", "");
    }

    /**
     * Carrega {@code templates/email/<template>.html} e troca cada {@code ${variavel}} pelo valor,
     * com escape de HTML. Placeholder sem valor é erro de programação e lança exceção.
     */
    public String renderizar(String template, Map<String, String> variaveis) {
        return substituir(carregar(template, "html"), template, variaveis, valor -> HtmlUtils.htmlEscape(valor, StandardCharsets.UTF_8.name()));
    }

    /**
     * Carrega {@code templates/email/<template>.txt} (versão texto simples do e-mail). Sem escape de HTML;
     * quebras de linha dos valores viram espaço para que um valor não consiga criar linhas novas no texto.
     */
    public String renderizarTexto(String template, Map<String, String> variaveis) {
        return substituir(carregar(template, "txt"), template, variaveis, valor -> valor.replaceAll("[\r\n]+", " "));
    }

    private String substituir(String modelo, String template, Map<String, String> variaveis, UnaryOperator<String> formatar) {
        Matcher m = PLACEHOLDER.matcher(modelo);
        StringBuilder saida = new StringBuilder();
        while (m.find()) {
            String valor = variaveis.get(m.group(1));
            if (valor == null) {
                throw new IllegalStateException(
                        "Template '" + template + "': variável '" + m.group(1) + "' não informada.");
            }
            m.appendReplacement(saida, Matcher.quoteReplacement(formatar.apply(valor)));
        }
        m.appendTail(saida);
        return saida.toString();
    }

    /** Monta um link absoluto sempre a partir de {@code app.frontend-url}; os parâmetros são codificados. */
    public String linkFrontend(String caminho, Map<String, String> parametros) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(frontendUrl)
                .path(caminho.startsWith("/") ? caminho : "/" + caminho);
        parametros.forEach((nome, valor) -> uri.queryParam(nome, "{" + nome + "}"));
        return uri.encode().build(parametros).toString();
    }

    /** Igual ao anterior, com parâmetros na ordem dada: {@code linkFrontend("/x", "token", t, "c", codigo)}. */
    public String linkFrontend(String caminho, String... paresNomeValor) {
        if (paresNomeValor.length % 2 != 0) {
            throw new IllegalArgumentException("Parâmetros do link devem vir em pares nome/valor.");
        }
        Map<String, String> parametros = new LinkedHashMap<>();
        for (int i = 0; i < paresNomeValor.length; i += 2) {
            parametros.put(paresNomeValor[i], paresNomeValor[i + 1]);
        }
        return linkFrontend(caminho, parametros);
    }

    private String carregar(String template, String extensao) {
        if (template == null || !NOME_VALIDO.matcher(template).matches()) {
            throw new IllegalArgumentException("Nome de template inválido: " + template);
        }
        return cache.computeIfAbsent(template + "." + extensao, chave -> {
            try (var in = new ClassPathResource(PASTA + chave).getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("Template de e-mail não encontrado: " + chave, e);
            }
        });
    }
}

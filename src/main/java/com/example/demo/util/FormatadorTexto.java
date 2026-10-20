package com.example.demo.util;

import com.example.demo.exception.CampoInvalidoException;
import com.example.demo.model.Clinica;
import com.example.demo.model.Convenio;
import com.example.demo.model.Dentista;
import com.example.demo.model.Endereco;
import com.example.demo.model.Funcionario;
import com.example.demo.model.Paciente;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normaliza os dados de contato e documentos para o formato canônico gravado no banco.
 * Aceita a entrada com ou sem máscara; valor vazio vira {@code null}; valor inválido
 * lança 400 com mensagem em português.
 */
public final class FormatadorTexto {

    private static final Set<String> UFS = Set.of(
            "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA",
            "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

    private static final Pattern CARACTERES_CPF = Pattern.compile("[\\d.\\-\\s]+");
    private static final Pattern CARACTERES_CNPJ = Pattern.compile("[0-9A-Za-z.\\-/\\s]+");
    private static final Pattern CARACTERES_TELEFONE = Pattern.compile("[\\d()\\-+.\\s]+");
    private static final Pattern CARACTERES_CEP = Pattern.compile("[\\d.\\-\\s]+");
    private static final Pattern CNPJ_BASE = Pattern.compile("[0-9A-Z]{12}\\d{2}");
    private static final Pattern CRO = Pattern.compile(
            "CRO\\s*[-/]?\\s*([A-Z]{2})\\s*[-/]?\\s*(\\d{4,6})(?:\\s*/\\s*(\\d{4}))?");

    private static final int[] PESOS_CNPJ_1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] PESOS_CNPJ_2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private FormatadorTexto() {}

    public static String cpf(String valor) {
        if (vazio(valor)) {
            return null;
        }
        String texto = valor.trim();
        String d = somenteDigitos(texto);
        if (!CARACTERES_CPF.matcher(texto).matches() || !cpfValido(d)) {
            throw invalido("CPF inválido. Verifique os números digitados.");
        }
        return d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9);
    }

    public static String cnpj(String valor) {
        if (vazio(valor)) {
            return null;
        }
        String texto = valor.trim();
        if (!CARACTERES_CNPJ.matcher(texto).matches()) {
            throw invalido("CNPJ inválido. Verifique os números digitados.");
        }
        String c = texto.replaceAll("[.\\-/\\s]", "").toUpperCase(Locale.ROOT);
        if (!cnpjValido(c)) {
            throw invalido("CNPJ inválido. Verifique os números digitados.");
        }
        return c.substring(0, 2) + "." + c.substring(2, 5) + "." + c.substring(5, 8) + "/"
                + c.substring(8, 12) + "-" + c.substring(12);
    }

    public static String cro(String valor) {
        if (vazio(valor)) {
            return null;
        }
        Matcher m = CRO.matcher(valor.trim().toUpperCase(Locale.ROOT));
        if (!m.matches() || !UFS.contains(m.group(1))) {
            throw invalido("CRO inválido. Use o formato CRO-UF 12345 ou CRO-UF 12345/2022.");
        }
        return "CRO-" + m.group(1) + " " + m.group(2) + (m.group(3) != null ? "/" + m.group(3) : "");
    }

    public static String celular(String valor) {
        return telefone(valor, "Celular", true, false, false);
    }

    public static String fixo(String valor) {
        return telefone(valor, "Telefone fixo", false, true, false);
    }

    public static String telefone(String valor, String rotulo) {
        return telefone(valor, rotulo, true, true, false);
    }

    public static String telefoneCentral(String valor, String rotulo) {
        return telefone(valor, rotulo, true, true, true);
    }

    public static String email(String valor) {
        if (vazio(valor)) {
            return null;
        }
        return valor.trim().toLowerCase(Locale.ROOT);
    }

    public static String cep(String valor) {
        if (vazio(valor)) {
            return null;
        }
        String texto = valor.trim();
        String d = somenteDigitos(texto);
        if (!CARACTERES_CEP.matcher(texto).matches() || d.length() != 8) {
            throw invalido("CEP inválido. Use o formato 00000-000.");
        }
        return d.substring(0, 5) + "-" + d.substring(5);
    }

    public static String nome(String valor) {
        if (vazio(valor)) {
            return null;
        }
        return valor.trim().replaceAll("\\s+", " ");
    }

    public static String cpfResponsavel(String valor) {
        if (vazio(valor)) {
            return null;
        }
        try {
            return cpf(valor);
        } catch (ResponseStatusException e) {
            throw new CampoInvalidoException("responsavelCpf",
                    "CPF do responsável inválido. Verifique os números digitados.");
        }
    }

    public static void normalizar(Paciente p) {
        p.setResponsavelNome(nome(p.getResponsavelNome()));
        p.setResponsavelCpf(cpfResponsavel(p.getResponsavelCpf()));
        if (p.getResponsavelCpf() != null && p.getResponsavelNome() == null) {
            throw new CampoInvalidoException("responsavelNome",
                    "Nome do responsável é obrigatório quando o CPF do responsável é informado.");
        }
        if (p.getResponsavelNome() != null && p.getResponsavelCpf() == null) {
            throw new CampoInvalidoException("responsavelCpf",
                    "CPF do responsável é obrigatório quando o nome do responsável é informado.");
        }
        p.setCpf(cpf(p.getCpf()));
        p.setEmail(email(p.getEmail()));
        p.setTelefoneCelular(celular(p.getTelefoneCelular()));
        p.setTelefoneFixo(fixo(p.getTelefoneFixo()));
        p.setTelefoneEmergencia(telefone(p.getTelefoneEmergencia(), "Telefone de emergência"));
        normalizar(p.getEndereco());
    }

    public static void normalizar(Funcionario f) {
        f.setCpf(cpf(f.getCpf()));
        f.setEmail(email(f.getEmail()));
        f.setTelefoneCelular(celular(f.getTelefoneCelular()));
        normalizar(f.getEndereco());
    }

    public static void normalizar(Dentista d) {
        d.setCro(cro(d.getCro()));
        d.setCpf(cpf(d.getCpf()));
        d.setEmail(email(d.getEmail()));
        d.setTelefoneCelular(celular(d.getTelefoneCelular()));
        normalizar(d.getEndereco());
    }

    public static void normalizar(Convenio c) {
        c.setCnpj(cnpj(c.getCnpj()));
        c.setTelefone(telefoneCentral(c.getTelefone(), "Telefone"));
        c.setEmail(email(c.getEmail()));
        normalizar(c.getEndereco());
    }

    public static void normalizar(Clinica c) {
        c.setCnpj(cnpj(c.getCnpj()));
        c.setTelefone(telefoneCentral(c.getTelefone(), "Telefone"));
        c.setEmailContato(email(c.getEmailContato()));
        normalizar(c.getEndereco());
    }

    public static void normalizar(Endereco e) {
        if (e != null) {
            e.setCep(cep(e.getCep()));
        }
    }

    public record ErroCampo(String campo, String valor, String motivo) {}

    public static List<ErroCampo> erros(Paciente p) {
        List<ErroCampo> erros = new ArrayList<>();
        if (vazio(p.getCpf())) {
            erros.add(new ErroCampo("cpf", p.getCpf(), "CPF não informado."));
        } else {
            checar(erros, "cpf", p.getCpf(), FormatadorTexto::cpf);
        }
        checar(erros, "responsavelCpf", p.getResponsavelCpf(), FormatadorTexto::cpfResponsavel);
        checar(erros, "telefoneCelular", p.getTelefoneCelular(), FormatadorTexto::celular);
        checar(erros, "telefoneFixo", p.getTelefoneFixo(), FormatadorTexto::fixo);
        checar(erros, "telefoneEmergencia", p.getTelefoneEmergencia(), v -> telefone(v, "Telefone de emergência"));
        checarCep(erros, p.getEndereco());
        return erros;
    }

    public static List<ErroCampo> erros(Funcionario f) {
        List<ErroCampo> erros = new ArrayList<>();
        checar(erros, "cpf", f.getCpf(), FormatadorTexto::cpf);
        checar(erros, "telefoneCelular", f.getTelefoneCelular(), FormatadorTexto::celular);
        checarCep(erros, f.getEndereco());
        return erros;
    }

    public static List<ErroCampo> erros(Dentista d) {
        List<ErroCampo> erros = new ArrayList<>();
        checar(erros, "cro", d.getCro(), FormatadorTexto::cro);
        checar(erros, "cpf", d.getCpf(), FormatadorTexto::cpf);
        checar(erros, "telefoneCelular", d.getTelefoneCelular(), FormatadorTexto::celular);
        checarCep(erros, d.getEndereco());
        return erros;
    }

    public static List<ErroCampo> erros(Convenio c) {
        List<ErroCampo> erros = new ArrayList<>();
        checar(erros, "cnpj", c.getCnpj(), FormatadorTexto::cnpj);
        checar(erros, "telefone", c.getTelefone(), v -> telefoneCentral(v, "Telefone"));
        checarCep(erros, c.getEndereco());
        return erros;
    }

    public static List<ErroCampo> erros(Clinica c) {
        List<ErroCampo> erros = new ArrayList<>();
        checar(erros, "cnpj", c.getCnpj(), FormatadorTexto::cnpj);
        checar(erros, "telefone", c.getTelefone(), v -> telefoneCentral(v, "Telefone"));
        checarCep(erros, c.getEndereco());
        return erros;
    }

    private static void checarCep(List<ErroCampo> erros, Endereco endereco) {
        if (endereco != null) {
            checar(erros, "endereco.cep", endereco.getCep(), FormatadorTexto::cep);
        }
    }

    private static void checar(List<ErroCampo> erros, String campo, String valor, UnaryOperator<String> normalizador) {
        try {
            normalizador.apply(valor);
        } catch (ResponseStatusException e) {
            erros.add(new ErroCampo(campo, valor, e.getReason()));
        }
    }

    private static String telefone(String valor, String rotulo, boolean aceitaCelular, boolean aceitaFixo,
                                   boolean aceita0800) {
        if (vazio(valor)) {
            return null;
        }
        String texto = valor.trim();
        String d = somenteDigitos(texto);
        if (aceita0800 && CARACTERES_TELEFONE.matcher(texto).matches() && d.length() == 11
                && (d.startsWith("0800") || d.startsWith("0300"))) {
            return d.substring(0, 4) + " " + d.substring(4, 7) + " " + d.substring(7);
        }
        if (CARACTERES_TELEFONE.matcher(texto).matches() && (d.length() == 12 || d.length() == 13) && d.startsWith("55")) {
            d = d.substring(2);
        }
        boolean ddd = d.length() >= 2 && d.charAt(0) != '0' && d.charAt(1) != '0';
        boolean celular = d.length() == 11 && d.charAt(2) == '9';
        boolean fixo = d.length() == 10 && d.charAt(2) >= '2' && d.charAt(2) <= '5';

        if (!CARACTERES_TELEFONE.matcher(texto).matches() || !ddd
                || !((aceitaCelular && celular) || (aceitaFixo && fixo))) {
            throw invalido(rotulo + " inválido. Use " + formatoEsperado(aceitaCelular, aceitaFixo, aceita0800) + ".");
        }
        String ddd2 = "(" + d.substring(0, 2) + ") ";
        return celular
                ? ddd2 + d.substring(2, 7) + "-" + d.substring(7)
                : ddd2 + d.substring(2, 6) + "-" + d.substring(6);
    }

    private static String formatoEsperado(boolean aceitaCelular, boolean aceitaFixo, boolean aceita0800) {
        if (aceita0800) {
            return "(XX) 9XXXX-XXXX, (XX) XXXX-XXXX ou 0800 XXX XXXX";
        }
        if (aceitaCelular && aceitaFixo) {
            return "(XX) 9XXXX-XXXX ou (XX) XXXX-XXXX";
        }
        return aceitaCelular ? "(XX) 9XXXX-XXXX" : "(XX) XXXX-XXXX";
    }

    public static boolean cpfValido(String digitos) {
        if (digitos == null || !digitos.matches("\\d{11}") || digitos.chars().distinct().count() == 1) {
            return false;
        }
        return digitoCpf(digitos, 9) == digitos.charAt(9) - '0'
                && digitoCpf(digitos, 10) == digitos.charAt(10) - '0';
    }

    public static boolean cnpjValido(String normalizado) {
        if (normalizado == null || !CNPJ_BASE.matcher(normalizado).matches()
                || normalizado.chars().distinct().count() == 1) {
            return false;
        }
        return digitoCnpj(normalizado, PESOS_CNPJ_1) == normalizado.charAt(12) - '0'
                && digitoCnpj(normalizado, PESOS_CNPJ_2) == normalizado.charAt(13) - '0';
    }

    private static int digitoCpf(String d, int tamanho) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) {
            soma += (d.charAt(i) - '0') * (tamanho + 1 - i);
        }
        int resto = soma * 10 % 11;
        return resto == 10 ? 0 : resto;
    }

    private static int digitoCnpj(String c, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += (c.charAt(i) - '0') * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static String somenteDigitos(String texto) {
        return texto.replaceAll("\\D", "");
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static ResponseStatusException invalido(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}

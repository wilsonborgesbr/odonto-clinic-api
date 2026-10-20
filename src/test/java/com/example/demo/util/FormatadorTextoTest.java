package com.example.demo.util;

import com.example.demo.exception.CampoInvalidoException;
import com.example.demo.model.Clinica;
import com.example.demo.model.Convenio;
import com.example.demo.model.Dentista;
import com.example.demo.model.Endereco;
import com.example.demo.model.Funcionario;
import com.example.demo.model.Paciente;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

class FormatadorTextoTest {

    @Test
    void cpf_AceitaComESemMascaraEFormataCanonico() {
        assertEquals("529.982.247-25", FormatadorTexto.cpf("52998224725"));
        assertEquals("529.982.247-25", FormatadorTexto.cpf("529.982.247-25"));
        assertEquals("529.982.247-25", FormatadorTexto.cpf("  529 982 247 25 "));
        assertEquals("529.982.247-25", FormatadorTexto.cpf("529.982.24725"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"529.982.247-24", "11111111111", "00000000000", "123", "5299822472", "529982247255", "abc.982.247-25", "529.982.247-2x"})
    void cpf_InvalidoLanca400(String valor) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> FormatadorTexto.cpf(valor));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().startsWith("CPF inválido"));
    }

    @Test
    void cnpj_AceitaComESemMascaraEFormataCanonico() {
        assertEquals("11.222.333/0001-81", FormatadorTexto.cnpj("11222333000181"));
        assertEquals("11.222.333/0001-81", FormatadorTexto.cnpj("11.222.333/0001-81"));
        assertEquals("11.444.777/0001-61", FormatadorTexto.cnpj(" 11 444 777 0001 61 "));
    }

    @Test
    void cnpj_AlfanumericoComDigitoVerificadorValido() {
        String valido = comDigitosDeCnpj("12ABC34501DE");

        assertEquals("12.ABC.345/01DE-" + valido.substring(12), FormatadorTexto.cnpj(valido.toLowerCase()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"11.222.333/0001-80", "00000000000000", "11111111111111", "1122233300018", "112223330001811", "11.222.333/0001-8x", "AB.CDE.FGH/IJKL-MN"})
    void cnpj_InvalidoLanca400(String valor) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> FormatadorTexto.cnpj(valor));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().startsWith("CNPJ inválido"));
    }

    @Test
    void cro_NormalizaCaixaEspacosEAno() {
        assertEquals("CRO-SE 12345", FormatadorTexto.cro("CRO-SE 12345"));
        assertEquals("CRO-SE 12345", FormatadorTexto.cro("cro-se   12345"));
        assertEquals("CRO-SE 12345", FormatadorTexto.cro("  CRO SE 12345 "));
        assertEquals("CRO-SE 12345", FormatadorTexto.cro("CRO-SE12345"));
        assertEquals("CRO-SP 1234", FormatadorTexto.cro("cro-sp 1234"));
        assertEquals("CRO-SE 12345/2022", FormatadorTexto.cro("CRO-SE 12345/2022"));
        assertEquals("CRO-SE 12345/2022", FormatadorTexto.cro("cro-se 12345 / 2022"));
        assertEquals("CRO-MG 123456", FormatadorTexto.cro("CRO-MG 123456"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CRO-XX 12345", "CRO-SE 123", "CRO-SE 1234567", "CRO-SE 12345/22", "CRO-S 12345", "12345", "CRM-SE 12345", "CRO-SE ABCDE"})
    void cro_InvalidoLanca400(String valor) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> FormatadorTexto.cro(valor));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().startsWith("CRO inválido"));
    }

    @Test
    void celular_FormataCanonico() {
        assertEquals("(79) 99999-1234", FormatadorTexto.celular("79999991234"));
        assertEquals("(79) 99999-1234", FormatadorTexto.celular("(79) 99999-1234"));
        assertEquals("(79) 99999-1234", FormatadorTexto.celular("79 9 9999 1234"));
        assertEquals("(79) 99999-1234", FormatadorTexto.celular("+55 (79) 99999-1234"));
        assertEquals("(79) 99999-1234", FormatadorTexto.celular("5579999991234"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"(79) 3222-1234", "7932221234", "9999-1234", "(00) 99999-1234", "(79) 89999-1234", "799999912345", "(79) 99999-12ab"})
    void celular_InvalidoLanca400(String valor) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> FormatadorTexto.celular(valor));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().startsWith("Celular inválido"));
    }

    @Test
    void fixo_FormataCanonico() {
        assertEquals("(79) 3222-1234", FormatadorTexto.fixo("7932221234"));
        assertEquals("(79) 3222-1234", FormatadorTexto.fixo("(79) 3222-1234"));
        assertEquals("(11) 4004-0019", FormatadorTexto.fixo("+55 11 4004 0019"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"(79) 99999-1234", "79322212", "(79) 1222-1234", "(79) 6222-1234"})
    void fixo_InvalidoLanca400(String valor) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> FormatadorTexto.fixo(valor));
        assertTrue(ex.getReason().startsWith("Telefone fixo inválido"));
    }

    @Test
    void telefoneGenerico_AceitaCelularOuFixoEUsaRotuloNaMensagem() {
        assertEquals("(79) 99999-1234", FormatadorTexto.telefone("79999991234", "Telefone de emergência"));
        assertEquals("(79) 3222-1234", FormatadorTexto.telefone("7932221234", "Telefone de emergência"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> FormatadorTexto.telefone("123", "Telefone de emergência"));
        assertTrue(ex.getReason().startsWith("Telefone de emergência inválido"));
    }

    @Test
    void email_TrimEMinusculo() {
        assertEquals("maria.silva@gmail.com", FormatadorTexto.email("  Maria.Silva@Gmail.COM "));
    }

    @Test
    void cep_FormataCanonico() {
        assertEquals("49000-123", FormatadorTexto.cep("49000123"));
        assertEquals("49000-123", FormatadorTexto.cep("49000-123"));
        assertEquals("49000-123", FormatadorTexto.cep(" 49.000-123 "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"4900012", "490001234", "49000-12a", "abcde-fgh"})
    void cep_InvalidoLanca400(String valor) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> FormatadorTexto.cep(valor));
        assertTrue(ex.getReason().startsWith("CEP inválido"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void camposOpcionaisVaziosViramNull(String valor) {
        for (UnaryOperator<String> f : java.util.List.<UnaryOperator<String>>of(
                FormatadorTexto::cpf, FormatadorTexto::cnpj, FormatadorTexto::cro, FormatadorTexto::celular,
                FormatadorTexto::fixo, FormatadorTexto::email, FormatadorTexto::cep,
                v -> FormatadorTexto.telefone(v, "Telefone"))) {
            assertNull(f.apply(valor));
        }
    }

    @Test
    void ehIdempotente() {
        assertEquals("529.982.247-25", FormatadorTexto.cpf(FormatadorTexto.cpf("52998224725")));
        assertEquals("11.222.333/0001-81", FormatadorTexto.cnpj(FormatadorTexto.cnpj("11222333000181")));
        assertEquals("CRO-SE 12345/2022", FormatadorTexto.cro(FormatadorTexto.cro("cro se 12345/2022")));
        assertEquals("(79) 99999-1234", FormatadorTexto.celular(FormatadorTexto.celular("79999991234")));
        assertEquals("(79) 3222-1234", FormatadorTexto.fixo(FormatadorTexto.fixo("7932221234")));
        assertEquals("49000-123", FormatadorTexto.cep(FormatadorTexto.cep("49000123")));
    }

    @Test
    void normalizarPaciente_AplicaTodosOsCampos() {
        Paciente p = Paciente.builder()
                .cpf("52998224725")
                .email("  Ana@Mail.COM ")
                .telefoneCelular("79999991234")
                .telefoneFixo("7932221234")
                .telefoneEmergencia("")
                .endereco(Endereco.builder().cep("49000123").build())
                .build();

        FormatadorTexto.normalizar(p);

        assertEquals("529.982.247-25", p.getCpf());
        assertEquals("ana@mail.com", p.getEmail());
        assertEquals("(79) 99999-1234", p.getTelefoneCelular());
        assertEquals("(79) 3222-1234", p.getTelefoneFixo());
        assertNull(p.getTelefoneEmergencia());
        assertEquals("49000-123", p.getEndereco().getCep());
    }

    @Test
    void normalizarFuncionario_AplicaTodosOsCampos() {
        Funcionario f = new Funcionario();
        f.setCpf("52998224725");
        f.setEmail("F@X.com");
        f.setTelefoneCelular("79999991234");
        f.setEndereco(Endereco.builder().cep("49000123").build());

        FormatadorTexto.normalizar(f);

        assertEquals("529.982.247-25", f.getCpf());
        assertEquals("f@x.com", f.getEmail());
        assertEquals("(79) 99999-1234", f.getTelefoneCelular());
        assertEquals("49000-123", f.getEndereco().getCep());
    }

    @Test
    void normalizarDentista_CpfOpcionalVirouNullECroFormatado() {
        Dentista d = new Dentista();
        d.setCro("cro-se 12345");
        d.setCpf("  ");
        d.setEmail("");
        d.setTelefoneCelular(null);

        FormatadorTexto.normalizar(d);

        assertEquals("CRO-SE 12345", d.getCro());
        assertNull(d.getCpf());
        assertNull(d.getEmail());
    }

    @Test
    void normalizarConvenioEClinica_AplicaTodosOsCampos() {
        Convenio c = new Convenio();
        c.setCnpj("11222333000181");
        c.setTelefone("1140040019");
        c.setEmail("CONTATO@Conv.com");
        FormatadorTexto.normalizar(c);
        assertEquals("11.222.333/0001-81", c.getCnpj());
        assertEquals("(11) 4004-0019", c.getTelefone());
        assertEquals("contato@conv.com", c.getEmail());

        Clinica cl = new Clinica();
        cl.setCnpj("11444777000161");
        cl.setTelefone("79999991234");
        cl.setEmailContato(" CLINICA@x.com ");
        FormatadorTexto.normalizar(cl);
        assertEquals("11.444.777/0001-61", cl.getCnpj());
        assertEquals("(79) 99999-1234", cl.getTelefone());
        assertEquals("clinica@x.com", cl.getEmailContato());
    }

    @Test
    void normalizarComEnderecoNuloNaoQuebra() {
        assertDoesNotThrow(() -> FormatadorTexto.normalizar(new Paciente()));
    }

    @Test
    void telefoneCentral_Aceita0800E0300ComFormatoCanonico() {
        assertEquals("0800 123 4567", FormatadorTexto.telefoneCentral("08001234567", "Telefone"));
        assertEquals("0800 123 4567", FormatadorTexto.telefoneCentral("0800 123 4567", "Telefone"));
        assertEquals("0800 123 4567", FormatadorTexto.telefoneCentral("0800-123-4567", "Telefone"));
        assertEquals("0300 313 3003", FormatadorTexto.telefoneCentral("03003133003", "Telefone"));
        assertEquals("0800 123 4567", FormatadorTexto.telefoneCentral(FormatadorTexto.telefoneCentral("08001234567", "Telefone"), "Telefone"));
    }

    @Test
    void telefoneCentral_ContinuaAceitandoFixoECelularEUsaMensagemComAs3Opcoes() {
        assertEquals("(11) 4004-0019", FormatadorTexto.telefoneCentral("1140040019", "Telefone"));
        assertEquals("(79) 99999-1234", FormatadorTexto.telefoneCentral("79999991234", "Telefone"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> FormatadorTexto.telefoneCentral("0900 123 4567", "Telefone"));
        assertEquals("Telefone inválido. Use (XX) 9XXXX-XXXX, (XX) XXXX-XXXX ou 0800 XXX XXXX.", ex.getReason());
        assertThrows(ResponseStatusException.class, () -> FormatadorTexto.telefoneCentral("0800 123 456", "Telefone"));
        assertThrows(ResponseStatusException.class, () -> FormatadorTexto.telefoneCentral("0800 123 45678", "Telefone"));
    }

    @Test
    void demaisTelefonesContinuamRejeitando0800() {
        assertThrows(ResponseStatusException.class, () -> FormatadorTexto.celular("08001234567"));
        assertThrows(ResponseStatusException.class, () -> FormatadorTexto.fixo("08001234567"));
        assertThrows(ResponseStatusException.class, () -> FormatadorTexto.telefone("08001234567", "Telefone de emergência"));

        Paciente p = Paciente.builder().telefoneEmergencia("08001234567").build();
        assertThrows(ResponseStatusException.class, () -> FormatadorTexto.normalizar(p));
    }

    @Test
    void convenioEClinica_Aceitam0800NoTelefone() {
        Convenio c = new Convenio();
        c.setTelefone("08001234567");
        FormatadorTexto.normalizar(c);
        assertEquals("0800 123 4567", c.getTelefone());

        Clinica cl = new Clinica();
        cl.setTelefone("0300 313 3003");
        FormatadorTexto.normalizar(cl);
        assertEquals("0300 313 3003", cl.getTelefone());
    }

    @Test
    void erros_ListaCampoValorEMotivoSemAlterarOObjeto() {
        Paciente p = Paciente.builder().cpf("123.456.789-00").telefoneCelular("(79) 99999-1234")
                .telefoneFixo("123").email("  X@Y.COM ").endereco(Endereco.builder().cep("4900").build()).build();

        var erros = FormatadorTexto.erros(p);

        assertEquals(java.util.List.of("cpf", "telefoneFixo", "endereco.cep"),
                erros.stream().map(FormatadorTexto.ErroCampo::campo).toList());
        assertEquals("123.456.789-00", erros.get(0).valor());
        assertTrue(erros.get(0).motivo().startsWith("CPF inválido"));
        assertEquals("  X@Y.COM ", p.getEmail());
    }

    @Test
    void nome_AparaEspacosEVazioViraNull() {
        assertEquals("Joana da Silva", FormatadorTexto.nome("  Joana   da \t Silva "));
        assertNull(FormatadorTexto.nome("   "));
        assertNull(FormatadorTexto.nome(null));
    }

    @Test
    void cpfResponsavel_NormalizaEVazioViraNull() {
        assertEquals("529.982.247-25", FormatadorTexto.cpfResponsavel("52998224725"));
        assertNull(FormatadorTexto.cpfResponsavel(""));
        assertNull(FormatadorTexto.cpfResponsavel(null));
    }

    @Test
    void cpfResponsavel_InvalidoLancaErroNoCampoResponsavelCpf() {
        CampoInvalidoException ex = assertThrows(CampoInvalidoException.class,
                () -> FormatadorTexto.cpfResponsavel("529.982.247-24"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("responsavelCpf", ex.getCampo());
        assertEquals("CPF do responsável inválido. Verifique os números digitados.", ex.getReason());
    }

    @Test
    void normalizar_PacienteResponsavelExigeNomeECpfJuntos() {
        Paciente ambos = Paciente.builder().responsavelNome(" Joana ").responsavelCpf("52998224725").build();
        FormatadorTexto.normalizar(ambos);
        assertEquals("Joana", ambos.getResponsavelNome());
        assertEquals("529.982.247-25", ambos.getResponsavelCpf());

        Paciente nenhum = Paciente.builder().responsavelNome(" ").responsavelCpf("").build();
        FormatadorTexto.normalizar(nenhum);
        assertNull(nenhum.getResponsavelNome());
        assertNull(nenhum.getResponsavelCpf());

        CampoInvalidoException soCpf = assertThrows(CampoInvalidoException.class, () -> FormatadorTexto.normalizar(
                Paciente.builder().responsavelCpf("52998224725").build()));
        assertEquals("responsavelNome", soCpf.getCampo());

        CampoInvalidoException soNome = assertThrows(CampoInvalidoException.class, () -> FormatadorTexto.normalizar(
                Paciente.builder().responsavelNome("Joana").build()));
        assertEquals("responsavelCpf", soNome.getCampo());
    }

    @Test
    void erros_PacienteSemCpfEPendenciaCpfNaoInformado() {
        for (String vazio : new String[]{null, "", "   "}) {
            var erros = FormatadorTexto.erros(Paciente.builder().cpf(vazio).build());
            assertEquals(1, erros.size());
            assertEquals("cpf", erros.get(0).campo());
            assertEquals("CPF não informado.", erros.get(0).motivo());
        }
    }

    @Test
    void erros_PacienteAcusaResponsavelCpfInvalidoMasNaoVazio() {
        var invalido = FormatadorTexto.erros(Paciente.builder().cpf("529.982.247-25")
                .responsavelNome("Joana").responsavelCpf("123").build());
        assertEquals(1, invalido.size());
        assertEquals("responsavelCpf", invalido.get(0).campo());
        assertEquals("123", invalido.get(0).valor());
        assertTrue(invalido.get(0).motivo().startsWith("CPF do responsável inválido"));

        assertTrue(FormatadorTexto.erros(Paciente.builder().cpf("529.982.247-25").build()).isEmpty());
    }

    @Test
    void erros_DentistaSemCpfContinuaSemPendencia() {
        Dentista d = new Dentista();
        d.setCpf(null);
        assertTrue(FormatadorTexto.erros(d).isEmpty());
    }

    @Test
    void erros_LegadoValidoSemMascaraOuVazioNaoViraPendencia() {
        Dentista d = new Dentista();
        d.setCro("cro-se 12345");
        d.setCpf("");
        d.setTelefoneCelular("79999991234");
        assertTrue(FormatadorTexto.erros(d).isEmpty());

        Convenio c = new Convenio();
        c.setCnpj("11222333000181");
        c.setTelefone("08001234567");
        assertTrue(FormatadorTexto.erros(c).isEmpty());

        assertTrue(FormatadorTexto.erros(new Funcionario()).isEmpty());
        assertTrue(FormatadorTexto.erros(new Clinica()).isEmpty());
    }

    private String comDigitosDeCnpj(String base12) {
        int[] p1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] p2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        String parcial = base12 + digito(base12, p1);
        return parcial + digito(parcial, p2);
    }

    private int digito(String base, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += (base.charAt(i) - '0') * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}

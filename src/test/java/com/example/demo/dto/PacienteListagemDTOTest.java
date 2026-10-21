package com.example.demo.dto;

import com.example.demo.model.Paciente;
import com.example.demo.util.FusoBrasil;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PacienteListagemDTOTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 21);

    @Test
    void menorDeIdade_17AnosEMenor() {
        assertTrue(PacienteListagemDTO.menorDeIdade(HOJE.minusYears(17), HOJE));
    }

    @Test
    void menorDeIdade_17AnosE364DiasEMenor() {
        assertTrue(PacienteListagemDTO.menorDeIdade(HOJE.minusYears(18).plusDays(1), HOJE));
    }

    @Test
    void menorDeIdade_Exatamente18HojeNaoEMenor() {
        assertFalse(PacienteListagemDTO.menorDeIdade(HOJE.minusYears(18), HOJE));
    }

    @Test
    void menorDeIdade_AdultoNaoEMenor() {
        assertFalse(PacienteListagemDTO.menorDeIdade(HOJE.minusYears(40), HOJE));
    }

    @Test
    void menorDeIdade_SemDataNaoEMenor() {
        assertFalse(PacienteListagemDTO.menorDeIdade(null, HOJE));
    }

    @Test
    void menorDeIdade_NascidoEm29DeFevereiroSoCompleta18Em01DeMarcoEmAnoNaoBissexto() {
        LocalDate nascimento = LocalDate.of(2008, 2, 29);
        assertTrue(PacienteListagemDTO.menorDeIdade(nascimento, LocalDate.of(2026, 2, 27)));
        assertTrue(PacienteListagemDTO.menorDeIdade(nascimento, LocalDate.of(2026, 2, 28)));
        assertFalse(PacienteListagemDTO.menorDeIdade(nascimento, LocalDate.of(2026, 3, 1)));
    }

    @Test
    void menorDeIdade_FevereiroNormalNaoRecebeDiaExtra() {
        LocalDate nascimento = LocalDate.of(2008, 2, 28);
        assertTrue(PacienteListagemDTO.menorDeIdade(nascimento, LocalDate.of(2026, 2, 27)));
        assertFalse(PacienteListagemDTO.menorDeIdade(nascimento, LocalDate.of(2026, 2, 28)));
    }

    @Test
    void construtor_CalculaMenorDeIdadeEExpoeSoONomeDoResponsavel() {
        Paciente crianca = Paciente.builder().id("p1").nomeCompleto("Léo")
                .dataNascimento(FusoBrasil.hoje().minusYears(5))
                .responsavelNome("Joana").responsavelCpf("529.982.247-25").build();

        PacienteListagemDTO dto = new PacienteListagemDTO(crianca);

        assertTrue(dto.menorDeIdade());
        assertEquals("Joana", dto.responsavelNome());
        assertFalse(new PacienteListagemDTO(Paciente.builder().nomeCompleto("Sem data").build()).menorDeIdade());
    }

    @Test
    void json_MenorDeIdadeNomesDosCamposEDataNascimentoComoDataPura() {
        JsonMapper mapper = JsonMapper.builder().build();
        Paciente p = Paciente.builder().id("p1").nomeCompleto("Léo")
                .dataNascimento(LocalDate.of(2015, 3, 9)).responsavelNome("Joana").build();

        JsonNode listagem = mapper.valueToTree(new PacienteListagemDTO(p));
        assertTrue(listagem.get("menorDeIdade").isBoolean());
        assertTrue(listagem.get("menorDeIdade").asBoolean());
        assertEquals("Joana", listagem.get("responsavelNome").asString());
        assertFalse(listagem.has("responsavelCpf"));
        assertFalse(listagem.has("dataNascimento"));

        p.setResponsavelCpf("529.982.247-25");
        JsonNode detalhe = mapper.valueToTree(p);
        assertEquals("529.982.247-25", detalhe.get("responsavelCpf").asString());
        assertTrue(detalhe.get("dataNascimento").isString());
        assertEquals("2015-03-09", detalhe.get("dataNascimento").asString());
    }
}

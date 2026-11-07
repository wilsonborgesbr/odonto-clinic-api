package com.example.demo.model;

import com.example.demo.dto.VincularOdontogramaDTO;
import com.example.demo.enums.CondicaoDenteEnum;
import com.example.demo.enums.FaceDenteEnum;
import com.example.demo.util.DenteFdi;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProcedimentoFacesFdiTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private Procedimento comDente(String dente) {
        return Procedimento.builder().dente(dente).build();
    }

    private VincularOdontogramaDTO vinculo(String dente) {
        VincularOdontogramaDTO dto = new VincularOdontogramaDTO();
        dto.setDente(dente);
        return dto;
    }

    @ParameterizedTest
    @ValueSource(strings = {"11", "18", "21", "28", "31", "38", "41", "48", "51", "55", "61", "65", "71", "75", "81", "85"})
    void fdiValido_PermanentesEDeciduos(String dente) {
        assertTrue(DenteFdi.valido(dente));
        assertTrue(VALIDATOR.validate(comDente(dente)).isEmpty());
        assertTrue(VALIDATOR.validate(vinculo(dente)).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"10", "19", "09", "00", "49", "56", "66", "76", "86", "90", "99", "1", "111", "A1", "1A", "1 1", " 11", "11 ", "-11", "1.1"})
    void foraDoFdi_RejeitadoNoProcedimentoENoVinculo(String dente) {
        assertFalse(DenteFdi.valido(dente), dente);
        assertEquals(1, VALIDATOR.validate(comDente(dente)).size(), dente);
        assertEquals(1, VALIDATOR.validate(vinculo(dente)).size(), dente);
    }

    @Test
    void noProcedimentoOVazioOuNuloContinuaValido_NoVinculoOdenteEObrigatorio() {
        assertTrue(VALIDATOR.validate(comDente(null)).isEmpty());
        assertTrue(VALIDATOR.validate(comDente("")).isEmpty(), "o Frontend envia dente vazio quando não há dente");

        assertFalse(VALIDATOR.validate(vinculo(null)).isEmpty());
        assertFalse(VALIDATOR.validate(vinculo("")).isEmpty());
    }

    @Test
    void facesAusentesEmDocumentoAntigoValemComoListaVazia() {
        Procedimento antigo = new Procedimento();

        assertEquals(List.of(), antigo.getFaces());
        assertNull(antigo.facesInformadas(), "o bruto continua nulo, para o PUT saber que nada foi enviado");
    }

    @Test
    void setFaces_RemoveRepetidasENulasPreservandoAOrdem() {
        Procedimento p = new Procedimento();

        p.setFaces(Arrays.asList(FaceDenteEnum.OCLUSAL, null, FaceDenteEnum.MESIAL, FaceDenteEnum.OCLUSAL));

        assertEquals(List.of(FaceDenteEnum.OCLUSAL, FaceDenteEnum.MESIAL), p.getFaces());
        p.setFaces(null);
        assertNull(p.facesInformadas());
        p.setFaces(List.of());
        assertEquals(List.of(), p.facesInformadas());
    }

    @Test
    void admiteFaces_EspelhaOFrontend() {
        for (CondicaoDenteEnum c : List.of(CondicaoDenteEnum.CARIE, CondicaoDenteEnum.RESTAURACAO_SATISFATORIA,
                CondicaoDenteEnum.RESTAURACAO_INSATISFATORIA, CondicaoDenteEnum.FRATURA, CondicaoDenteEnum.SELANTE,
                CondicaoDenteEnum.CARIADO, CondicaoDenteEnum.RESTAURADO, CondicaoDenteEnum.FRATURADO)) {
            assertTrue(c.admiteFaces(), c.name());
        }
        for (CondicaoDenteEnum c : List.of(CondicaoDenteEnum.HIGIDO, CondicaoDenteEnum.AUSENTE, CondicaoDenteEnum.IMPLANTE,
                CondicaoDenteEnum.ENDODONTIA, CondicaoDenteEnum.PROTESE_FIXA, CondicaoDenteEnum.EXTRACAO_INDICADA)) {
            assertFalse(c.admiteFaces(), c.name());
        }
    }
}

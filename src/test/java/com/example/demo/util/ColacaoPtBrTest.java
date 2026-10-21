package com.example.demo.util;

import com.example.demo.repository.DentistaRepository;
import com.example.demo.repository.FuncionarioRepository;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class ColacaoPtBrTest {

    @Test
    void colacao_LocalePtComStrength1() {
        Document doc = ColacaoPtBr.COLLATION.toDocument();

        assertEquals("pt", doc.getString("locale"));
        assertEquals(1, doc.getInteger("strength"));
    }

    @Test
    void json_EquivaleAColacaoDoServico() {
        Document doc = Document.parse(ColacaoPtBr.JSON);

        assertEquals("pt", doc.getString("locale"));
        assertEquals(1, doc.getInteger("strength"));
    }

    @Test
    void listagemPaginadaDeDentistasUsaColacao() throws NoSuchMethodException {
        assertColacaoNaListagem(DentistaRepository.class);
    }

    @Test
    void listagemPaginadaDeFuncionariosUsaColacao() throws NoSuchMethodException {
        assertColacaoNaListagem(FuncionarioRepository.class);
    }

    private void assertColacaoNaListagem(Class<?> repositorio) throws NoSuchMethodException {
        Method metodo = repositorio.getMethod("findByClinicaIdAndAtivoTrue", String.class, Pageable.class);
        Query query = metodo.getAnnotation(Query.class);

        assertNotNull(query);
        assertEquals("{ 'clinicaId': ?0, 'ativo': true }", query.value());
        assertEquals(ColacaoPtBr.JSON, query.collation());
    }

    @Test
    void consultasNaoPaginadasEBuscasPorDocumentoNaoUsamColacao() throws NoSuchMethodException {
        assertNull(DentistaRepository.class.getMethod("findByClinicaIdAndAtivoTrue", String.class)
                .getAnnotation(Query.class));
        assertNull(DentistaRepository.class.getMethod("findByCroAndClinicaId", String.class, String.class)
                .getAnnotation(Query.class));
        assertNull(FuncionarioRepository.class.getMethod("findByCpfAndClinicaId", String.class, String.class)
                .getAnnotation(Query.class));
    }
}

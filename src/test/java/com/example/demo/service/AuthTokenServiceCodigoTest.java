package com.example.demo.service;

import com.example.demo.enums.TipoTokenAuthEnum;
import com.example.demo.model.AuthToken;
import com.example.demo.repository.AuthTokenRepository;
import com.example.demo.service.AuthTokenService.CodigoEmitido;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthTokenServiceCodigoTest {

    private static final Instant AGORA = Instant.parse("2026-10-26T13:00:00Z");
    private static final String SEGREDO = "segredo-de-teste-do-servidor";

    @Mock
    private AuthTokenRepository repository;
    @Mock
    private MongoTemplate mongoTemplate;

    private AuthTokenService service(String segredo) {
        return new AuthTokenService(repository, mongoTemplate, Clock.fixed(AGORA, ZoneOffset.UTC), segredo);
    }

    private CodigoEmitido emitir(AuthTokenService service) {
        return service.emitirComCodigo("u1", "c1", TipoTokenAuthEnum.RESET_SENHA, Duration.ofMinutes(30),
                Duration.ofMinutes(15), "1.2.3.4");
    }

    private AuthToken gravado() {
        ArgumentCaptor<AuthToken> captor = ArgumentCaptor.forClass(AuthToken.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private static String sha256(String texto) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void emitirComCodigo_SempreSeisDigitosInclusiveComZerosAEsquerda() {
        AuthTokenService service = service(SEGREDO);
        boolean viuZeroAEsquerda = false;
        for (int i = 0; i < 3000; i++) {
            String codigo = emitir(service).codigo();
            assertTrue(codigo.matches("\\d{6}"), codigo);
            viuZeroAEsquerda |= codigo.startsWith("0");
        }
        assertTrue(viuZeroAEsquerda, "em 3000 códigos deve aparecer ao menos um começando com zero");
    }

    @Test
    void emitirComCodigo_GravaSoHmacComSalDe16BytesNuncaOCodigoNemSha256Puro() throws Exception {
        CodigoEmitido emitido = emitir(service(SEGREDO));

        AuthToken salvo = gravado();
        assertEquals(64, salvo.getCodigoHash().length());
        assertTrue(salvo.getCodigoHash().matches("[0-9a-f]{64}"));
        assertEquals(32, salvo.getCodigoSalt().length(), "16 bytes em hexadecimal");
        assertNotEquals(emitido.codigo(), salvo.getCodigoHash());
        assertNotEquals(sha256(emitido.codigo()), salvo.getCodigoHash());
        assertNotEquals(sha256(salvo.getCodigoSalt() + emitido.codigo()), salvo.getCodigoHash());
        assertEquals(AGORA.plus(Duration.ofMinutes(15)), salvo.getCodigoExpiraEm());
        assertEquals(AGORA.plus(Duration.ofMinutes(30)), salvo.getExpiraEm());
        assertEquals(0, salvo.getCodigoTentativas());
        assertNull(salvo.getCodigoBloqueado());
        assertEquals(AuthTokenService.hash(emitido.token()), salvo.getTokenHash());
        verify(repository).deleteByUserIdAndTipoAndUsadoEmIsNull("u1", TipoTokenAuthEnum.RESET_SENHA);
    }

    @Test
    void emitirComCodigo_CadaEmissaoTemSalProprio() {
        AuthTokenService service = service(SEGREDO);
        emitir(service);
        emitir(service);

        ArgumentCaptor<AuthToken> captor = ArgumentCaptor.forClass(AuthToken.class);
        verify(repository, times(2)).save(captor.capture());
        assertNotEquals(captor.getAllValues().get(0).getCodigoSalt(), captor.getAllValues().get(1).getCodigoSalt());
    }

    @Test
    void codigoEmitido_ToStringNaoVazaOCodigo() {
        CodigoEmitido emitido = new CodigoEmitido("tok", "849201", AGORA);

        assertFalse(emitido.toString().contains("849201"));
        assertFalse(emitido.toString().contains("tok"));
    }

    @Test
    void consumirPorCodigo_Correto_QueimaOTokenInteiroEmUmaOperacaoAtomica() {
        AuthTokenService service = service(SEGREDO);
        CodigoEmitido emitido = emitir(service);
        AuthToken salvo = gravado();
        salvo.setId("t1");
        when(mongoTemplate.findOne(any(Query.class), eq(AuthToken.class))).thenReturn(salvo);
        AuthToken consumido = AuthToken.builder().id("t1").usadoEm(AGORA).build();
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class), any(FindAndModifyOptions.class), eq(AuthToken.class)))
                .thenReturn(consumido);

        Optional<AuthToken> r = service.consumirPorCodigo("u1", TipoTokenAuthEnum.RESET_SENHA, emitido.codigo());

        assertTrue(r.isPresent());
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findAndModify(query.capture(), update.capture(), any(FindAndModifyOptions.class), eq(AuthToken.class));
        assertEquals(new Document("$set", new Document("usadoEm", AGORA)), update.getValue().getUpdateObject());
        Document filtro = query.getValue().getQueryObject();
        assertEquals("t1", filtro.get("id"));
        assertNull(filtro.get("usadoEm"));
        assertEquals(new Document("$ne", true), filtro.get("codigoBloqueado"));
        assertEquals(new Document("$lt", 5), filtro.get("codigoTentativas"));
        assertEquals(new Document("$gt", AGORA), filtro.get("codigoExpiraEm"));
    }

    @Test
    void consumirPorCodigo_Errado_SomaUmaTentativaComIncAtomicoENaoBloqueiaAntesDaQuinta() {
        AuthTokenService service = service(SEGREDO);
        CodigoEmitido emitido = emitir(service);
        AuthToken salvo = gravado();
        salvo.setId("t1");
        when(mongoTemplate.findOne(any(Query.class), eq(AuthToken.class))).thenReturn(salvo);
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class), any(FindAndModifyOptions.class), eq(AuthToken.class)))
                .thenReturn(AuthToken.builder().id("t1").codigoTentativas(4).build());
        String errado = emitido.codigo().equals("000000") ? "000001" : "000000";

        Optional<AuthToken> r = service.consumirPorCodigo("u1", TipoTokenAuthEnum.RESET_SENHA, errado);

        assertTrue(r.isEmpty());
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).findAndModify(any(Query.class), update.capture(), any(FindAndModifyOptions.class), eq(AuthToken.class));
        assertEquals(new Document("$inc", new Document("codigoTentativas", 1)), update.getValue().getUpdateObject());
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(UpdateDefinition.class), eq(AuthToken.class));
    }

    @Test
    void consumirPorCodigo_QuintoErro_MarcaCodigoBloqueado() {
        AuthTokenService service = service(SEGREDO);
        CodigoEmitido emitido = emitir(service);
        AuthToken salvo = gravado();
        salvo.setId("t1");
        when(mongoTemplate.findOne(any(Query.class), eq(AuthToken.class))).thenReturn(salvo);
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class), any(FindAndModifyOptions.class), eq(AuthToken.class)))
                .thenReturn(AuthToken.builder().id("t1").codigoTentativas(5).build());
        String errado = emitido.codigo().equals("000000") ? "000001" : "000000";

        assertTrue(service.consumirPorCodigo("u1", TipoTokenAuthEnum.RESET_SENHA, errado).isEmpty());

        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).updateFirst(any(Query.class), update.capture(), eq(AuthToken.class));
        assertEquals(new Document("$set", new Document("codigoBloqueado", true)), update.getValue().getUpdateObject());
    }

    @Test
    void consumirPorCodigo_SemTokenPendenteComCodigo_DevolveVazioSemEscreverNada() {
        when(mongoTemplate.findOne(any(Query.class), eq(AuthToken.class))).thenReturn(null);

        assertTrue(service(SEGREDO).consumirPorCodigo("u1", TipoTokenAuthEnum.RESET_SENHA, "123456").isEmpty());

        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(UpdateDefinition.class), any(FindAndModifyOptions.class), any());
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(UpdateDefinition.class), any(Class.class));
    }

    @Test
    void consumirPorCodigo_ProcuraSoTokenDoUsuarioNaoUsadoComCodigoEDentroDoPrazo() {
        when(mongoTemplate.findOne(any(Query.class), eq(AuthToken.class))).thenReturn(null);

        service(SEGREDO).consumirPorCodigo("u1", TipoTokenAuthEnum.RESET_SENHA, "123456");

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findOne(query.capture(), eq(AuthToken.class));
        Document filtro = query.getValue().getQueryObject();
        assertEquals("u1", filtro.get("userId"));
        assertEquals(TipoTokenAuthEnum.RESET_SENHA, filtro.get("tipo"));
        assertNull(filtro.get("usadoEm"));
        assertEquals(new Document("$ne", null), filtro.get("codigoHash"));
        assertEquals(new Document("$gt", AGORA), filtro.get("expiraEm"));
    }

    @Test
    void hmacDependeDaChaveDoServidor_OutraChaveNaoConfereOMesmoCodigo() {
        AuthTokenService dona = service("chave-A-do-servidor");
        CodigoEmitido emitido = emitir(dona);
        AuthToken salvo = gravado();
        salvo.setId("t1");
        when(mongoTemplate.findOne(any(Query.class), eq(AuthToken.class))).thenReturn(salvo);
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class), any(FindAndModifyOptions.class), eq(AuthToken.class)))
                .thenReturn(AuthToken.builder().id("t1").codigoTentativas(1).build());

        service("chave-B-de-um-atacante").consumirPorCodigo("u1", TipoTokenAuthEnum.RESET_SENHA, emitido.codigo());

        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).findAndModify(any(Query.class), update.capture(), any(FindAndModifyOptions.class), eq(AuthToken.class));
        assertEquals(new Document("$inc", new Document("codigoTentativas", 1)), update.getValue().getUpdateObject(),
                "com outra chave o código certo conta como erro");
    }

    @Test
    void simularConferencia_NaoToca_NoBanco() {
        service(SEGREDO).simularConferencia("123456");
        service(SEGREDO).simularConferencia(null);

        verifyNoInteractions(mongoTemplate, repository);
    }
}

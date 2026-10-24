package com.example.demo.service;

import com.example.demo.enums.TipoTokenAuthEnum;
import com.example.demo.model.AuthToken;
import com.example.demo.repository.AuthTokenRepository;
import com.example.demo.service.AuthTokenService.TokenEmitido;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthTokenServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-22T13:00:00Z");
    private static final String SEGREDO = "segredo-de-teste-do-servidor";

    @Mock
    private AuthTokenRepository repository;

    @Mock
    private MongoTemplate mongoTemplate;

    private AuthTokenService service() {
        return new AuthTokenService(repository, mongoTemplate, Clock.fixed(AGORA, ZoneOffset.UTC), SEGREDO);
    }

    private AuthToken salvo(TokenEmitido emitido) {
        ArgumentCaptor<AuthToken> captor = ArgumentCaptor.forClass(AuthToken.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void emitir_TokenDe32BytesEmBase64UrlSemPadding() {
        TokenEmitido emitido = service().emitir("u1", "c1", TipoTokenAuthEnum.RESET_SENHA, Duration.ofMinutes(30), "1.2.3.4");

        assertEquals(43, emitido.token().length());
        assertTrue(emitido.token().matches("[A-Za-z0-9_-]{43}"));
        assertEquals(32, java.util.Base64.getUrlDecoder().decode(emitido.token()).length);
    }

    @Test
    void emitir_TokenNuncaVaiEmClaroParaOBanco() {
        TokenEmitido emitido = service().emitir("u1", "c1", TipoTokenAuthEnum.RESET_SENHA, Duration.ofMinutes(30), "1.2.3.4");

        AuthToken gravado = salvo(emitido);
        assertNotEquals(emitido.token(), gravado.getTokenHash());
        assertEquals(AuthTokenService.hash(emitido.token()), gravado.getTokenHash());
        assertTrue(gravado.getTokenHash().matches("[0-9a-f]{64}"));
        assertFalse(gravado.toString().contains(emitido.token()), "o token em claro não pode estar em nenhum campo");
    }

    @Test
    void emitir_GravaDadosDoTokenEExpiracao() {
        TokenEmitido emitido = service().emitir("u1", "c1", TipoTokenAuthEnum.CONFIRMAR_EMAIL_DONO, Duration.ofHours(48), "1.2.3.4");

        AuthToken gravado = salvo(emitido);
        assertEquals("u1", gravado.getUserId());
        assertEquals("c1", gravado.getClinicaId());
        assertEquals(TipoTokenAuthEnum.CONFIRMAR_EMAIL_DONO, gravado.getTipo());
        assertEquals(AGORA, gravado.getCriadoEm());
        assertEquals(AGORA.plus(Duration.ofHours(48)), gravado.getExpiraEm());
        assertEquals(gravado.getExpiraEm(), emitido.expiraEm());
        assertNull(gravado.getUsadoEm());
        assertEquals("1.2.3.4", gravado.getIpSolicitacao());
    }

    @Test
    void emitir_InvalidaPendentesDoMesmoUsuarioETipoAntesDeSalvar() {
        service().emitir("u1", "c1", TipoTokenAuthEnum.RESET_SENHA, Duration.ofMinutes(30), null);

        InOrder ordem = inOrder(repository);
        ordem.verify(repository).deleteByUserIdAndTipoAndUsadoEmIsNull("u1", TipoTokenAuthEnum.RESET_SENHA);
        ordem.verify(repository).save(any(AuthToken.class));
    }

    @Test
    void emitir_TokensDistintosACadaChamada() {
        AuthTokenService servico = service();
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            tokens.add(servico.emitir("u1", "c1", TipoTokenAuthEnum.RESET_SENHA, Duration.ofMinutes(1), null).token());
        }
        assertEquals(200, tokens.size());
    }

    private AuthToken registro(TipoTokenAuthEnum tipo, Instant expiraEm, Instant usadoEm) {
        return AuthToken.builder().id("t1").userId("u1").clinicaId("c1").tipo(tipo)
                .tokenHash(AuthTokenService.hash("meu-token")).expiraEm(expiraEm).usadoEm(usadoEm).build();
    }

    @Test
    void validar_TokenValidoRetornaORegistroSemConsumir() {
        AuthToken ok = registro(TipoTokenAuthEnum.RESET_SENHA, AGORA.plusSeconds(60), null);
        when(repository.findByTokenHash(AuthTokenService.hash("meu-token"))).thenReturn(Optional.of(ok));

        assertSame(ok, service().validar("meu-token", TipoTokenAuthEnum.RESET_SENHA));
        verify(repository, never()).save(any());
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void validar_ConsultaPeloHashENuncaPeloTokenEmClaro() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service().validar("meu-token", TipoTokenAuthEnum.RESET_SENHA));

        verify(repository).findByTokenHash(AuthTokenService.hash("meu-token"));
        verify(repository, never()).findByTokenHash("meu-token");
    }

    @Test
    void validar_TokenJaUsadoERejeitado() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(
                registro(TipoTokenAuthEnum.RESET_SENHA, AGORA.plusSeconds(60), AGORA.minusSeconds(5))));

        assertInvalido(() -> service().validar("meu-token", TipoTokenAuthEnum.RESET_SENHA));
    }

    @Test
    void validar_TokenExpiradoERejeitado() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(
                registro(TipoTokenAuthEnum.RESET_SENHA, AGORA.minusSeconds(1), null)));

        assertInvalido(() -> service().validar("meu-token", TipoTokenAuthEnum.RESET_SENHA));
    }

    @Test
    void validar_ExpirandoExatamenteAgoraJaEstaExpirado() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(
                registro(TipoTokenAuthEnum.RESET_SENHA, AGORA, null)));

        assertInvalido(() -> service().validar("meu-token", TipoTokenAuthEnum.RESET_SENHA));
    }

    @Test
    void validar_TipoDiferenteERejeitado() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(
                registro(TipoTokenAuthEnum.ATIVACAO_CONTA, AGORA.plusSeconds(60), null)));

        assertInvalido(() -> service().validar("meu-token", TipoTokenAuthEnum.RESET_SENHA));
    }

    @Test
    void validar_TokenDesconhecidoNuloOuVazioDaMesmaMensagemSemConsultarOBanco() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());
        assertInvalido(() -> service().validar("desconhecido", TipoTokenAuthEnum.RESET_SENHA));

        clearInvocations(repository);
        assertInvalido(() -> service().validar(null, TipoTokenAuthEnum.RESET_SENHA));
        assertInvalido(() -> service().validar("  ", TipoTokenAuthEnum.RESET_SENHA));
        verifyNoInteractions(repository);
    }

    @Test
    void consumir_QueimaOTokenDeFormaAtomicaFiltrandoPorHashTipoNaoUsadoENaoExpirado() {
        AuthToken queimado = registro(TipoTokenAuthEnum.RESET_SENHA, AGORA.plusSeconds(60), AGORA);
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(AuthToken.class))).thenReturn(queimado);

        AuthToken resultado = service().consumir("meu-token", TipoTokenAuthEnum.RESET_SENHA);

        assertSame(queimado, resultado);
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).findAndModify(query.capture(), update.capture(), any(FindAndModifyOptions.class), eq(AuthToken.class));
        Document filtro = query.getValue().getQueryObject();
        assertEquals(AuthTokenService.hash("meu-token"), filtro.get("tokenHash"));
        assertEquals(TipoTokenAuthEnum.RESET_SENHA, filtro.get("tipo"));
        assertNull(filtro.get("usadoEm"));
        assertTrue(filtro.containsKey("usadoEm"));
        assertEquals(AGORA, ((Document) filtro.get("expiraEm")).get("$gt"));
        assertEquals(AGORA, ((Document) update.getValue().getUpdateObject().get("$set")).get("usadoEm"));
        assertFalse(filtro.toString().contains("meu-token"), "o token em claro não vai na consulta");
    }

    @Test
    void consumir_ReusoERejeitadoQuandoNenhumDocumentoCasa() {
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(AuthToken.class))).thenReturn(null);

        assertInvalido(() -> service().consumir("meu-token", TipoTokenAuthEnum.RESET_SENHA));
    }

    @Test
    void consumir_TokenNuloOuVazioNaoChegaAoBanco() {
        assertInvalido(() -> service().consumir(null, TipoTokenAuthEnum.RESET_SENHA));
        assertInvalido(() -> service().consumir("", TipoTokenAuthEnum.RESET_SENHA));
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void hash_Sha256EmHexEDeterministico() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", AuthTokenService.hash("abc"));
    }

    private void assertInvalido(org.junit.jupiter.api.function.Executable acao) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, acao);
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals(AuthTokenService.MENSAGEM_INVALIDO, ex.getReason());
    }
}

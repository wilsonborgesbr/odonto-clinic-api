package com.example.demo.service;

import com.example.demo.enums.TipoTokenAuthEnum;
import com.example.demo.model.AuthToken;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

// Usa um banco temporário próprio (derrubado ao final); é ignorado quando não há Mongo local.
@EnabledIf("mongoLocalDisponivel")
@SpringBootTest(properties = "spring.mongodb.uri=mongodb://localhost:27017/tmp_teste_auth_tokens")
class AuthTokenServiceMongoLocalTest {

    static boolean mongoLocalDisponivel() {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("localhost", 27017), 500);
            return true;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    @Autowired
    AuthTokenService service;

    @Autowired
    MongoTemplate mongo;

    @AfterAll
    static void derrubarBanco(@Autowired MongoTemplate mongo) {
        mongo.getDb().drop();
    }

    private void prepararBanco() {
        assertEquals("tmp_teste_auth_tokens", mongo.getDb().getName());
        mongo.dropCollection(AuthToken.class);
        mongo.indexOps(AuthToken.class).createIndex(new Index().on("tokenHash", Sort.Direction.ASC).unique());
    }

    @Test
    void ciclo_TokenSoExisteEmHashUsoUnicoEExpiracaoNoBancoReal() {
        prepararBanco();
        var emitido = service.emitir("u1", "c1", TipoTokenAuthEnum.RESET_SENHA, Duration.ofMinutes(30), "127.0.0.1");

        for (Document doc : mongo.getCollection("auth_tokens").find()) {
            assertFalse(doc.toJson().contains(emitido.token()), "token em claro encontrado no banco");
            assertEquals(AuthTokenService.hash(emitido.token()), doc.getString("tokenHash"));
        }

        assertEquals("u1", service.validar(emitido.token(), TipoTokenAuthEnum.RESET_SENHA).getUserId());
        AuthToken usado = service.consumir(emitido.token(), TipoTokenAuthEnum.RESET_SENHA);
        assertNotNull(usado.getUsadoEm());
        assertThrows(ResponseStatusException.class, () -> service.consumir(emitido.token(), TipoTokenAuthEnum.RESET_SENHA));
        assertThrows(ResponseStatusException.class, () -> service.validar(emitido.token(), TipoTokenAuthEnum.RESET_SENHA));
    }

    @Test
    void tokenExpiradoERejeitado() {
        prepararBanco();
        var expirado = service.emitir("u1", "c1", TipoTokenAuthEnum.RESET_SENHA, Duration.ofSeconds(-5), null);

        assertThrows(ResponseStatusException.class, () -> service.validar(expirado.token(), TipoTokenAuthEnum.RESET_SENHA));
        assertThrows(ResponseStatusException.class, () -> service.consumir(expirado.token(), TipoTokenAuthEnum.RESET_SENHA));
    }

    @Test
    void tipoErradoNaoQueimaOTokenENovaEmissaoInvalidaAAnterior() {
        prepararBanco();
        var primeiro = service.emitir("u1", "c1", TipoTokenAuthEnum.RESET_SENHA, Duration.ofMinutes(30), null);

        assertThrows(ResponseStatusException.class, () -> service.consumir(primeiro.token(), TipoTokenAuthEnum.ATIVACAO_CONTA));
        assertDoesNotThrow(() -> service.validar(primeiro.token(), TipoTokenAuthEnum.RESET_SENHA));

        var segundo = service.emitir("u1", "c1", TipoTokenAuthEnum.RESET_SENHA, Duration.ofMinutes(30), null);
        assertThrows(ResponseStatusException.class, () -> service.consumir(primeiro.token(), TipoTokenAuthEnum.RESET_SENHA));
        assertDoesNotThrow(() -> service.consumir(segundo.token(), TipoTokenAuthEnum.RESET_SENHA));
    }

    @Test
    void consumoSimultaneoDoMesmoTokenSoPassaUmaVez() throws Exception {
        prepararBanco();
        var emitido = service.emitir("u1", "c1", TipoTokenAuthEnum.RESET_SENHA, Duration.ofMinutes(30), null);
        AtomicInteger sucessos = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        List<Callable<Void>> tarefas = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            tarefas.add(() -> {
                try {
                    service.consumir(emitido.token(), TipoTokenAuthEnum.RESET_SENHA);
                    sucessos.incrementAndGet();
                } catch (ResponseStatusException ignorada) {
                    // rejeitado: esperado para todos menos um
                }
                return null;
            });
        }
        for (Future<Void> f : pool.invokeAll(tarefas)) {
            f.get();
        }
        pool.shutdown();

        assertEquals(1, sucessos.get());
    }
}

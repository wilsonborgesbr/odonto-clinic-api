package com.example.demo.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitador em memória (janela deslizante) para endpoints públicos. Vale para uma instância só
 * (a VPS atual); ao escalar para várias instâncias, trocar por um armazenamento compartilhado.
 */
@Service
public class RateLimitService {

    private static final int LIMITE_CHAVES_ANTES_DA_LIMPEZA = 10_000;

    private final Map<String, Deque<Long>> eventos = new ConcurrentHashMap<>();
    private final Clock clock;

    @Autowired
    public RateLimitService() {
        this(Clock.systemUTC());
    }

    RateLimitService(Clock clock) {
        this.clock = clock;
    }

    /** Registra um evento na chave e diz se ainda cabe no limite (true = permitido). */
    public boolean registrarSePermitido(String chave, int maximo, Duration janela) {
        long agora = clock.millis();
        Deque<Long> fila = eventos.computeIfAbsent(chave, k -> new ArrayDeque<>());
        synchronized (fila) {
            descartarAntigos(fila, agora - janela.toMillis());
            if (fila.size() >= maximo) {
                return false;
            }
            fila.addLast(agora);
        }
        if (eventos.size() > LIMITE_CHAVES_ANTES_DA_LIMPEZA) {
            limpar(janela);
        }
        return true;
    }

    /** Só consulta, sem registrar: true se a chave já atingiu o limite. */
    public boolean atingiuLimite(String chave, int maximo, Duration janela) {
        Deque<Long> fila = eventos.get(chave);
        if (fila == null) {
            return false;
        }
        synchronized (fila) {
            descartarAntigos(fila, clock.millis() - janela.toMillis());
            return fila.size() >= maximo;
        }
    }

    /** Registra um evento (ex.: tentativa de login que falhou). */
    public void registrar(String chave, Duration janela) {
        long agora = clock.millis();
        Deque<Long> fila = eventos.computeIfAbsent(chave, k -> new ArrayDeque<>());
        synchronized (fila) {
            descartarAntigos(fila, agora - janela.toMillis());
            fila.addLast(agora);
        }
        if (eventos.size() > LIMITE_CHAVES_ANTES_DA_LIMPEZA) {
            limpar(janela);
        }
    }

    public void esquecer(String chave) {
        eventos.remove(chave);
    }

    private void limpar(Duration janela) {
        long corte = clock.millis() - janela.toMillis();
        eventos.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                descartarAntigos(e.getValue(), corte);
                return e.getValue().isEmpty();
            }
        });
    }

    private void descartarAntigos(Deque<Long> fila, long corte) {
        while (!fila.isEmpty() && fila.peekFirst() <= corte) {
            fila.pollFirst();
        }
    }
}

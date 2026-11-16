package com.example.demo.googlecalendar;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Google em memória para os testes de integração: contas, agendas, eventos, canais, tokens e o histórico de
 * mudanças que alimenta o syncToken. Permite simular o que o usuário faz direto no Google (editar, excluir,
 * criar evento pessoal, copiar evento) e injetar falhas.
 *
 * <p>Códigos de autorização têm a forma {@code code:<email>} (ou {@code code:<email>:semrefresh} /
 * {@code code:<email>:semagenda}).
 */
public class FakeGoogleCalendarApi implements GoogleCalendarApi {

    public static final String ESCOPO_OK = "openid https://www.googleapis.com/auth/userinfo.email "
            + "https://www.googleapis.com/auth/calendar.events";

    public static class Armazenado {
        public final String conta;
        public final String id;
        public boolean cancelado;
        public LocalDateTime inicio;
        public LocalDateTime fim;
        public String titulo;
        public String descricao;
        public Map<String, String> privadas = new LinkedHashMap<>();

        Armazenado(String conta, String id) {
            this.conta = conta;
            this.id = id;
        }
    }

    private record Mudanca(long seq, String conta, String eventId) {}

    private final Map<String, Armazenado> eventos = new LinkedHashMap<>();
    private final List<Mudanca> mudancas = new ArrayList<>();
    private final List<String> chamadas = new ArrayList<>();
    private final Map<String, String> canais = new LinkedHashMap<>(); // channelId -> conta
    private long seq = 0;
    private long contadorTokens = 0;
    private long tokenInvalidoAte = 0;

    private final java.util.Set<String> contasRevogadas = new java.util.HashSet<>();
    private RuntimeException falhaEscrita;
    private RuntimeException falhaListagem;
    private final Map<String, RuntimeException> falhasExclusao = new LinkedHashMap<>();
    private Supplier<Instant> relogio = Instant::now;

    // ───────────── controle dos testes ─────────────

    public synchronized void limpar() {
        falhaEscrita = null;
        falhaListagem = null;
        falhasExclusao.clear();
        contasRevogadas.clear();
        relogio = Instant::now;
    }

    public synchronized void definirRelogio(Supplier<Instant> relogio) {
        this.relogio = relogio;
    }

    public synchronized void falharEscritas(RuntimeException e) { this.falhaEscrita = e; }
    public synchronized void falharListagens(RuntimeException e) { this.falhaListagem = e; }
    public synchronized void falharExclusaoDe(String eventId, RuntimeException e) { falhasExclusao.put(eventId, e); }
    public synchronized void revogarConta(String email) { contasRevogadas.add(email); }

    /** Todos os syncTokens emitidos até agora passam a responder 410. */
    public synchronized void expirarSyncTokens() { tokenInvalidoAte = seq + 1; seq++; }

    public synchronized List<String> chamadas() { return new ArrayList<>(chamadas); }

    public synchronized long contar(String prefixo) {
        return chamadas.stream().filter(c -> c.startsWith(prefixo)).count();
    }

    public synchronized Armazenado evento(String eventId) { return eventos.get(eventId); }

    public synchronized List<Armazenado> eventosDa(String conta) {
        return eventos.values().stream().filter(e -> e.conta.equals(conta)).toList();
    }

    public synchronized List<Armazenado> eventosAtivosDa(String conta) {
        return eventos.values().stream().filter(e -> e.conta.equals(conta) && !e.cancelado).toList();
    }

    /** O usuário muda o horário do evento no Google. */
    public synchronized void usuarioEditaHorario(String eventId, LocalDateTime inicio, LocalDateTime fim) {
        Armazenado e = eventos.get(eventId);
        e.inicio = inicio;
        e.fim = fim;
        registrar(e);
    }

    /** O usuário exclui o evento no Google. */
    public synchronized void usuarioExclui(String eventId) {
        Armazenado e = eventos.get(eventId);
        e.cancelado = true;
        registrar(e);
    }

    /** Evento pessoal do dentista, sem nenhuma relação com o Bokka. */
    public synchronized String usuarioCriaEventoPessoal(String conta, LocalDateTime inicio, LocalDateTime fim) {
        Armazenado e = new Armazenado(conta, "pessoal" + (++seq));
        e.inicio = inicio;
        e.fim = fim;
        e.titulo = "Dentista pessoal";
        eventos.put(e.id, e);
        registrar(e);
        return e.id;
    }

    /** O usuário duplica um evento do Bokka: a cópia leva as mesmas propriedades privadas, com outro id. */
    public synchronized String usuarioCopiaEvento(String eventId, LocalDateTime novoInicio, LocalDateTime novoFim) {
        Armazenado origem = eventos.get(eventId);
        Armazenado copia = new Armazenado(origem.conta, "copia" + (++seq));
        copia.inicio = novoInicio;
        copia.fim = novoFim;
        copia.titulo = origem.titulo;
        copia.privadas = new LinkedHashMap<>(origem.privadas);
        eventos.put(copia.id, copia);
        registrar(copia);
        return copia.id;
    }

    private void registrar(Armazenado e) {
        mudancas.add(new Mudanca(++seq, e.conta, e.id));
    }

    // ───────────── OAuth ─────────────

    @Override
    public synchronized Tokens trocarCodigo(String codigo) {
        chamadas.add("trocarCodigo");
        if (!codigo.startsWith("code:")) {
            throw new GoogleApiException(400, "invalid_grant");
        }
        String[] partes = codigo.split(":");
        String email = partes[1];
        String modo = partes.length > 2 ? partes[2] : "";
        String scope = modo.equals("semagenda") ? "openid https://www.googleapis.com/auth/userinfo.email" : ESCOPO_OK;
        return new Tokens(novoAccess(email), modo.equals("semrefresh") ? null : "ref:" + email,
                relogio.get().plusSeconds(3600), scope);
    }

    @Override
    public synchronized Tokens renovar(String refreshToken) {
        chamadas.add("renovar");
        String email = refreshToken.substring("ref:".length());
        if (contasRevogadas.contains(email)) {
            throw new GoogleApiException(400, "invalid_grant");
        }
        return new Tokens(novoAccess(email), null, relogio.get().plusSeconds(3600), ESCOPO_OK);
    }

    @Override
    public synchronized void revogar(String token) {
        chamadas.add("revogar:" + token);
    }

    @Override
    public synchronized String emailDaConta(String accessToken) {
        return contaDe(accessToken);
    }

    private String novoAccess(String email) {
        return "acc:" + email + ":" + (++contadorTokens);
    }

    private static String contaDe(String accessToken) {
        return accessToken.split(":")[1];
    }

    // ───────────── eventos ─────────────

    @Override
    public synchronized void inserirEvento(String accessToken, String calendarId, Evento evento) {
        chamadas.add("insert:" + evento.id());
        if (falhaEscrita != null) throw falhaEscrita;
        if (eventos.containsKey(evento.id())) {
            throw new GoogleApiException(409, "duplicate");
        }
        Armazenado e = new Armazenado(contaDe(accessToken), evento.id());
        preencher(e, evento);
        eventos.put(e.id, e);
        registrar(e);
    }

    @Override
    public synchronized void atualizarEvento(String accessToken, String calendarId, Evento evento) {
        chamadas.add("update:" + evento.id());
        if (falhaEscrita != null) throw falhaEscrita;
        Armazenado e = eventos.get(evento.id());
        if (e == null) {
            throw new GoogleApiException(404, "notFound");
        }
        preencher(e, evento);
        e.cancelado = false;
        registrar(e);
    }

    private static void preencher(Armazenado e, Evento evento) {
        e.titulo = evento.titulo();
        e.descricao = evento.descricao();
        e.inicio = evento.inicio();
        e.fim = evento.fim();
        e.privadas = new LinkedHashMap<>(evento.propriedadesPrivadas());
    }

    @Override
    public synchronized void excluirEvento(String accessToken, String calendarId, String eventId) {
        chamadas.add("delete:" + eventId);
        if (falhasExclusao.containsKey(eventId)) throw falhasExclusao.get(eventId);
        if (falhaEscrita != null) throw falhaEscrita;
        Armazenado e = eventos.get(eventId);
        if (e == null || e.cancelado) {
            return;
        }
        e.cancelado = true;
        registrar(e);
    }

    @Override
    public synchronized PaginaEventos listarEventos(String accessToken, String calendarId, String syncToken,
                                                    String pagina, Instant aPartirDe) {
        chamadas.add("listar:" + (syncToken == null ? "inicial" : syncToken));
        if (falhaListagem != null) throw falhaListagem;
        String conta = contaDe(accessToken);
        if (syncToken == null) {
            return new PaginaEventos(List.of(), null, "tok-" + seq);
        }
        long desde = Long.parseLong(syncToken.substring("tok-".length()));
        if (desde < tokenInvalidoAte) {
            throw new GoogleApiException(410, "fullSyncRequired");
        }
        List<EventoRemoto> resultado = new ArrayList<>();
        java.util.Set<String> vistos = new java.util.HashSet<>();
        for (Mudanca m : mudancas) {
            if (m.seq() > desde && m.conta().equals(conta) && vistos.add(m.eventId())) {
                Armazenado e = eventos.get(m.eventId());
                resultado.add(e.cancelado
                        // evento excluído: o Google manda só id e status, sem propriedades
                        ? new EventoRemoto(e.id, true, null, null, Map.of(), null)
                        : new EventoRemoto(e.id, false, e.inicio, e.fim, new LinkedHashMap<>(e.privadas), e.titulo));
            }
        }
        return new PaginaEventos(resultado, null, "tok-" + seq);
    }

    // ───────────── canais ─────────────

    @Override
    public synchronized Canal criarCanal(String accessToken, String calendarId, String channelId, String endereco,
                                         String tokenCanal, Instant expiracao) {
        chamadas.add("criarCanal:" + channelId);
        if (falhaEscrita != null) throw falhaEscrita;
        canais.put(channelId, contaDe(accessToken));
        return new Canal(channelId, "res-" + channelId, expiracao);
    }

    @Override
    public synchronized void pararCanal(String accessToken, String channelId, String resourceId) {
        chamadas.add("pararCanal:" + channelId);
        canais.remove(channelId);
    }

    public synchronized boolean canalAberto(String channelId) {
        return canais.containsKey(channelId);
    }
}

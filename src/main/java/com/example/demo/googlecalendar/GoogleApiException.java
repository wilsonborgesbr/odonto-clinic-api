package com.example.demo.googlecalendar;

/** Falha numa chamada às APIs do Google. A mensagem nunca carrega tokens nem o corpo da resposta. */
public class GoogleApiException extends RuntimeException {

    private final int status;
    private final String motivo;

    public GoogleApiException(int status, String motivo) {
        super("Google API " + status + (motivo == null ? "" : " (" + motivo + ")"));
        this.status = status;
        this.motivo = motivo;
    }

    public GoogleApiException(int status, String motivo, String detalhe) {
        super("Google API " + status + (motivo == null ? "" : " (" + motivo + ")")
                + (detalhe == null ? "" : ": " + (detalhe.length() > 200 ? detalhe.substring(0, 200) : detalhe)));
        this.status = status;
        this.motivo = motivo;
    }

    public GoogleApiException(String mensagem, Throwable causa) {
        super(mensagem, causa);
        this.status = 0;
        this.motivo = null;
    }

    public int status() { return status; }
    public String motivo() { return motivo; }

    /** O refresh token foi revogado ou expirou: o usuário precisa reconectar. */
    public boolean concessaoInvalida() {
        return "invalid_grant".equals(motivo);
    }

    public boolean naoAutorizado() { return status == 401; }
    public boolean proibido() { return status == 403; }
    public boolean naoEncontrado() { return status == 404; }
    public boolean conflito() { return status == 409; }
    /** 410: evento apagado de vez ou syncToken vencido. */
    public boolean desaparecido() { return status == 410; }

    /** Vale tentar de novo: limite de uso, erro do servidor ou falha de rede. */
    public boolean transitorio() {
        return status == 0 || status == 429 || status >= 500;
    }
}

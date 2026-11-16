package com.example.demo.googlecalendar;

/**
 * Evento de domínio publicado quando um agendamento muda. O ouvinte assíncrono relê o estado atual do banco,
 * então a ordem em que os eventos chegam não importa: sempre converge para o estado mais recente.
 */
public record AgendamentoSincronizacaoEvent(String clinicaId, String agendamentoId, Tipo tipo) {

    public enum Tipo { CRIADO, ATUALIZADO, CANCELADO, EXCLUIDO }
}

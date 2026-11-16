package com.example.demo.enums;

/**
 * O que cai na agenda Google de cada usuário. A conta Google é sempre a PRÓPRIA do usuário (nunca compartilhada);
 * o escopo só define o conteúdo. Quem escolhe é o proprietário/sócio, ao lado da permissão do usuário.
 */
public enum EscopoGoogleCalendarEnum {
    /** Todos os agendamentos da clínica (recepção, financeiro...). Não exige vínculo com dentista. */
    TODOS_DA_CLINICA,
    /** Só os agendamentos do dentista vinculado ao usuário. Exige dentista vinculado e ativo. */
    SOMENTE_PROPRIO;

    public boolean exigeDentista() {
        return this == SOMENTE_PROPRIO;
    }
}

package com.example.demo.enums;

public enum SyncStatusGoogleEnum {
    ATIVO,
    /** O dono retirou a permissão do usuário; nada é enviado nem recebido até reconectar. */
    PAUSADO_PELO_DONO,
    /** O Google recusou renovar o acesso (usuário revogou nas configurações da conta Google). */
    REVOGADO_PELO_GOOGLE,
    /** Falha de autenticação que não é revogação (credenciais da aplicação, escopo insuficiente). */
    ERRO_AUTENTICACAO;

    public boolean sincroniza() {
        return this == ATIVO;
    }
}

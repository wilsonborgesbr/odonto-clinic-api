package com.example.demo.enums;

/**
 * Permissões de acesso a módulos do sistema.
 * Um {@code User} pode ter um subconjunto delas — o proprietário e sócios
 * têm todas por default; outros usuários recebem o pacote típico do seu cargo,
 * com o proprietário podendo customizar caso a caso.
 */
public enum PermissaoEnum {
    // Painel / navegação
    DASHBOARD,

    // Módulos clínicos
    PACIENTES,
    DENTISTAS,
    AGENDAMENTOS,
    PROCEDIMENTOS,
    ODONTOGRAMA,
    ANAMNESE,
    DOCUMENTOS,

    // Administrativo
    FUNCIONARIOS,
    ESTOQUE,
    CONVENIOS,

    // Financeiro
    AUDITORIA_FINANCEIRA,

    // Master (só proprietário/sócio por padrão)
    USUARIOS_E_PERMISSOES,
    CONFIGURACOES
}

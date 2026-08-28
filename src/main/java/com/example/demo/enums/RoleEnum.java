package com.example.demo.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Papel do usuário dentro da clínica.
 * Cada role possui um conjunto padrão de permissões — que o proprietário
 * pode sobrescrever no cadastro individual do usuário.
 */
public enum RoleEnum {

    /** Dono majoritário — controle total, único que pode transferir titularidade */
    PROPRIETARIO(EnumSet.allOf(PermissaoEnum.class)),

    /** Sócio — mesmo poder do proprietário */
    SOCIO(EnumSet.allOf(PermissaoEnum.class)),

    /** Gerente/administrador da clínica — tudo menos configurações críticas */
    ADMINISTRADOR(EnumSet.of(
            PermissaoEnum.DASHBOARD,
            PermissaoEnum.PACIENTES,
            PermissaoEnum.DENTISTAS,
            PermissaoEnum.AGENDAMENTOS,
            PermissaoEnum.PROCEDIMENTOS,
            PermissaoEnum.ODONTOGRAMA,
            PermissaoEnum.ANAMNESE,
            PermissaoEnum.DOCUMENTOS,
            PermissaoEnum.FUNCIONARIOS,
            PermissaoEnum.ESTOQUE,
            PermissaoEnum.CONVENIOS,
            PermissaoEnum.AUDITORIA_FINANCEIRA,
            PermissaoEnum.USUARIOS_E_PERMISSOES
    )),

    /** Dentista — pacientes, agendamentos, prontuários e procedimentos */
    DENTISTA(EnumSet.of(
            PermissaoEnum.DASHBOARD,
            PermissaoEnum.PACIENTES,
            PermissaoEnum.DENTISTAS,
            PermissaoEnum.AGENDAMENTOS,
            PermissaoEnum.PROCEDIMENTOS,
            PermissaoEnum.ODONTOGRAMA,
            PermissaoEnum.ANAMNESE,
            PermissaoEnum.DOCUMENTOS
    )),

    /** Recepcionista/secretária — gerencia pacientes e agenda */
    RECEPCIONISTA(EnumSet.of(
            PermissaoEnum.DASHBOARD,
            PermissaoEnum.PACIENTES,
            PermissaoEnum.AGENDAMENTOS,
            PermissaoEnum.CONVENIOS,
            PermissaoEnum.DOCUMENTOS
    )),

    /** Financeiro — só auditoria e dashboard */
    FINANCEIRO(EnumSet.of(
            PermissaoEnum.DASHBOARD,
            PermissaoEnum.AUDITORIA_FINANCEIRA
    )),

    /** Estoquista — só controle de estoque */
    ESTOQUISTA(EnumSet.of(
            PermissaoEnum.DASHBOARD,
            PermissaoEnum.ESTOQUE
    )),

    /** Auxiliar de saúde bucal (ASB/TSB) — apoio clínico ao dentista */
    AUXILIAR_CLINICO(EnumSet.of(
            PermissaoEnum.DASHBOARD,
            PermissaoEnum.PACIENTES,
            PermissaoEnum.AGENDAMENTOS,
            PermissaoEnum.PROCEDIMENTOS,
            PermissaoEnum.ODONTOGRAMA,
            PermissaoEnum.ANAMNESE,
            PermissaoEnum.ESTOQUE
    ));

    private final Set<PermissaoEnum> permissoesPadrao;

    RoleEnum(Set<PermissaoEnum> permissoesPadrao) {
        this.permissoesPadrao = permissoesPadrao;
    }

    public Set<PermissaoEnum> permissoesPadrao() {
        return EnumSet.copyOf(permissoesPadrao);
    }
}

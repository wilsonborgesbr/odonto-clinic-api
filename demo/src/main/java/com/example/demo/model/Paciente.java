package com.example.demo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "pacientes")
public class Paciente {

    @Id
    private String id;

    // Dados Pessoais
    @NotBlank(message = "Nome completo é obrigatório")
    private String nomeCompleto;

    @NotBlank(message = "CPF é obrigatório")
    @Indexed(unique = true)
    private String cpf;

    private String rg;

    @NotNull(message = "Data de nascimento é obrigatória")
    private LocalDate dataNascimento;

    @NotNull(message = "Sexo é obrigatório")
    private Sexo sexo;

    private EstadoCivil estadoCivil;
    private String profissao;

    // Contato
    @Email(message = "E-mail inválido")
    private String email;

    private String telefoneCelular;
    private String telefoneFixo;
    private String nomeContatoEmergencia;
    private String telefoneEmergencia;

    // Endereço
    private Endereco endereco;

    // Dados Clínicos
    private String tipoSanguineo;

    // Dados Administrativos
    private String numeroProntuario;

    @NotNull(message = "Tipo de paciente é obrigatório")
    private TipoPaciente tipoPaciente;

    private TipoPagamento tipoPagamento;
    private ComoConheceu comoConheceu;
    private Boolean ativo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Classe Interna Endereco
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Endereco {
        private String cep;
        private String logradouro;
        private String numero;
        private String complemento;
        private String bairro;
        private String cidade;
        private String estado;
    }

    // Enums de Controle
    public enum Sexo {
        MASCULINO, FEMININO, OUTRO
    }

    public enum EstadoCivil {
        SOLTEIRO, CASADO, DIVORCIADO, VIUVO, OUTRO
    }

    public enum TipoPagamento {
        PIX, DINHEIRO, CARTAO_CREDITO, CARTAO_DEBITO
    }

    public enum TipoPaciente {
        CONVENIO, PARTICULAR, MISTO
    }

    public enum ComoConheceu {
        INDICACAO, INSTAGRAM, GOOGLE, OUTRO
    }
}

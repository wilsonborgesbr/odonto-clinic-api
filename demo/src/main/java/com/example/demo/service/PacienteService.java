package com.example.demo.service;

import com.example.demo.model.Paciente;
import com.example.demo.repository.PacienteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PacienteService {

    @Autowired
    private PacienteRepository pacienteRepository;

    public Paciente criar(Paciente paciente) {
        // Verifica se já existe um paciente com o mesmo CPF
        if (pacienteRepository.findByCpf(paciente.getCpf()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CPF já cadastrado.");
        }

        // Gera automaticamente o numeroProntuario como um UUID curto (8 caracteres)
        String numeroProntuario = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        paciente.setNumeroProntuario(numeroProntuario);

        // Preenche os campos administrativos iniciais
        paciente.setAtivo(true);
        paciente.setCreatedAt(LocalDateTime.now());
        paciente.setUpdatedAt(LocalDateTime.now());

        return pacienteRepository.save(paciente);
    }

    public List<Paciente> listarTodos() {
        return pacienteRepository.findByAtivoTrue();
    }

    public Paciente buscarPorId(String id) {
        return pacienteRepository.findById(id)
                .filter(Paciente::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente não encontrado."));
    }

    public Paciente atualizar(String id, Paciente dadosAtualizados) {
        Paciente pacienteExistente = pacienteRepository.findById(id)
                .filter(Paciente::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente não encontrado."));

        // Se o CPF foi alterado, verifica se o novo CPF já está cadastrado em outro paciente
        if (!pacienteExistente.getCpf().equals(dadosAtualizados.getCpf())) {
            if (pacienteRepository.findByCpf(dadosAtualizados.getCpf()).isPresent()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CPF já cadastrado.");
            }
        }

        // Atualização de Dados Pessoais
        pacienteExistente.setNomeCompleto(dadosAtualizados.getNomeCompleto());
        pacienteExistente.setCpf(dadosAtualizados.getCpf());
        pacienteExistente.setRg(dadosAtualizados.getCpf()); // Mapeamento correto de RG
        pacienteExistente.setRg(dadosAtualizados.getRg());
        pacienteExistente.setDataNascimento(dadosAtualizados.getDataNascimento());
        pacienteExistente.setSexo(dadosAtualizados.getSexo());
        pacienteExistente.setEstadoCivil(dadosAtualizados.getEstadoCivil());
        pacienteExistente.setProfissao(dadosAtualizados.getProfissao());

        // Atualização de Contato
        pacienteExistente.setEmail(dadosAtualizados.getEmail());
        pacienteExistente.setTelefoneCelular(dadosAtualizados.getTelefoneCelular());
        pacienteExistente.setTelefoneFixo(dadosAtualizados.getTelefoneFixo());
        pacienteExistente.setNomeContatoEmergencia(dadosAtualizados.getNomeContatoEmergencia());
        pacienteExistente.setTelefoneEmergencia(dadosAuthorized(dadosAtualizados.getTelefoneEmergencia()));

        // Atualização de Endereço
        if (dadosAtualizados.getEndereco() != null) {
            Paciente.Endereco end = pacienteExistente.getEndereco();
            if (end == null) {
                end = new Paciente.Endereco();
            }
            end.setCep(dadosAtualizados.getEndereco().getCep());
            end.setLogradouro(dadosAtualizados.getEndereco().getLogradouro());
            end.setNumero(dadosAtualizados.getEndereco().getNumero());
            end.setComplemento(dadosAtualizados.getEndereco().getComplemento());
            end.setBairro(dadosAtualizados.getEndereco().getBairro());
            end.setCidade(dadosAtualizados.getEndereco().getCidade());
            end.setEstado(dadosAtualizados.getEndereco().getEstado());
            pacienteExistente.setEndereco(end);
        } else {
            pacienteExistente.setEndereco(null);
        }

        // Atualização de Dados Clínicos
        pacienteExistente.setTipoSanguineo(dadosAtualizados.getTipoSanguineo());

        // Atualização de Dados Administrativos
        pacienteExistente.setTipoPaciente(dadosAtualizados.getTipoPaciente());
        pacienteExistente.setTipoPagamento(dadosAtualizados.getTipoPagamento());
        pacienteExistente.setComoConheceu(dadosAtualizados.getComoConheceu());

        // Atualiza a data de modificação
        pacienteExistente.setUpdatedAt(LocalDateTime.now());

        return pacienteRepository.save(pacienteExistente);
    }

    public void deletar(String id) {
        Paciente pacienteExistente = pacienteRepository.findById(id)
                .filter(Paciente::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente não encontrado."));

        // Soft delete: inativa o paciente e atualiza a data de modificação
        pacienteExistente.setAtivo(false);
        pacienteExistente.setUpdatedAt(LocalDateTime.now());

        pacienteRepository.save(pacienteExistente);
    }

    private String dadosAuthorized(String val) {
        return val;
    }
}

package com.example.demo.service;

import com.example.demo.dto.PacienteListagemDTO;
import com.example.demo.model.Endereco;
import com.example.demo.model.Paciente;
import com.example.demo.repository.PacienteRepository;
import com.example.demo.security.AuthContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PacienteService {

    @Autowired
    private PacienteRepository pacienteRepository;

    public Paciente criar(Paciente paciente) {
        String clinicaId = AuthContextHelper.currentClinicaId();

        if (pacienteRepository.findByCpfAndClinicaId(paciente.getCpf(), clinicaId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CPF já cadastrado nesta clínica.");
        }

        paciente.setClinicaId(clinicaId);
        paciente.setNumeroProntuario(UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        paciente.setAtivo(true);
        paciente.setCreatedAt(LocalDateTime.now());
        paciente.setUpdatedAt(LocalDateTime.now());

        return pacienteRepository.save(paciente);
    }

    public Page<PacienteListagemDTO> listarTodos(Pageable pageable) {
        return pacienteRepository
                .findByClinicaIdAndAtivoTrue(AuthContextHelper.currentClinicaId(), pageable)
                .map(PacienteListagemDTO::new);
    }

    public Page<PacienteListagemDTO> buscarPorNome(String nome, Pageable pageable) {
        if (nome == null || nome.isBlank()) {
            return listarTodos(pageable);
        }
        return pacienteRepository
                .findByClinicaIdAndAtivoTrueAndNomeCompletoContainingIgnoreCase(
                        AuthContextHelper.currentClinicaId(), nome.trim(), pageable)
                .map(PacienteListagemDTO::new);
    }

    public Paciente buscarPorId(String id) {
        return pacienteRepository
                .findByIdAndClinicaId(id, AuthContextHelper.currentClinicaId())
                .filter(Paciente::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente não encontrado."));
    }

    public Paciente atualizar(String id, Paciente dadosAtualizados) {
        String clinicaId = AuthContextHelper.currentClinicaId();
        Paciente pacienteExistente = pacienteRepository.findByIdAndClinicaId(id, clinicaId)
                .filter(Paciente::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente não encontrado."));

        if (!pacienteExistente.getCpf().equals(dadosAtualizados.getCpf())
                && pacienteRepository.findByCpfAndClinicaId(dadosAtualizados.getCpf(), clinicaId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CPF já cadastrado nesta clínica.");
        }

        pacienteExistente.setNomeCompleto(dadosAtualizados.getNomeCompleto());
        pacienteExistente.setCpf(dadosAtualizados.getCpf());
        pacienteExistente.setRg(dadosAtualizados.getRg());
        pacienteExistente.setDataNascimento(dadosAtualizados.getDataNascimento());
        pacienteExistente.setSexo(dadosAtualizados.getSexo());
        pacienteExistente.setEstadoCivil(dadosAtualizados.getEstadoCivil());
        pacienteExistente.setProfissao(dadosAtualizados.getProfissao());

        pacienteExistente.setEmail(dadosAtualizados.getEmail());
        pacienteExistente.setTelefoneCelular(dadosAtualizados.getTelefoneCelular());
        pacienteExistente.setTelefoneFixo(dadosAtualizados.getTelefoneFixo());
        pacienteExistente.setNomeContatoEmergencia(dadosAtualizados.getNomeContatoEmergencia());
        pacienteExistente.setTelefoneEmergencia(dadosAtualizados.getTelefoneEmergencia());

        if (dadosAtualizados.getEndereco() != null) {
            Endereco end = pacienteExistente.getEndereco() != null
                    ? pacienteExistente.getEndereco()
                    : new Endereco();
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

        pacienteExistente.setTipoSanguineo(dadosAtualizados.getTipoSanguineo());
        pacienteExistente.setTipoPaciente(dadosAtualizados.getTipoPaciente());
        pacienteExistente.setConvenioId(dadosAtualizados.getConvenioId());
        pacienteExistente.setTipoPagamento(dadosAtualizados.getTipoPagamento());
        pacienteExistente.setComoConheceu(dadosAtualizados.getComoConheceu());
        pacienteExistente.setUpdatedAt(LocalDateTime.now());

        return pacienteRepository.save(pacienteExistente);
    }

    public void deletar(String id) {
        Paciente p = pacienteRepository
                .findByIdAndClinicaId(id, AuthContextHelper.currentClinicaId())
                .filter(Paciente::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente não encontrado."));
        p.setAtivo(false);
        p.setUpdatedAt(LocalDateTime.now());
        pacienteRepository.save(p);
    }

    public Paciente reativar(String id) {
        Paciente p = pacienteRepository
                .findByIdAndClinicaId(id, AuthContextHelper.currentClinicaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente não encontrado."));
        p.setAtivo(true);
        p.setUpdatedAt(LocalDateTime.now());
        return pacienteRepository.save(p);
    }
}

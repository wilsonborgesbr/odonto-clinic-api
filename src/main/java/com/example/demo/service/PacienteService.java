package com.example.demo.service;

import com.example.demo.dto.PacienteListagemDTO;
import com.example.demo.dto.PacienteResumoDTO;
import com.example.demo.model.Endereco;
import com.example.demo.model.Paciente;
import com.example.demo.repository.PacienteRepository;
import com.example.demo.security.AuthContextHelper;
import com.example.demo.util.ColacaoPtBr;
import com.example.demo.util.FormatadorTexto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class PacienteService {

    static final int MAX_IDS_RESUMO = 200;

    @Autowired
    private PacienteRepository pacienteRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    public Paciente criar(Paciente paciente) {
        FormatadorTexto.normalizar(paciente);
        String clinicaId = AuthContextHelper.currentClinicaId();

        if (cpfEmUso(paciente.getCpf(), clinicaId)) {
            throw cpfDuplicado();
        }

        paciente.setClinicaId(clinicaId);
        paciente.setNumeroProntuario(UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        paciente.setAtivo(true);
        paciente.setCreatedAt(LocalDateTime.now());
        paciente.setUpdatedAt(LocalDateTime.now());

        return salvar(paciente);
    }

    private boolean cpfEmUso(String cpf, String clinicaId) {
        return cpf != null && !cpf.isBlank()
                && pacienteRepository.findByCpfAndClinicaId(cpf, clinicaId).isPresent();
    }

    private ResponseStatusException cpfDuplicado() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "CPF já cadastrado nesta clínica.");
    }

    private Paciente salvar(Paciente paciente) {
        try {
            return pacienteRepository.save(paciente);
        } catch (DuplicateKeyException e) {
            throw cpfDuplicado();
        }
    }

    public Page<PacienteListagemDTO> listar(String termo, LocalDate desde, Pageable pageable) {
        Criteria criteria = Criteria.where("clinicaId").is(AuthContextHelper.currentClinicaId())
                .and("ativo").is(true);

        if (desde != null) {
            criteria.and("createdAt").gte(desde.atStartOfDay());
        }
        if (termo != null && !termo.isBlank()) {
            criteria.orOperator(criteriosDeBusca(termo.trim()));
        }

        Query query = new Query(criteria).with(pageable).collation(ColacaoPtBr.COLLATION);
        query.fields().include("nomeCompleto", "cpf", "email", "telefoneCelular", "ativo", "createdAt", "fotoUrl",
                "responsavelNome", "dataNascimento");

        List<Paciente> pacientes = mongoTemplate.find(query, Paciente.class);
        return PageableExecutionUtils.getPage(pacientes, pageable,
                () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Paciente.class))
                .map(PacienteListagemDTO::new);
    }

    private Criteria[] criteriosDeBusca(String termo) {
        List<Criteria> criterios = new ArrayList<>();
        criterios.add(Criteria.where("nomeCompleto").regex(BuscaTextoHelper.regexTolerante(termo), "i"));
        criterios.add(Criteria.where("email").regex(BuscaTextoHelper.regexTolerante(termo), "i"));
        if (BuscaTextoHelper.pareceCpf(termo)) {
            criterios.add(Criteria.where("cpf").regex(BuscaTextoHelper.regexCpf(termo)));
        }
        return criterios.toArray(new Criteria[0]);
    }

    public List<PacienteResumoDTO> buscarResumos(List<String> ids) {
        if (ids == null) {
            return List.of();
        }
        List<String> idsValidos = ids.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (idsValidos.size() > MAX_IDS_RESUMO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Máximo de " + MAX_IDS_RESUMO + " ids por requisição.");
        }
        if (idsValidos.isEmpty()) {
            return List.of();
        }
        return pacienteRepository
                .findByIdInAndClinicaId(idsValidos, AuthContextHelper.currentClinicaId())
                .stream()
                .map(PacienteResumoDTO::new)
                .toList();
    }

    public Paciente buscarPorId(String id) {
        return pacienteRepository
                .findByIdAndClinicaId(id, AuthContextHelper.currentClinicaId())
                .filter(Paciente::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente não encontrado."));
    }

    public Paciente atualizar(String id, Paciente dadosAtualizados) {
        FormatadorTexto.normalizar(dadosAtualizados);
        String clinicaId = AuthContextHelper.currentClinicaId();
        Paciente pacienteExistente = pacienteRepository.findByIdAndClinicaId(id, clinicaId)
                .filter(Paciente::getAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente não encontrado."));

        if (!Objects.equals(pacienteExistente.getCpf(), dadosAtualizados.getCpf())
                && cpfEmUso(dadosAtualizados.getCpf(), clinicaId)) {
            throw cpfDuplicado();
        }

        pacienteExistente.setNomeCompleto(dadosAtualizados.getNomeCompleto());
        pacienteExistente.setCpf(dadosAtualizados.getCpf());
        pacienteExistente.setRg(dadosAtualizados.getRg());
        pacienteExistente.setResponsavelNome(dadosAtualizados.getResponsavelNome());
        pacienteExistente.setResponsavelCpf(dadosAtualizados.getResponsavelCpf());
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

        return salvar(pacienteExistente);
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

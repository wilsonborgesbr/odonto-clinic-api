package com.example.demo.service;

import com.example.demo.dto.VincularOdontogramaDTO;
import com.example.demo.dto.VinculoOdontogramaResponseDTO;
import com.example.demo.enums.CondicaoDenteEnum;
import com.example.demo.enums.FaceDenteEnum;
import com.example.demo.model.DenteStatus;
import com.example.demo.model.Odontograma;
import com.example.demo.model.Procedimento;
import com.example.demo.repository.OdontogramaRepository;
import com.example.demo.repository.PacienteRepository;
import com.example.demo.repository.ProcedimentoRepository;
import com.example.demo.security.AuthContextHelper;
import com.example.demo.util.DenteFdi;
import com.example.demo.util.FusoBrasil;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Liga um procedimento a um odontograma e marca o dente/faces nele.
 *
 * <p>São dois documentos (odontograma e procedimento) e o Mongo local é standalone, sem transação entre
 * documentos. A atomicidade vem de: cada escrita é atômica no seu documento ({@code $set}, {@code $addToSet},
 * {@code $push} condicional); o odontograma é gravado primeiro (inofensivo se ficar sozinho); e qualquer falha
 * antes de o procedimento ser gravado desfaz o que foi feito (apaga o odontograma criado ou restaura o dente).
 * A operação é idempotente: repetir a mesma chamada converge para o mesmo estado.
 */
@Service
public class VinculoOdontogramaService {

    static final String MSG_ODONTOGRAMA_INVALIDO = "Odontograma vinculado inválido para este paciente.";
    static final String MSG_CONFLITO = "Não foi possível atualizar o odontograma agora. Tente novamente.";
    private static final int MAX_TENTATIVAS = 3;

    private static final Logger log = LoggerFactory.getLogger(VinculoOdontogramaService.class);

    @Autowired
    private ProcedimentoRepository procedimentoRepository;

    @Autowired
    private OdontogramaRepository odontogramaRepository;

    @Autowired
    private PacienteRepository pacienteRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    private Clock relogio = Clock.systemUTC();

    public VinculoOdontogramaResponseDTO vincular(String procedimentoId, VincularOdontogramaDTO dto) {
        String clinicaId = AuthContextHelper.currentClinicaId();

        Procedimento procedimento = procedimentoRepository.findByIdAndClinicaId(procedimentoId, clinicaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Procedimento não encontrado."));
        pacienteRepository.findByIdAndClinicaId(procedimento.getPacienteId(), clinicaId)
                .filter(p -> Boolean.TRUE.equals(p.getAtivo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente não encontrado."));

        String dente = dto.getDente() == null ? null : dto.getDente().trim();
        if (!DenteFdi.valido(dente)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, DenteFdi.MENSAGEM);
        }
        List<FaceDenteEnum> faces = facesSemRepeticao(dto.getFaces());

        Odontograma alvo = resolverOdontograma(procedimento, dto.getOdontogramaId(), clinicaId).orElse(null);
        DenteStatus anterior = alvo == null ? null : copiaDoDente(alvo, dente);
        validarCoerencia(dto.getCondicao(), anterior, faces);

        boolean criado = alvo == null;
        if (criado) {
            alvo = criarOdontogramaMinimo(procedimento, clinicaId);
        }

        boolean denteEscrito = false;
        try {
            gravarDente(alvo.getId(), clinicaId, dente, faces, dto.getCondicao());
            denteEscrito = true;
            Procedimento atualizado = gravarProcedimento(procedimento, alvo.getId(), dente, faces, clinicaId);
            Odontograma resultado = odontogramaRepository.findByIdAndClinicaId(alvo.getId(), clinicaId).orElse(alvo);
            return new VinculoOdontogramaResponseDTO(atualizado, resultado, copiaDoDente(resultado, dente), criado);
        } catch (RuntimeException e) {
            desfazer(alvo.getId(), clinicaId, criado, denteEscrito, dente, anterior);
            throw e;
        }
    }

    /** O informado; senão o já ligado ao procedimento; senão o mais recente do paciente; senão vazio. */
    private Optional<Odontograma> resolverOdontograma(Procedimento procedimento, String informado, String clinicaId) {
        if (informado != null && !informado.isBlank()) {
            return Optional.of(odontogramaRepository.findByIdAndClinicaId(informado, clinicaId)
                    .filter(o -> procedimento.getPacienteId().equals(o.getPacienteId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, MSG_ODONTOGRAMA_INVALIDO)));
        }
        String atual = procedimento.getOdontogramaId();
        if (atual != null && !atual.isBlank()) {
            Optional<Odontograma> ligado = odontogramaRepository.findByIdAndClinicaId(atual, clinicaId)
                    .filter(o -> procedimento.getPacienteId().equals(o.getPacienteId()));
            if (ligado.isPresent()) {
                return ligado;
            }
        }
        return odontogramaRepository.findTopByClinicaIdAndPacienteIdOrderByCreatedAtDesc(clinicaId, procedimento.getPacienteId());
    }

    /** Face só combina com condições que a admitem; sem condição nenhuma não há o que contradizer. */
    private void validarCoerencia(CondicaoDenteEnum informada, DenteStatus anterior, List<FaceDenteEnum> faces) {
        CondicaoDenteEnum efetiva = informada != null ? informada : (anterior == null ? null : anterior.getCondicao());
        if (!faces.isEmpty() && efetiva != null && !efetiva.admiteFaces()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A condição " + efetiva + " não admite marcação de faces.");
        }
    }

    private Odontograma criarOdontogramaMinimo(Procedimento procedimento, String clinicaId) {
        Odontograma novo = new Odontograma();
        novo.setClinicaId(clinicaId);
        novo.setPacienteId(procedimento.getPacienteId());
        novo.setDentes(new ArrayList<>());
        novo.setDataAvaliacao(FusoBrasil.hoje(relogio));
        novo.setObservacoes("Criado automaticamente ao vincular um procedimento.");
        novo.setCreatedAt(LocalDateTime.now());
        return odontogramaRepository.save(novo);
    }

    /**
     * Cria ou atualiza o dente com operações atômicas no documento: se já existe, soma as faces e só troca a
     * condição quando ela foi informada; se não existe, acrescenta apenas se ninguém o acrescentou antes.
     */
    private void gravarDente(String odontogramaId, String clinicaId, String dente, List<FaceDenteEnum> faces,
                             CondicaoDenteEnum condicao) {
        Criteria doOdontograma = Criteria.where("id").is(odontogramaId).and("clinicaId").is(clinicaId);
        for (int tentativa = 0; tentativa < MAX_TENTATIVAS; tentativa++) {
            Query comODente = new Query(new Criteria().andOperator(doOdontograma, Criteria.where("dentes.numeroDente").is(dente)));
            Update existente = new Update();
            if (condicao != null) {
                existente.set("dentes.$.condicao", condicao);
            }
            if (!faces.isEmpty()) {
                existente.addToSet("dentes.$.faces").each(faces.toArray());
            }
            if (existente.getUpdateObject().isEmpty()) {
                if (mongoTemplate.exists(comODente, Odontograma.class)) {
                    return;
                }
            } else if (mongoTemplate.updateFirst(comODente, existente, Odontograma.class).getMatchedCount() > 0) {
                return;
            }

            Query semODente = new Query(new Criteria().andOperator(doOdontograma, Criteria.where("dentes.numeroDente").ne(dente)));
            DenteStatus novo = new DenteStatus(dente, condicao, faces.isEmpty() ? null : new ArrayList<>(faces), null);
            if (mongoTemplate.updateFirst(semODente, new Update().push("dentes", novo), Odontograma.class).getMatchedCount() > 0) {
                return;
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, MSG_CONFLITO);
    }

    private Procedimento gravarProcedimento(Procedimento procedimento, String odontogramaId, String dente,
                                            List<FaceDenteEnum> faces, String clinicaId) {
        Update update = new Update()
                .set("odontogramaId", odontogramaId)
                .set("dente", dente)
                .set("faces", faces)
                .set("updatedAt", LocalDateTime.now());
        Procedimento atualizado = mongoTemplate.findAndModify(
                new Query(Criteria.where("id").is(procedimento.getId()).and("clinicaId").is(clinicaId)),
                update, FindAndModifyOptions.options().returnNew(true), Procedimento.class);
        if (atualizado == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Procedimento não encontrado.");
        }
        return atualizado;
    }

    /** Melhor esforço: devolve o odontograma ao estado de antes. Falhas aqui só vão para o log. */
    private void desfazer(String odontogramaId, String clinicaId, boolean criado, boolean denteEscrito, String dente,
                          DenteStatus anterior) {
        try {
            if (criado) {
                odontogramaRepository.deleteById(odontogramaId);
                return;
            }
            if (!denteEscrito) {
                return;
            }
            Criteria doOdontograma = Criteria.where("id").is(odontogramaId).and("clinicaId").is(clinicaId);
            if (anterior != null) {
                Query comODente = new Query(new Criteria().andOperator(doOdontograma, Criteria.where("dentes.numeroDente").is(dente)));
                mongoTemplate.updateFirst(comODente, new Update().set("dentes.$", anterior), Odontograma.class);
            } else {
                mongoTemplate.updateFirst(new Query(doOdontograma),
                        new Update().pull("dentes", new Document("numeroDente", dente)), Odontograma.class);
            }
        } catch (RuntimeException e) {
            log.error("Falha ao desfazer o vínculo no odontograma {}: {}", odontogramaId, e.getMessage());
        }
    }

    private static List<FaceDenteEnum> facesSemRepeticao(List<FaceDenteEnum> faces) {
        if (faces == null || faces.isEmpty()) {
            return List.of();
        }
        EnumSet<FaceDenteEnum> conjunto = EnumSet.noneOf(FaceDenteEnum.class);
        faces.stream().filter(f -> f != null).forEach(conjunto::add);
        return List.copyOf(conjunto);
    }

    private static DenteStatus copiaDoDente(Odontograma odontograma, String dente) {
        if (odontograma.getDentes() == null) {
            return null;
        }
        return odontograma.getDentes().stream()
                .filter(d -> dente.equals(d.getNumeroDente()))
                .findFirst()
                .map(d -> new DenteStatus(d.getNumeroDente(), d.getCondicao(),
                        d.getFaces() == null ? null : new ArrayList<>(d.getFaces()), d.getObservacao()))
                .orElse(null);
    }
}

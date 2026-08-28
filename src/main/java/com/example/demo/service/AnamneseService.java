package com.example.demo.service;

import com.example.demo.model.Anamnese;
import com.example.demo.repository.AnamneseRepository;
import com.example.demo.repository.PacienteRepository;
import com.example.demo.security.AuthContextHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AnamneseService {

    @Autowired
    private AnamneseRepository anamneseRepository;

    @Autowired
    private PacienteRepository pacienteRepository;

    public Anamnese criar(Anamnese anamnese) {
        String clinicaId = AuthContextHelper.currentClinicaId();
        if (anamnese.getPacienteId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID do paciente não pode ser nulo.");
        }

        var pacienteOpt = pacienteRepository.findByIdAndClinicaId(anamnese.getPacienteId(), clinicaId);
        if (pacienteOpt.isEmpty() || !Boolean.TRUE.equals(pacienteOpt.get().getAtivo())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente não encontrado.");
        }

        anamnese.setClinicaId(clinicaId);
        anamnese.setDataPreenchimento(LocalDate.now());
        anamnese.setCreatedAt(LocalDateTime.now());

        return anamneseRepository.save(anamnese);
    }

    public List<Anamnese> listarPorPacienteId(String pacienteId) {
        return anamneseRepository.findByClinicaIdAndPacienteId(
                AuthContextHelper.currentClinicaId(), pacienteId);
    }

    public Anamnese buscarMaisRecentePorPacienteId(String pacienteId) {
        return anamneseRepository
                .findTopByClinicaIdAndPacienteIdOrderByCreatedAtDesc(AuthContextHelper.currentClinicaId(), pacienteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nenhuma anamnese encontrada para o paciente informado."));
    }

    public Anamnese buscarPorId(String id) {
        return anamneseRepository
                .findByIdAndClinicaId(id, AuthContextHelper.currentClinicaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Anamnese não encontrada."));
    }

    public Anamnese atualizar(String id, Anamnese dados) {
        Anamnese existente = buscarPorId(id);
        existente.setQueixaPrincipal(dados.getQueixaPrincipal());
        existente.setHistoricoDental(dados.getHistoricoDental());
        existente.setUsaMedicamentos(dados.getUsaMedicamentos());
        existente.setQuaisMedicamentos(dados.getQuaisMedicamentos());
        existente.setTemAlergia(dados.getTemAlergia());
        existente.setQuaisAlergias(dados.getQuaisAlergias());
        existente.setDoencasPreexistentes(dados.getDoencasPreexistentes());
        existente.setGestante(dados.getGestante());
        existente.setFumante(dados.getFumante());
        existente.setConsumoAlcool(dados.getConsumoAlcool());
        existente.setHistoriaFamiliar(dados.getHistoriaFamiliar());
        existente.setObservacoes(dados.getObservacoes());
        return anamneseRepository.save(existente);
    }

    public void excluir(String id) {
        anamneseRepository.delete(buscarPorId(id));
    }
}

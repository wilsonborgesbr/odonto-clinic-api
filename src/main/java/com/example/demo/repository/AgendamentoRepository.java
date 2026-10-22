package com.example.demo.repository;

import com.example.demo.enums.StatusAgendamentoEnum;
import com.example.demo.model.Agendamento;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AgendamentoRepository extends MongoRepository<Agendamento, String> {

    List<Agendamento> findByClinicaId(String clinicaId);

    org.springframework.data.domain.Page<Agendamento> findByClinicaId(
            String clinicaId, org.springframework.data.domain.Pageable pageable);

    @Query("{ 'clinicaId': ?0, 'dataHoraInicio': { $gte: ?1, $lt: ?2 } }")
    org.springframework.data.domain.Page<Agendamento> findPorPeriodo(
            String clinicaId, LocalDateTime inicioInclusivo, LocalDateTime fimExclusivo,
            org.springframework.data.domain.Pageable pageable);

    @Query("{ 'clinicaId': ?0, 'dataHoraInicio': { $gte: ?1 } }")
    org.springframework.data.domain.Page<Agendamento> findAPartirDe(
            String clinicaId, LocalDateTime inicioInclusivo,
            org.springframework.data.domain.Pageable pageable);

    @Query("{ 'clinicaId': ?0, 'dataHoraInicio': { $lt: ?1 } }")
    org.springframework.data.domain.Page<Agendamento> findAte(
            String clinicaId, LocalDateTime fimExclusivo,
            org.springframework.data.domain.Pageable pageable);

    List<Agendamento> findByClinicaIdAndPacienteId(String clinicaId, String pacienteId);

    List<Agendamento> findByClinicaIdAndDentistaId(String clinicaId, String dentistaId);

    List<Agendamento> findByClinicaIdAndStatus(String clinicaId, StatusAgendamentoEnum status);

    Optional<Agendamento> findByIdAndClinicaId(String id, String clinicaId);

    @Query("{ 'clinicaId': ?0, 'dentistaId': ?1, 'status': { $nin: ['CANCELADO'] }, "
         + "'dataHoraInicio': { $lt: ?3 }, 'dataHoraFim': { $gt: ?2 } }")
    List<Agendamento> findConflitos(String clinicaId, String dentistaId,
                                    LocalDateTime inicio, LocalDateTime fim);
}

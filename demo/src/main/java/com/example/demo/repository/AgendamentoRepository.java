package com.example.demo.repository;

import com.example.demo.enums.StatusAgendamentoEnum;
import com.example.demo.model.Agendamento;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AgendamentoRepository extends MongoRepository<Agendamento, String> {

    List<Agendamento> findByPacienteId(String pacienteId);

    List<Agendamento> findByDentistaId(String dentistaId);

    List<Agendamento> findByStatus(StatusAgendamentoEnum status);

    List<Agendamento> findByDataHoraInicioBetween(LocalDateTime inicio, LocalDateTime fim);

    List<Agendamento> findByDentistaIdAndDataHoraInicioBetween(
            String dentistaId, LocalDateTime inicio, LocalDateTime fim);
}

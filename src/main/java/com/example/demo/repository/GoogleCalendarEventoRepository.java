package com.example.demo.repository;

import com.example.demo.model.GoogleCalendarEvento;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface GoogleCalendarEventoRepository extends MongoRepository<GoogleCalendarEvento, String> {

    Optional<GoogleCalendarEvento> findByConexaoIdAndAgendamentoId(String conexaoId, String agendamentoId);

    Optional<GoogleCalendarEvento> findByConexaoIdAndGoogleEventId(String conexaoId, String googleEventId);

    List<GoogleCalendarEvento> findByClinicaIdAndAgendamentoId(String clinicaId, String agendamentoId);

    List<GoogleCalendarEvento> findByConexaoId(String conexaoId);

    void deleteByConexaoId(String conexaoId);
}

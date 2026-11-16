package com.example.demo.repository;

import com.example.demo.model.GoogleCalendarConexao;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface GoogleCalendarConexaoRepository extends MongoRepository<GoogleCalendarConexao, String> {

    Optional<GoogleCalendarConexao> findByClinicaIdAndUserId(String clinicaId, String userId);

    Optional<GoogleCalendarConexao> findByIdAndClinicaId(String id, String clinicaId);


    boolean existsByGoogleEmailAndIdNot(String googleEmail, String id);

    List<GoogleCalendarConexao> findByClinicaId(String clinicaId);

    Optional<GoogleCalendarConexao> findByWebhookWatchChannelId(String channelId);

    List<GoogleCalendarConexao> findByWebhookWatchExpirationBefore(java.time.Instant limite);
}

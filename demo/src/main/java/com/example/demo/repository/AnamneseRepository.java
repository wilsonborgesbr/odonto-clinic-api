package com.example.demo.repository;

import com.example.demo.model.Anamnese;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface AnamneseRepository extends MongoRepository<Anamnese, String> {

    List<Anamnese> findByClinicaIdAndPacienteId(String clinicaId, String pacienteId);

    Optional<Anamnese> findTopByClinicaIdAndPacienteIdOrderByCreatedAtDesc(String clinicaId, String pacienteId);

    Optional<Anamnese> findByIdAndClinicaId(String id, String clinicaId);
}

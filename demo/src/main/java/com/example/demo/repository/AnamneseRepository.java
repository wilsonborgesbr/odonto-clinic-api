package com.example.demo.repository;

import com.example.demo.model.Anamnese;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface AnamneseRepository extends MongoRepository<Anamnese, String> {

    List<Anamnese> findByPacienteId(String pacienteId);

    Optional<Anamnese> findTopByPacienteIdOrderByCreatedAtDesc(String pacienteId);
}

package com.example.demo.repository;

import com.example.demo.model.Odontograma;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface OdontogramaRepository extends MongoRepository<Odontograma, String> {

    List<Odontograma> findByPacienteId(String pacienteId);

    Optional<Odontograma> findTopByPacienteIdOrderByCreatedAtDesc(String pacienteId);
}

package com.example.demo.repository;

import com.example.demo.model.Paciente;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface PacienteRepository extends MongoRepository<Paciente, String> {

    Optional<Paciente> findByCpf(String cpf);

    List<Paciente> findByAtivoTrue();
}

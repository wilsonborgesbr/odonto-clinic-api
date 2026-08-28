package com.example.demo.repository;

import com.example.demo.model.Paciente;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface PacienteRepository extends MongoRepository<Paciente, String> {

    Optional<Paciente> findByCpfAndClinicaId(String cpf, String clinicaId);

    List<Paciente> findByClinicaIdAndAtivoTrue(String clinicaId);

    Page<Paciente> findByClinicaIdAndAtivoTrue(String clinicaId, Pageable pageable);

    Page<Paciente> findByClinicaIdAndAtivoTrueAndNomeCompletoContainingIgnoreCase(
            String clinicaId, String nomeCompleto, Pageable pageable);

    Optional<Paciente> findByIdAndClinicaId(String id, String clinicaId);
}

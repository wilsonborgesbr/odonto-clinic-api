package com.example.demo.repository;

import com.example.demo.model.Paciente;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PacienteRepository extends MongoRepository<Paciente, String> {

    Optional<Paciente> findByCpfAndClinicaId(String cpf, String clinicaId);

    List<Paciente> findByClinicaIdAndAtivoTrue(String clinicaId);

    Optional<Paciente> findByIdAndClinicaId(String id, String clinicaId);

    List<Paciente> findByIdInAndClinicaId(Collection<String> ids, String clinicaId);
}

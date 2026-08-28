package com.example.demo.repository;

import com.example.demo.model.Documento;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface DocumentoRepository extends MongoRepository<Documento, String> {

    List<Documento> findByClinicaId(String clinicaId, Sort sort);

    List<Documento> findByClinicaIdAndPacienteId(String clinicaId, String pacienteId);

    java.util.Optional<Documento> findByIdAndClinicaId(String id, String clinicaId);
}

package com.example.demo.repository;

import com.example.demo.model.Dentista;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface DentistaRepository extends MongoRepository<Dentista, String> {

    Optional<Dentista> findByCroAndClinicaId(String cro, String clinicaId);

    List<Dentista> findByClinicaIdAndAtivoTrue(String clinicaId);

    Page<Dentista> findByClinicaIdAndAtivoTrue(String clinicaId, Pageable pageable);

    Optional<Dentista> findByIdAndClinicaId(String id, String clinicaId);
}

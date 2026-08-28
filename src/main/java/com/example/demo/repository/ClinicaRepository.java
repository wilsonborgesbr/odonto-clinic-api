package com.example.demo.repository;

import com.example.demo.model.Clinica;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClinicaRepository extends MongoRepository<Clinica, String> {

    Optional<Clinica> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);
}

package com.example.demo.repository;

import com.example.demo.model.Dentista;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DentistaRepository extends MongoRepository<Dentista, String> {

    Optional<Dentista> findByCro(String cro);

    List<Dentista> findByAtivoTrue();
}

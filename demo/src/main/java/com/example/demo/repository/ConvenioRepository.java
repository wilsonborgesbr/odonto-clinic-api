package com.example.demo.repository;

import com.example.demo.model.Convenio;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface ConvenioRepository extends MongoRepository<Convenio, String> {

    Optional<Convenio> findByCnpj(String cnpj);

    List<Convenio> findByAtivoTrue();
}

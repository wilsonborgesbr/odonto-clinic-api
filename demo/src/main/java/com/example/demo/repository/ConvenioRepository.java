package com.example.demo.repository;

import com.example.demo.model.Convenio;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface ConvenioRepository extends MongoRepository<Convenio, String> {

    Optional<Convenio> findByCnpjAndClinicaId(String cnpj, String clinicaId);

    List<Convenio> findByClinicaIdAndAtivoTrue(String clinicaId);

    List<Convenio> findByClinicaId(String clinicaId);

    Optional<Convenio> findByIdAndClinicaId(String id, String clinicaId);
}

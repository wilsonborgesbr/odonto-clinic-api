package com.example.demo.repository;

import com.example.demo.enums.StatusFinanceiroEnum;
import com.example.demo.model.ContaReceber;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;

public interface ContaReceberRepository extends MongoRepository<ContaReceber, String> {

    List<ContaReceber> findByClinicaId(String clinicaId);

    List<ContaReceber> findByClinicaIdAndPacienteId(String clinicaId, String pacienteId);

    List<ContaReceber> findByClinicaIdAndStatus(String clinicaId, StatusFinanceiroEnum status);

    java.util.Optional<ContaReceber> findByIdAndClinicaId(String id, String clinicaId);
}

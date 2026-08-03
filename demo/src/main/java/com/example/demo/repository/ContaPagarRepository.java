package com.example.demo.repository;

import com.example.demo.enums.StatusFinanceiroEnum;
import com.example.demo.model.ContaPagar;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ContaPagarRepository extends MongoRepository<ContaPagar, String> {

    List<ContaPagar> findByClinicaId(String clinicaId);

    List<ContaPagar> findByClinicaIdAndStatus(String clinicaId, StatusFinanceiroEnum status);

    java.util.Optional<ContaPagar> findByIdAndClinicaId(String id, String clinicaId);
}

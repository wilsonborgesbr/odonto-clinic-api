package com.example.demo.repository;

import com.example.demo.enums.StatusFinanceiroEnum;
import com.example.demo.model.ContaReceber;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;

public interface ContaReceberRepository extends MongoRepository<ContaReceber, String> {

    List<ContaReceber> findByPacienteId(String pacienteId);

    List<ContaReceber> findByStatus(StatusFinanceiroEnum status);

    List<ContaReceber> findByDataVencimentoBefore(LocalDate data);
}

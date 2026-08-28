package com.example.demo.repository;

import com.example.demo.model.Funcionario;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface FuncionarioRepository extends MongoRepository<Funcionario, String> {

    Optional<Funcionario> findByCpfAndClinicaId(String cpf, String clinicaId);

    List<Funcionario> findByClinicaIdAndAtivoTrue(String clinicaId);

    Page<Funcionario> findByClinicaIdAndAtivoTrue(String clinicaId, Pageable pageable);

    Optional<Funcionario> findByIdAndClinicaId(String id, String clinicaId);
}

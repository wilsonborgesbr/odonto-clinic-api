package com.example.demo.repository;

import com.example.demo.model.Funcionario;
import com.example.demo.util.ColacaoPtBr;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface FuncionarioRepository extends MongoRepository<Funcionario, String> {

    Optional<Funcionario> findByCpfAndClinicaId(String cpf, String clinicaId);

    List<Funcionario> findByClinicaIdAndAtivoTrue(String clinicaId);

    @Query(value = "{ 'clinicaId': ?0, 'ativo': true }", collation = ColacaoPtBr.JSON)
    Page<Funcionario> findByClinicaIdAndAtivoTrue(String clinicaId, Pageable pageable);

    Optional<Funcionario> findByIdAndClinicaId(String id, String clinicaId);
}

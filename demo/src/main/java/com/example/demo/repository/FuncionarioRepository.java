package com.example.demo.repository;

import com.example.demo.model.Funcionario;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface FuncionarioRepository extends MongoRepository<Funcionario, String> {

    Optional<Funcionario> findByCpf(String cpf);

    List<Funcionario> findByAtivoTrue();
}

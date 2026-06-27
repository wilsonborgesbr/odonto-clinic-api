package com.example.demo.repository;

import com.example.demo.enums.CategoriaEstoqueEnum;
import com.example.demo.model.Estoque;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface EstoqueRepository extends MongoRepository<Estoque, String> {

    List<Estoque> findByQuantidadeAtualLessThanEqual(Integer quantidade);

    List<Estoque> findByCategoria(CategoriaEstoqueEnum categoria);
}

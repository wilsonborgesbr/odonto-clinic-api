package com.example.demo.repository;

import com.example.demo.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {

    /**
     * Busca por (email, clinicaId). Único combo garantido pelo compound index.
     */
    Optional<User> findByEmailAndClinicaId(String email, String clinicaId);

    /** Verifica duplicidade dentro de uma clínica. */
    boolean existsByEmailAndClinicaId(String email, String clinicaId);

    /** Lista todos os usuários de uma clínica (para tela de gestão). */
    List<User> findByClinicaId(String clinicaId);
}

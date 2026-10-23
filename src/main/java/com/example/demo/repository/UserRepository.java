package com.example.demo.repository;

import com.example.demo.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
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

    /** Busca tolerante a legado: e-mail igual ignorando caixa (regex ancorado e escapado). */
    @Query("{ 'clinicaId': ?0, 'email': { $regex: ?1, $options: 'i' } }")
    List<User> findByClinicaIdAndEmailRegexIgnoreCase(String clinicaId, String regex);

    /** Login sem código de clínica: todas as contas com este e-mail (já normalizado). */
    List<User> findByEmail(String email);

    /** Idem, tolerante a legado com maiúsculas (regex ancorado e escapado). */
    @Query("{ 'email': { $regex: ?0, $options: 'i' } }")
    List<User> findByEmailRegexIgnoreCase(String regex);

    /** Lista todos os usuários de uma clínica (para tela de gestão). */
    List<User> findByClinicaId(String clinicaId);
}

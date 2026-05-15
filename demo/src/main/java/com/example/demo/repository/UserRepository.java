package com.example.demo.repository;

import com.example.demo.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    
    // Método padrão do Spring Data JPA/MongoDB para buscar usuário pelo email (que é o login)
    UserDetails findByEmail(String email);
}

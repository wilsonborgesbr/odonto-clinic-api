package com.example.demo.repository;

import com.example.demo.enums.TipoTokenAuthEnum;
import com.example.demo.model.AuthToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface AuthTokenRepository extends MongoRepository<AuthToken, String> {

    Optional<AuthToken> findByTokenHash(String tokenHash);

    void deleteByUserIdAndTipoAndUsadoEmIsNull(String userId, TipoTokenAuthEnum tipo);
}

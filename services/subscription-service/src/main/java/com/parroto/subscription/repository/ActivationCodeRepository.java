package com.parroto.subscription.repository;

import com.parroto.subscription.entity.ActivationCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ActivationCodeRepository extends JpaRepository<ActivationCode, UUID> {
    Optional<ActivationCode> findByCode(String code);
}

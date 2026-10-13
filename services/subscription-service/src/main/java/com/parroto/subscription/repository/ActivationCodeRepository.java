package com.parroto.subscription.repository;

import com.parroto.subscription.entity.ActivationCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivationCodeRepository extends JpaRepository<ActivationCode, UUID> {

    Optional<ActivationCode> findByCode(String code);

    /** Row lock so the same code cannot be redeemed twice concurrently. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ActivationCode c WHERE c.code = :code")
    Optional<ActivationCode> findByCodeForUpdate(@Param("code") String code);

    @Query("SELECT c.code FROM ActivationCode c WHERE c.code IN :codes")
    List<String> findExistingCodes(@Param("codes") Collection<String> codes);

    @EntityGraph(attributePaths = "plan")
    Page<ActivationCode> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = "plan")
    Page<ActivationCode> findByUsedByUserIdIsNotNullOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = "plan")
    Page<ActivationCode> findByUsedByUserIdIsNullOrderByCreatedAtDesc(Pageable pageable);
}

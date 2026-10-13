package com.parroto.auth.repository;

import com.parroto.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    /**
     * Admin user list. {@code emailPattern} is a lower-cased LIKE pattern with {@code \} as escape character
     * ({@code "%"} matches everyone) — case-insensitive like ILIKE. {@code status} is {@code all|active|locked};
     * a NULL is_active counts as locked (login treats it the same way). No nullable parameters on purpose
     * (PostgreSQL cannot always infer the type of a NULL bind).
     */
    @Query("""
            SELECT u FROM User u
            WHERE LOWER(u.email) LIKE :emailPattern ESCAPE '\\'
              AND (:status = 'all'
                   OR (:status = 'active' AND u.isActive = true)
                   OR (:status = 'locked' AND (u.isActive = false OR u.isActive IS NULL)))
            """)
    Page<User> searchForAdmin(@Param("emailPattern") String emailPattern,
                              @Param("status") String status,
                              Pageable pageable);

    /** {@code excludedEmails} must be lower-cased and non-empty (pass a sentinel such as "" when there are none). */
    @Query("SELECT COUNT(u) FROM User u WHERE LOWER(u.email) NOT IN :excludedEmails")
    long countExcluding(@Param("excludedEmails") Collection<String> excludedEmails);

    @Query("SELECT COUNT(u) FROM User u WHERE LOWER(u.email) NOT IN :excludedEmails AND u.isActive = true")
    long countActiveExcluding(@Param("excludedEmails") Collection<String> excludedEmails);

    @Query("SELECT COUNT(u) FROM User u WHERE LOWER(u.email) NOT IN :excludedEmails AND u.createdAt >= :since")
    long countCreatedSinceExcluding(@Param("excludedEmails") Collection<String> excludedEmails,
                                    @Param("since") OffsetDateTime since);
}

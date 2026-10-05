package org.institutonata.suprihub.auth.infra;

import java.time.Instant;
import java.util.Optional;

import org.institutonata.suprihub.auth.domain.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    Optional<VerificationToken> findByTokenHash(String tokenHash);

    /** Invalida tokens anteriores do mesmo propósito ao emitir um novo. */
    @Modifying
    @Query("""
            UPDATE VerificationToken t SET t.usedAt = :now
            WHERE t.userId = :userId AND t.purpose = :purpose AND t.usedAt IS NULL""")
    int invalidateOpen(@Param("userId") Long userId, @Param("purpose") VerificationToken.Purpose purpose,
            @Param("now") Instant now);
}

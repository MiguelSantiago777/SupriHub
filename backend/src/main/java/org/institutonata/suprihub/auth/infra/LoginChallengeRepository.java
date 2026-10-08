package org.institutonata.suprihub.auth.infra;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.institutonata.suprihub.auth.domain.LoginChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginChallengeRepository extends JpaRepository<LoginChallenge, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM LoginChallenge c WHERE c.challengeHash = :hash")
    Optional<LoginChallenge> findByChallengeHashForUpdate(@Param("hash") String hash);
}

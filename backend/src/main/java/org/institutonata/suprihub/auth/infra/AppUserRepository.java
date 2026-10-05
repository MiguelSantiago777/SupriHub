package org.institutonata.suprihub.auth.infra;

import java.util.Optional;

import org.institutonata.suprihub.auth.domain.AppRole;
import org.institutonata.suprihub.auth.domain.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    @Query("SELECT u FROM AppUser u WHERE lower(u.email) = lower(:email)")
    Optional<AppUser> findByEmail(@Param("email") String email);

    @Query("SELECT COUNT(u) > 0 FROM AppUser u JOIN u.roles r WHERE r.code = :role")
    boolean existsWithRole(@Param("role") AppRole role);

    /** {@code q} vazio ("") casa com todos. */
    @Query("""
            SELECT u FROM AppUser u
            WHERE lower(u.fullName) LIKE lower(concat('%', :q, '%'))
               OR lower(u.email) LIKE lower(concat('%', :q, '%'))
            """)
    Page<AppUser> search(@Param("q") String q, Pageable pageable);

    @Query("""
            SELECT u FROM AppUser u JOIN u.roles r
            WHERE r.code = :role
              AND (lower(u.fullName) LIKE lower(concat('%', :q, '%'))
                   OR lower(u.email) LIKE lower(concat('%', :q, '%')))
            """)
    Page<AppUser> searchByRole(@Param("role") AppRole role, @Param("q") String q, Pageable pageable);
}

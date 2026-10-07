package org.institutonata.suprihub.tender.infra;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.institutonata.suprihub.tender.domain.RotationDisplacement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RotationDisplacementRepository extends JpaRepository<RotationDisplacement, Long> {

    Optional<RotationDisplacement> findFirstByApplicationIdOrderByDisplacedAtDesc(Long applicationId);

    /** Último deslocamento de cada fornecedor (conta como seleção na ordem da fila). */
    @Query("""
            SELECT d.applicationId, MAX(d.displacedAt) FROM RotationDisplacement d
            WHERE d.applicationId IN :ids GROUP BY d.applicationId
            """)
    List<Object[]> lastDisplacement(@Param("ids") Collection<Long> ids);
}

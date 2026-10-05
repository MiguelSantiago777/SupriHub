package org.institutonata.suprihub.messaging.infra;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.institutonata.suprihub.messaging.domain.OutboxEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Lote de eventos pendentes, travando as linhas. {@code SKIP LOCKED} permite vários
     * relays (várias instâncias) em paralelo sem publicar o mesmo evento duas vezes.
     */
    @Query(value = """
            SELECT * FROM outbox_event
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY occurred_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockNextBatch(@Param("now") Instant now, @Param("limit") int limit);

    Page<OutboxEvent> findByStatus(OutboxEvent.Status status, Pageable pageable);

    long countByStatus(OutboxEvent.Status status);

    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.status = 'PUBLISHED' AND e.publishedAt < :before")
    int deletePublishedBefore(@Param("before") Instant before);
}

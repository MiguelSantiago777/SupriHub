package org.institutonata.suprihub.judgment.infra;

import java.util.Optional;

import org.institutonata.suprihub.judgment.model.DrawRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DrawRecordRepository extends JpaRepository<DrawRecord, Long> {

    Optional<DrawRecord> findByJudgmentId(Long judgmentId);
}

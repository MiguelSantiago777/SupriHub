package org.institutonata.suprihub.judgment.infra;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.institutonata.suprihub.judgment.model.CoverOffer;
import org.institutonata.suprihub.judgment.model.JudgmentEnums.CoverStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoverOfferRepository extends JpaRepository<CoverOffer, Long> {

    List<CoverOffer> findByJudgmentIdOrderByPosition(Long judgmentId);

    Optional<CoverOffer> findByJudgmentIdAndStatus(Long judgmentId, CoverStatus status);

    Optional<CoverOffer> findByJudgmentIdAndApplicationId(Long judgmentId, Long applicationId);

    List<CoverOffer> findByStatusAndDueDateBefore(CoverStatus status, LocalDate date);
}

package org.institutonata.suprihub.contract.infra;

import java.time.Instant;
import java.util.List;

import org.institutonata.suprihub.contract.domain.DossierReview;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DossierReviewRepository extends JpaRepository<DossierReview, Long> {

    List<DossierReview> findByServiceOrderIdOrderByReviewedAtDesc(Long serviceOrderId);

    List<DossierReview> findByReviewedAtBetween(Instant from, Instant to);
}

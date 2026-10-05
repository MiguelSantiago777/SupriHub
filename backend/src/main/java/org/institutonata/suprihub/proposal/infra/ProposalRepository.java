package org.institutonata.suprihub.proposal.infra;

import java.util.List;
import java.util.Optional;

import org.institutonata.suprihub.proposal.domain.Proposal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProposalRepository extends JpaRepository<Proposal, Long> {

    Optional<Proposal> findByTenderIdAndApplicationId(Long tenderId, Long applicationId);

    List<Proposal> findByTenderIdOrderByTotalValueAscSubmittedAtAsc(Long tenderId);

    long countByTenderId(Long tenderId);

    List<Proposal> findByApplicationId(Long applicationId);
}

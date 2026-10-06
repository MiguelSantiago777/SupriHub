package org.institutonata.suprihub.contract.infra;

import java.util.List;

import org.institutonata.suprihub.contract.domain.ServiceOrderReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceOrderReceiptRepository extends JpaRepository<ServiceOrderReceipt, Long> {

    List<ServiceOrderReceipt> findByServiceOrderIdOrderByReceivedAt(Long serviceOrderId);
}

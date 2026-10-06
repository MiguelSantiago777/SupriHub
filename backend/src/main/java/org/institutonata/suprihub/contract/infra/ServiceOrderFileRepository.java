package org.institutonata.suprihub.contract.infra;

import java.util.List;

import org.institutonata.suprihub.contract.domain.ContractEnums.FileKind;
import org.institutonata.suprihub.contract.domain.ServiceOrderFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceOrderFileRepository extends JpaRepository<ServiceOrderFile, Long> {

    List<ServiceOrderFile> findByServiceOrderIdOrderByUploadedAt(Long serviceOrderId);

    boolean existsByServiceOrderIdAndKind(Long serviceOrderId, FileKind kind);
}

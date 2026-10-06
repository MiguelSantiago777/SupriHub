package org.institutonata.suprihub.sanction.infra;

import java.util.List;

import org.institutonata.suprihub.sanction.domain.SanctionFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SanctionFileRepository extends JpaRepository<SanctionFile, Long> {

    List<SanctionFile> findByProcessIdOrderByUploadedAt(Long processId);
}

package org.institutonata.suprihub.supplier.infra;

import java.util.List;
import java.util.Optional;

import org.institutonata.suprihub.supplier.domain.DocumentType;
import org.institutonata.suprihub.supplier.domain.SupplierDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierDocumentRepository extends JpaRepository<SupplierDocument, Long> {

    List<SupplierDocument> findByApplicationIdOrderByUploadedAt(Long applicationId);

    Optional<SupplierDocument> findByApplicationIdAndType(Long applicationId, DocumentType type);

    Optional<SupplierDocument> findByIdAndApplicationId(Long id, Long applicationId);
}

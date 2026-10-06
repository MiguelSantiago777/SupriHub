package org.institutonata.suprihub.contract.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.institutonata.suprihub.contract.domain.ContractEnums.FileKind;

/** Arquivo da contratação: evidência do recebimento, nota fiscal/RPA ou comprovante de pagamento. */
@Entity
@Table(name = "service_order_file")
public class ServiceOrderFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_order_id", nullable = false, updatable = false)
    private Long serviceOrderId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private FileKind kind;
    @Column(name = "file_name", nullable = false, updatable = false)
    private String fileName;
    @Column(name = "file_type", nullable = false, updatable = false)
    private String fileType;
    @Column(name = "file_size", nullable = false, updatable = false)
    private long fileSize;
    @Column(name = "file_sha256", nullable = false, updatable = false)
    private String fileSha256;
    @Column(name = "file_key", nullable = false, updatable = false)
    private String fileKey;
    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private Long uploadedBy;
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    protected ServiceOrderFile() {
    }

    public ServiceOrderFile(Long serviceOrderId, FileKind kind, String fileName, String fileType, long fileSize,
            String fileSha256, String fileKey, Long uploadedBy, Instant now) {
        this.serviceOrderId = serviceOrderId;
        this.kind = kind;
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.fileSha256 = fileSha256;
        this.fileKey = fileKey;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getServiceOrderId() {
        return serviceOrderId;
    }

    public FileKind getKind() {
        return kind;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getFileSha256() {
        return fileSha256;
    }

    public String getFileKey() {
        return fileKey;
    }

    public Long getUploadedBy() {
        return uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    @Override
    public String toString() {
        return "ServiceOrderFile#" + id;
    }
}

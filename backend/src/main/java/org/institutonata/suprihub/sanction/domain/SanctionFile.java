package org.institutonata.suprihub.sanction.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.institutonata.suprihub.sanction.domain.SanctionEnums.FileKind;

/** Arquivo do processo sancionador: prova, anexo da defesa ou do recurso. */
@Entity
@Table(name = "sanction_file")
public class SanctionFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "process_id", nullable = false, updatable = false)
    private Long processId;
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

    protected SanctionFile() {
    }

    public SanctionFile(Long processId, FileKind kind, String fileName, String fileType, long fileSize,
            String fileSha256, String fileKey, Long uploadedBy, Instant now) {
        this.processId = processId;
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

    public Long getProcessId() {
        return processId;
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

    public String getFileKey() {
        return fileKey;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}

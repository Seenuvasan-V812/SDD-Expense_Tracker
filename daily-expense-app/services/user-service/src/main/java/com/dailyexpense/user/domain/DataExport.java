package com.dailyexpense.user.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-user data export request. download_ref is NEVER logged (SEC-5 / T035).
 */
@Entity
@Table(name = "data_exports")
public class DataExport {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 25)
    private DataExportStatus status;

    @Column(name = "download_ref", length = 500)
    private String downloadRef; // NEVER included in log messages

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public DataExportStatus getStatus() { return status; }
    public void setStatus(DataExportStatus status) { this.status = status; }

    public String getDownloadRef() { return downloadRef; }
    public void setDownloadRef(String downloadRef) { this.downloadRef = downloadRef; }

    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

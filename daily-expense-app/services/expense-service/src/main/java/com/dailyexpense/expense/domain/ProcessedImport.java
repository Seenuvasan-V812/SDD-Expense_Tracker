package com.dailyexpense.expense.domain;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * T065 — Idempotency dedup record for CSV import.
 * PK is composite (idempotencyKey, userId) so different users can reuse the same key.
 */
@Entity
@Table(name = "processed_imports")
@IdClass(ProcessedImport.ImportKey.class)
public class ProcessedImport {

    @Id
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    @Column(name = "result_json", nullable = false, columnDefinition = "TEXT")
    private String resultJson;

    public ProcessedImport() {}

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }

    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }

    public static class ImportKey implements Serializable {
        private String idempotencyKey;
        private UUID userId;

        public ImportKey() {}
        public ImportKey(String idempotencyKey, UUID userId) {
            this.idempotencyKey = idempotencyKey;
            this.userId = userId;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ImportKey k)) return false;
            return Objects.equals(idempotencyKey, k.idempotencyKey) && Objects.equals(userId, k.userId);
        }
        @Override public int hashCode() { return Objects.hash(idempotencyKey, userId); }
    }
}

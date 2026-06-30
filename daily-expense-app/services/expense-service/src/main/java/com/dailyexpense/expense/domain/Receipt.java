package com.dailyexpense.expense.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * T060 — Receipt entity. 1:1 with Expense (uq_receipts_expense_id).
 * AL-4: never serialized; ReceiptResponse DTO only.
 * SEC-1: mimeType is the server-detected type, not the client-supplied Content-Type.
 */
@Entity
@Table(name = "receipts")
public class Receipt {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "expense_id", nullable = false, unique = true)
    private UUID expenseId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "mime_type", nullable = false, length = 30)
    private String mimeType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Receipt() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getExpenseId() { return expenseId; }
    public void setExpenseId(UUID expenseId) { this.expenseId = expenseId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

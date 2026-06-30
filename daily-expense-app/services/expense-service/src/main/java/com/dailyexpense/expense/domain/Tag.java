package com.dailyexpense.expense.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * T062 — Tag entity. Unique per (userId, name).
 * AL-4: never serialized; TagResponse DTO only.
 * Delete cascades via expense_tags ON DELETE CASCADE — Expenses are NOT deleted.
 */
@Entity
@Table(name = "tags")
public class Tag {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Tag() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

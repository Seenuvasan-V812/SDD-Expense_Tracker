package com.dailyexpense.user.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity — NEVER serialized to JSON (AL-4). Controllers use UserResponse DTO only.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 25)
    private UserStatus status;

    @Column(name = "preferred_currency", nullable = false, length = 3)
    private String preferredCurrency = "INR";

    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone = "Asia/Kolkata";

    @Column(name = "locale", nullable = false, length = 10)
    private String locale = "en-IN";

    @Column(name = "weekly_digest_enabled", nullable = false)
    private boolean weeklyDigestEnabled = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    public String getPreferredCurrency() { return preferredCurrency; }
    public void setPreferredCurrency(String preferredCurrency) { this.preferredCurrency = preferredCurrency; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }

    public boolean isWeeklyDigestEnabled() { return weeklyDigestEnabled; }
    public void setWeeklyDigestEnabled(boolean weeklyDigestEnabled) { this.weeklyDigestEnabled = weeklyDigestEnabled; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

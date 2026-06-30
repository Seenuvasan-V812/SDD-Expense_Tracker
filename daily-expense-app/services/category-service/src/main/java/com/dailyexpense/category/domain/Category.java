package com.dailyexpense.category.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * T041 — Category aggregate root.
 * user_id=NULL ⇒ DEFAULT (shared); user_id set ⇒ CUSTOM (owned). Never JSON-serialized (AL-4).
 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;     // null = DEFAULT

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 25)
    private CategoryType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 25)
    private CategoryOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_role", nullable = false, length = 25)
    private SystemCategoryRole systemRole;

    @Column(name = "icon", length = 100)
    private String icon;

    @Column(name = "color", length = 20)
    private String color;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public CategoryType getType() { return type; }
    public void setType(CategoryType type) { this.type = type; }

    public CategoryOrigin getOrigin() { return origin; }
    public void setOrigin(CategoryOrigin origin) { this.origin = origin; }

    public SystemCategoryRole getSystemRole() { return systemRole; }
    public void setSystemRole(SystemCategoryRole systemRole) { this.systemRole = systemRole; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public boolean isDefault() {
        return CategoryOrigin.DEFAULT == this.origin;
    }
}

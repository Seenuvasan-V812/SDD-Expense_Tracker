package com.dailyexpense.category.dto;

import java.util.UUID;

public record CategoryResponse(
    UUID categoryId,
    String name,
    String type,
    String origin,
    String systemRole,
    String icon,
    String color,
    boolean deletable   // false for DEFAULT (INV-9)
) {}

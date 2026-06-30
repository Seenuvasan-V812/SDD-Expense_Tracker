package com.dailyexpense.category.port;

import java.util.UUID;

/**
 * Internal port response for CategoryLookupPort (consumed by expense-service, budget-service).
 */
public record CategoryLookupResponse(
    UUID categoryId,
    String name,
    String type,
    String systemRole
) {}

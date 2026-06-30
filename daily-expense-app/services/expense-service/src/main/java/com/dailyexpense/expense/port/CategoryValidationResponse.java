package com.dailyexpense.expense.port;

import java.util.UUID;

/**
 * Result from CategoryLookupPort — validates category visibility, type, and system role.
 */
public record CategoryValidationResponse(
    UUID categoryId,
    String name,
    String type,
    String systemRole
) {
    public boolean isSavingsCategory() {
        return "SAVINGS".equals(systemRole);
    }

    public boolean isExpenseCompatible() {
        return "EXPENSE".equals(type) || "BOTH".equals(type);
    }
}

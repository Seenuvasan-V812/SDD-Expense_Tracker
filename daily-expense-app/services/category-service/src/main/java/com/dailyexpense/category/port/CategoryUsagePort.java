package com.dailyexpense.category.port;

import java.util.UUID;

/**
 * T045 — Anti-Corruption Port: checks whether a category has associated transactions.
 * Implemented by an HTTP adapter calling expense-service (AL-2, AL-1: no cross-service SQL).
 * Phase 1: stub returns false (expense-service not yet built).
 */
public interface CategoryUsagePort {

    /**
     * Returns true if the category is referenced by any expense or recurring expense.
     * NO cross-service DB access allowed (AL-1).
     */
    boolean isCategoryInUse(UUID categoryId);
}

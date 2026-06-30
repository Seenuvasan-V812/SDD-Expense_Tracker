package com.dailyexpense.expense.port;

import java.util.UUID;

/**
 * Anti-corruption port: validates a category is visible to the caller and meets type requirements.
 * Phase 1: implemented by CategoryLookupPortStub (always validates).
 * Phase 2: replaced by HTTP adapter calling /internal/categories/{id}/validate on category-service.
 * Throws ForbiddenOwnershipException (→403) when category is not visible to userId.
 * Throws IllegalArgumentException (→400) when category type is INCOME for an EXPENSE operation.
 */
public interface CategoryLookupPort {
    CategoryValidationResponse validate(UUID categoryId, UUID userId, String requiredType);
}

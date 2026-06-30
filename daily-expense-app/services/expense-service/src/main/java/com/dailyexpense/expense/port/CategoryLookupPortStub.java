package com.dailyexpense.expense.port;

import java.util.UUID;

/**
 * Phase 1 stub: always returns a valid EXPENSE/NONE category.
 * Not a @Component — registered by CategoryLookupConfig when category-service.base-url is absent.
 * @MockBean in integration tests replaces this stub automatically.
 */
public class CategoryLookupPortStub implements CategoryLookupPort {

    @Override
    public CategoryValidationResponse validate(UUID categoryId, UUID userId, String requiredType) {
        return new CategoryValidationResponse(categoryId, "Stub Category", "EXPENSE", "NONE");
    }
}

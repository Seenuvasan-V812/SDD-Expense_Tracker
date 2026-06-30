package com.dailyexpense.category.port;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Phase 1 stub — always returns false (no in-use categories).
 * Phase 2 replaces this with an HTTP adapter calling expense-service
 * (CategoryUsagePort contract: no cross-service DB access, AL-1).
 */
@Component
public class CategoryUsagePortStub implements CategoryUsagePort {

    @Override
    public boolean isCategoryInUse(UUID categoryId) {
        return false;
    }
}

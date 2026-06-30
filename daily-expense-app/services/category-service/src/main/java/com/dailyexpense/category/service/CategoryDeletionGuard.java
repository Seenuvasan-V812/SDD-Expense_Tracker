package com.dailyexpense.category.service;

import com.dailyexpense.category.port.CategoryUsagePort;
import com.dailyexpense.shared.exception.BusinessConflictException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * T045 — Guards category deletion.
 * In-use check goes through CategoryUsagePort (AL-2) — never a DB FK (REQ-CAT-005, INV-9).
 */
@Service
public class CategoryDeletionGuard {

    private final CategoryUsagePort categoryUsagePort;

    public CategoryDeletionGuard(CategoryUsagePort categoryUsagePort) {
        this.categoryUsagePort = categoryUsagePort;
    }

    public void assertCanDelete(UUID categoryId) {
        if (categoryUsagePort.isCategoryInUse(categoryId)) {
            throw new BusinessConflictException(
                "Category has associated transactions; reassign them first");
        }
    }
}

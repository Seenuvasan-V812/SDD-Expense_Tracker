package com.dailyexpense.category.port;

import com.dailyexpense.category.domain.Category;
import com.dailyexpense.category.domain.CategoryType;
import com.dailyexpense.category.repository.CategoryRepository;
import com.dailyexpense.shared.exception.ForbiddenOwnershipException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * T046 — Internal port endpoint for cross-service category validation (AL-2).
 * Consumed by expense-service and budget-service — NOT part of the public API.
 *
 * Contract:
 * - DEFAULT categories are visible to all users.
 * - CUSTOM categories visible only to their owner (foreign → 403).
 * - INCOME-type category rejected when requiredType=EXPENSE (→ 403).
 * - Non-existent category → 403 (403-never-404, INV-1/SEC-3).
 */
@RestController
@RequestMapping("/internal/categories")
public class CategoryLookupController {

    private final CategoryRepository categoryRepository;

    public CategoryLookupController(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /**
     * Validates that category {id} is visible to {userId} and permits {requiredType} transactions.
     * Returns 200 + CategoryLookupResponse if valid, 403 otherwise.
     *
     * requiredType: EXPENSE or INCOME — the type the caller intends to create.
     */
    @GetMapping("/{id}/validate")
    public ResponseEntity<CategoryLookupResponse> validate(
            @PathVariable UUID id,
            @RequestParam UUID userId,
            @RequestParam(required = false) CategoryType requiredType) {

        Category cat = categoryRepository.findById(id)
            .orElseThrow(ForbiddenOwnershipException::new); // 403-never-404

        // Visibility: DEFAULT visible to all; CUSTOM visible only to owner
        if (!cat.isDefault() && !userId.equals(cat.getUserId())) {
            throw new ForbiddenOwnershipException(); // foreign custom → 403
        }

        // Type check: INCOME-type rejected for EXPENSE use
        if (requiredType == CategoryType.EXPENSE && cat.getType() == CategoryType.INCOME) {
            throw new ForbiddenOwnershipException(); // type mismatch → 403
        }
        // Symmetric: EXPENSE-type rejected for INCOME use
        if (requiredType == CategoryType.INCOME && cat.getType() == CategoryType.EXPENSE) {
            throw new ForbiddenOwnershipException();
        }

        return ResponseEntity.ok(new CategoryLookupResponse(
            cat.getId(),
            cat.getName(),
            cat.getType().name(),
            cat.getSystemRole().name()
        ));
    }
}

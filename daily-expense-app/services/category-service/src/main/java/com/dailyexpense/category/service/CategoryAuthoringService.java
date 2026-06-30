package com.dailyexpense.category.service;

import com.dailyexpense.category.domain.Category;
import com.dailyexpense.category.domain.CategoryOrigin;
import com.dailyexpense.category.domain.CategoryType;
import com.dailyexpense.category.domain.SystemCategoryRole;
import com.dailyexpense.category.dto.CategoryResponse;
import com.dailyexpense.category.dto.CreateCategoryRequest;
import com.dailyexpense.category.dto.UpdateCategoryRequest;
import com.dailyexpense.category.repository.CategoryRepository;
import com.dailyexpense.shared.api.PageResponse;
import com.dailyexpense.shared.exception.BusinessConflictException;
import com.dailyexpense.shared.exception.ForbiddenOwnershipException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * T043/T044 — Custom category CRUD + DEFAULT protection (REQ-CAT-002/003).
 * 403-never-404 for foreign custom categories (INV-1/SEC-3).
 */
@Service
public class CategoryAuthoringService {

    private static final Logger log = LoggerFactory.getLogger(CategoryAuthoringService.class);

    private final CategoryRepository categoryRepository;
    private final CategoryDeletionGuard deletionGuard;

    public CategoryAuthoringService(CategoryRepository categoryRepository,
                                    CategoryDeletionGuard deletionGuard) {
        this.categoryRepository = categoryRepository;
        this.deletionGuard = deletionGuard;
    }

    // ── T043: List (DEFAULT + own CUSTOM), optional type filter ──────────────

    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> listVisible(UUID callerId, CategoryType typeFilter,
                                                      int page, int size) {
        Page<Category> categories = categoryRepository.findVisibleToUser(
            callerId, typeFilter, PageRequest.of(page, size));
        return PageResponse.ofSpringPage(categories.map(this::toResponse));
    }

    // ── T043: Get single ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CategoryResponse getOne(UUID callerId, UUID categoryId) {
        Category cat = findOrForbid(categoryId);
        assertVisible(cat, callerId);
        return toResponse(cat);
    }

    // ── T043: Create custom category ──────────────────────────────────────────

    @Transactional
    public Category create(UUID callerId, CreateCategoryRequest request) {
        if (categoryRepository.findByNameAndUserId(request.name(), callerId).isPresent()) {
            throw new BusinessConflictException(
                "A category named '" + request.name() + "' already exists for this owner");
        }
        Category cat = new Category();
        cat.setId(UUID.randomUUID());
        cat.setUserId(callerId);
        cat.setName(request.name());
        cat.setType(request.type());
        cat.setOrigin(CategoryOrigin.CUSTOM);
        cat.setSystemRole(SystemCategoryRole.NONE);
        cat.setIcon(request.icon());
        cat.setColor(request.color());
        cat.setCreatedAt(Instant.now());
        cat.setUpdatedAt(Instant.now());
        Category saved = categoryRepository.save(cat);
        log.info("Category created id={} userId={}", saved.getId(), callerId);
        return saved;
    }

    // ── T044: Update custom category (DEFAULT → 403) ──────────────────────────

    @Transactional
    public CategoryResponse update(UUID callerId, UUID categoryId, UpdateCategoryRequest request) {
        Category cat = findOrForbid(categoryId);

        if (cat.isDefault()) {
            throw new ForbiddenOwnershipException(); // DEFAULT not editable → 403
        }
        assertOwner(cat, callerId);

        // Duplicate name check (excluding self)
        categoryRepository.findByNameAndUserId(request.name(), callerId)
            .filter(existing -> !existing.getId().equals(categoryId))
            .ifPresent(dup -> { throw new BusinessConflictException(
                "A category named '" + request.name() + "' already exists for this owner"); });

        cat.setName(request.name());
        cat.setType(request.type());
        cat.setIcon(request.icon());
        cat.setColor(request.color());
        cat.setUpdatedAt(Instant.now());
        return toResponse(categoryRepository.save(cat));
    }

    // ── T044/T045: Delete custom category (DEFAULT → 409, in-use → 409, foreign → 403) ──

    @Transactional
    public void delete(UUID callerId, UUID categoryId) {
        Category cat = findOrForbid(categoryId);

        if (cat.isDefault()) {
            throw new BusinessConflictException("Default categories cannot be deleted"); // → 409
        }
        assertOwner(cat, callerId);
        deletionGuard.assertCanDelete(categoryId); // in-use → 409

        categoryRepository.delete(cat);
        log.info("Category deleted id={} userId={}", categoryId, callerId);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Category findOrForbid(UUID categoryId) {
        return categoryRepository.findById(categoryId)
            .orElseThrow(ForbiddenOwnershipException::new); // 403, never 404 (INV-1/SEC-3)
    }

    private void assertOwner(Category cat, UUID callerId) {
        if (!callerId.equals(cat.getUserId())) {
            throw new ForbiddenOwnershipException(); // foreign custom → 403
        }
    }

    private void assertVisible(Category cat, UUID callerId) {
        if (!cat.isDefault() && !callerId.equals(cat.getUserId())) {
            throw new ForbiddenOwnershipException(); // foreign custom → 403
        }
    }

    public CategoryResponse toResponse(Category cat) {
        return new CategoryResponse(
            cat.getId(),
            cat.getName(),
            cat.getType().name(),
            cat.getOrigin().name(),
            cat.getSystemRole().name(),
            cat.getIcon(),
            cat.getColor(),
            !cat.isDefault() // deletable=false for DEFAULT (INV-9)
        );
    }
}

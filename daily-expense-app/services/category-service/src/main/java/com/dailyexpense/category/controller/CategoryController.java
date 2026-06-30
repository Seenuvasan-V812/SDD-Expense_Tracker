package com.dailyexpense.category.controller;

import com.dailyexpense.category.domain.Category;
import com.dailyexpense.category.domain.CategoryType;
import com.dailyexpense.category.dto.CategoryResponse;
import com.dailyexpense.category.dto.CreateCategoryRequest;
import com.dailyexpense.category.dto.UpdateCategoryRequest;
import com.dailyexpense.category.service.CategoryAuthoringService;
import com.dailyexpense.shared.api.PageResponse;
import com.dailyexpense.shared.security.CallerContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

/**
 * T047 — Category public API: GET (list + single), POST, PUT, DELETE.
 * Identity from JWT CallerContext (AL-5). 403-never-404 (INV-1/SEC-3).
 */
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryAuthoringService authoringService;

    public CategoryController(CategoryAuthoringService authoringService) {
        this.authoringService = authoringService;
    }

    // T047 — GET /api/v1/categories?type=&page=&size=
    @GetMapping
    public ResponseEntity<PageResponse<CategoryResponse>> list(
            @AuthenticationPrincipal CallerContext caller,
            @RequestParam(required = false) CategoryType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int cappedSize = Math.min(size, 100); // API-2: max 100
        return ResponseEntity.ok(
            authoringService.listVisible(caller.userId(), type, page, cappedSize));
    }

    // T047 — GET /api/v1/categories/{id}
    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> getOne(
            @AuthenticationPrincipal CallerContext caller,
            @PathVariable UUID id) {
        return ResponseEntity.ok(authoringService.getOne(caller.userId(), id));
    }

    // T043 — POST /api/v1/categories → 201 + Location; dup name → 409
    @PostMapping
    public ResponseEntity<Void> create(
            @AuthenticationPrincipal CallerContext caller,
            @Valid @RequestBody CreateCategoryRequest request) {
        Category created = authoringService.create(caller.userId(), request);
        URI location = URI.create("/api/v1/categories/" + created.getId());
        return ResponseEntity.created(location).build();
    }

    // T044 — PUT /api/v1/categories/{id}: DEFAULT → 403; foreign CUSTOM → 403
    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> update(
            @AuthenticationPrincipal CallerContext caller,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCategoryRequest request) {
        return ResponseEntity.ok(authoringService.update(caller.userId(), id, request));
    }

    // T044/T045 — DELETE /api/v1/categories/{id}: DEFAULT → 409; in-use → 409; foreign → 403
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CallerContext caller,
            @PathVariable UUID id) {
        authoringService.delete(caller.userId(), id);
        return ResponseEntity.noContent().build();
    }
}

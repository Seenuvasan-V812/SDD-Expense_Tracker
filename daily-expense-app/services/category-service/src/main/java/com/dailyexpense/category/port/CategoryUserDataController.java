package com.dailyexpense.category.port;

import com.dailyexpense.category.domain.Category;
import com.dailyexpense.category.repository.CategoryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * T114 — Internal endpoint: returns all categories visible to a given userId.
 * Guarded by /internal/** permitAll in CategoryServiceSecurityConfig (service-token boundary).
 * No expense_db / savings_goal_db / budget_db SQL (AL-1).
 */
@RestController
@RequestMapping("/internal/users")
public class CategoryUserDataController {

    private final CategoryRepository categoryRepository;

    public CategoryUserDataController(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /**
     * Returns all DEFAULT categories + all CUSTOM categories owned by userId.
     * Empty list (not error) when user has no custom categories.
     */
    @GetMapping("/{userId}/export-data")
    public ResponseEntity<Map<String, Object>> exportData(@PathVariable UUID userId) {
        List<Category> categories = categoryRepository
                .findVisibleToUser(userId, null, PageRequest.of(0, Integer.MAX_VALUE))
                .getContent();

        List<Map<String, Object>> items = categories.stream()
                .map(c -> Map.<String, Object>of(
                        "id", c.getId().toString(),
                        "name", c.getName(),
                        "type", c.getType().name(),
                        "origin", c.getOrigin().name(),
                        "systemRole", c.getSystemRole().name()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("categories", items));
    }
}

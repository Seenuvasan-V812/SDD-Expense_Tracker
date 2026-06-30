package com.dailyexpense.category.repository;

import com.dailyexpense.category.domain.Category;
import com.dailyexpense.category.domain.CategoryOrigin;
import com.dailyexpense.category.domain.CategoryType;
import com.dailyexpense.category.domain.SystemCategoryRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    /** All DEFAULT categories + caller's CUSTOM, optionally filtered by type. */
    @Query("SELECT c FROM Category c WHERE (c.userId IS NULL OR c.userId = :userId) " +
           "AND (:type IS NULL OR c.type = :type) ORDER BY c.origin ASC, c.name ASC")
    Page<Category> findVisibleToUser(@Param("userId") UUID userId,
                                     @Param("type") CategoryType type,
                                     Pageable pageable);

    Optional<Category> findByNameAndUserIdIsNull(String name);

    Optional<Category> findByNameAndUserId(String name, UUID userId);

    Optional<Category> findBySystemRole(SystemCategoryRole systemRole);

    boolean existsByOrigin(CategoryOrigin origin);

    long countByOrigin(CategoryOrigin origin);
}

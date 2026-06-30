package com.dailyexpense.category.dto;

import com.dailyexpense.category.domain.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
    @NotBlank @Size(max = 100) String name,
    @NotNull CategoryType type,
    @Size(max = 100) String icon,
    @Size(max = 20) String color
) {}

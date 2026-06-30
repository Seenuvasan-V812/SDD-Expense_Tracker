package com.dailyexpense.expense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTagRequest(
    @NotBlank @Size(max = 100) String name
) {}

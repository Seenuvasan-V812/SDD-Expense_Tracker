package com.dailyexpense.budget.port;

import java.util.UUID;

/**
 * AL-2 — Anti-corruption port: validates that a categoryId exists in the category-service.
 * Budget-service MUST NOT query category_db directly.
 */
public interface CategoryLookupPort {

    /** Returns true if the category exists and is accessible (HTTP 200), false otherwise. */
    boolean exists(UUID categoryId, String bearerToken);
}

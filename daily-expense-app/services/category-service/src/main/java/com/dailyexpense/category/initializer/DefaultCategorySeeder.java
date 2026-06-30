package com.dailyexpense.category.initializer;

import com.dailyexpense.category.domain.Category;
import com.dailyexpense.category.domain.CategoryOrigin;
import com.dailyexpense.category.domain.CategoryType;
import com.dailyexpense.category.domain.SystemCategoryRole;
import com.dailyexpense.category.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * T042 — Seeds 11 DEFAULT categories on first boot; idempotent on re-run (REQ-CAT-001).
 * Savings Category has system_role=SAVINGS, user_id=NULL — non-deletable (INV-9).
 */
@Component
public class DefaultCategorySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultCategorySeeder.class);

    private final CategoryRepository categoryRepository;

    public DefaultCategorySeeder(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (categoryRepository.countByOrigin(CategoryOrigin.DEFAULT) >= 11) {
            log.info("DefaultCategorySeeder: {} DEFAULT categories already present — skipping",
                     categoryRepository.countByOrigin(CategoryOrigin.DEFAULT));
            return;
        }
        seedDefaults();
        log.info("DefaultCategorySeeder: seeded 11 DEFAULT categories");
    }

    private void seedDefaults() {
        List<SeedEntry> seeds = List.of(
            new SeedEntry("Food",                          CategoryType.EXPENSE, SystemCategoryRole.NONE,    "🍽️", "#FF6B6B"),
            new SeedEntry("Transport",                     CategoryType.EXPENSE, SystemCategoryRole.NONE,    "🚗", "#4ECDC4"),
            new SeedEntry("Housing",                       CategoryType.EXPENSE, SystemCategoryRole.NONE,    "🏠", "#45B7D1"),
            new SeedEntry("Health",                        CategoryType.EXPENSE, SystemCategoryRole.NONE,    "🏥", "#96CEB4"),
            new SeedEntry("Entertainment",                 CategoryType.EXPENSE, SystemCategoryRole.NONE,    "🎬", "#FFEAA7"),
            new SeedEntry("Shopping",                      CategoryType.EXPENSE, SystemCategoryRole.NONE,    "🛍️", "#DDA0DD"),
            new SeedEntry("Education",                     CategoryType.EXPENSE, SystemCategoryRole.NONE,    "📚", "#98D8C8"),
            new SeedEntry("Savings",                       CategoryType.BOTH,    SystemCategoryRole.SAVINGS, "💰", "#F7DC6F"),
            new SeedEntry("Loans",                         CategoryType.EXPENSE, SystemCategoryRole.NONE,    "🏦", "#E8B4B8"),
            new SeedEntry("Credit & Debit",                CategoryType.BOTH,    SystemCategoryRole.NONE,    "💳", "#A8D8EA"),
            new SeedEntry("Third Party Payments & Other",  CategoryType.EXPENSE, SystemCategoryRole.NONE,    "📲", "#B0C4DE")
        );

        for (SeedEntry seed : seeds) {
            if (categoryRepository.findByNameAndUserIdIsNull(seed.name()).isEmpty()) {
                Category cat = new Category();
                cat.setId(UUID.randomUUID());
                cat.setUserId(null);
                cat.setName(seed.name());
                cat.setType(seed.type());
                cat.setOrigin(CategoryOrigin.DEFAULT);
                cat.setSystemRole(seed.role());
                cat.setIcon(seed.icon());
                cat.setColor(seed.color());
                cat.setCreatedAt(Instant.now());
                cat.setUpdatedAt(Instant.now());
                categoryRepository.save(cat);
            }
        }
    }

    private record SeedEntry(String name, CategoryType type, SystemCategoryRole role,
                             String icon, String color) {}
}

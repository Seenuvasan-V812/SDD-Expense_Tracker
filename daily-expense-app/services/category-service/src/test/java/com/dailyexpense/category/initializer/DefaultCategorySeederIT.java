package com.dailyexpense.category.initializer;

import com.dailyexpense.category.domain.CategoryOrigin;
import com.dailyexpense.category.domain.SystemCategoryRole;
import com.dailyexpense.category.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T042 — DefaultCategorySeeder: seeds ≥11 DEFAULT categories including Savings; idempotent.
 */
@SpringBootTest(
    properties = {
        "jwt.secret=test-secret-key-for-category-seeder-test-at-least-32-chars",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
    }
)
@ActiveProfiles("test")
@Testcontainers
class DefaultCategorySeederIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("category_db")
            .withUsername("category_user")
            .withPassword("category_pass");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    DefaultCategorySeeder seeder;

    @Test
    void seeds_atLeast11DefaultCategories() {
        long count = categoryRepository.countByOrigin(CategoryOrigin.DEFAULT);
        assertThat(count).isGreaterThanOrEqualTo(11);
    }

    @Test
    void seeds_savingsCategoryWithSystemRoleSavings() {
        var savings = categoryRepository.findBySystemRole(SystemCategoryRole.SAVINGS);
        assertThat(savings).isPresent();
        assertThat(savings.get().getOrigin()).isEqualTo(CategoryOrigin.DEFAULT);
        assertThat(savings.get().getUserId()).isNull(); // user_id=NULL for DEFAULT
    }

    @Test
    void seeds_savingsCategoryName() {
        var savings = categoryRepository.findBySystemRole(SystemCategoryRole.SAVINGS);
        assertThat(savings.get().getName()).isEqualTo("Savings");
    }

    @Test
    void seeds_allDefaultCategoriesHaveNullUserId() {
        Integer withNonNullOwner = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM categories WHERE origin='DEFAULT' AND user_id IS NOT NULL",
            Integer.class);
        assertThat(withNonNullOwner).isZero();
    }

    @Test
    void seeds_isIdempotent_rerunDoesNotDuplicate() {
        long countBefore = categoryRepository.countByOrigin(CategoryOrigin.DEFAULT);
        seeder.run(null); // second run
        long countAfter = categoryRepository.countByOrigin(CategoryOrigin.DEFAULT);
        assertThat(countAfter).isEqualTo(countBefore);
    }

    @Test
    void migration_createsCategoriesTable() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name='categories'",
            Integer.class);
        assertThat(count).isGreaterThan(0);
    }

    @Test
    void migration_partialSavingsIndexExists() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM pg_indexes WHERE tablename='categories' AND indexname='idx_categories_system_role'",
            Integer.class);
        assertThat(count).isGreaterThan(0);
    }

    @Test
    void migration_defaultNoOwnerConstraintExists() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.table_constraints " +
            "WHERE table_name='categories' AND constraint_name='ck_categories_default_no_owner'",
            Integer.class);
        assertThat(count).isGreaterThan(0);
    }
}

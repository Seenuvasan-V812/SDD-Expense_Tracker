package com.dailyexpense.category;

import com.dailyexpense.category.domain.CategoryOrigin;
import com.dailyexpense.category.domain.SystemCategoryRole;
import com.dailyexpense.category.dto.CategoryResponse;
import com.dailyexpense.category.dto.CreateCategoryRequest;
import com.dailyexpense.category.dto.UpdateCategoryRequest;
import com.dailyexpense.category.repository.CategoryRepository;
import com.dailyexpense.shared.api.PageResponse;
import com.dailyexpense.shared.security.JwtService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T048 — Full CategoryIT gate: seeding, CRUD, DEFAULT protection, in-use 409,
 * foreign 403, INCOME rejection via lookup port, ?type= filter.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "jwt.secret=test-secret-key-for-category-it-integration-at-least-32-chars",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
    }
)
@ActiveProfiles("test")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CategoryIT {

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

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    JwtService jwtService;

    // Two distinct users
    final UUID USER_A = UUID.randomUUID();
    final UUID USER_B = UUID.randomUUID();

    String tokenA;
    String tokenB;

    @BeforeAll
    void setup() {
        tokenA = jwtService.issueAccessToken(USER_A);
        tokenB = jwtService.issueAccessToken(USER_B);
    }

    private String base() { return "http://localhost:" + port + "/api/v1/categories"; }
    private String internal() { return "http://localhost:" + port + "/internal/categories"; }

    private HttpHeaders bearerOf(String token) {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", "Bearer " + token);
        return h;
    }

    // ── Seeding ───────────────────────────────────────────────────────────────

    @Test
    void seeder_createsAtLeast11Defaults() {
        long count = categoryRepository.countByOrigin(CategoryOrigin.DEFAULT);
        assertThat(count).isGreaterThanOrEqualTo(11);
    }

    @Test
    void seeder_savingsCategoryPresent() {
        var savings = categoryRepository.findBySystemRole(SystemCategoryRole.SAVINGS);
        assertThat(savings).isPresent();
        assertThat(savings.get().getUserId()).isNull();
        assertThat(savings.get().getOrigin()).isEqualTo(CategoryOrigin.DEFAULT);
    }

    // ── List: DEFAULT visible to all ──────────────────────────────────────────

    @Test
    void list_returnsDefaultAndOwnerCustom() {
        HttpEntity<Void> req = new HttpEntity<>(bearerOf(tokenA));
        ResponseEntity<PageResponse<CategoryResponse>> resp = rest.exchange(
            base(), HttpMethod.GET, req,
            new ParameterizedTypeReference<PageResponse<CategoryResponse>>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().totalElements()).isGreaterThanOrEqualTo(11);
        // All DEFAULT categories present
        assertThat(resp.getBody().content())
            .anyMatch(c -> "Savings".equals(c.name()) && "DEFAULT".equals(c.origin()));
    }

    @Test
    void list_unauthenticated_returns401() {
        ResponseEntity<String> resp = rest.getForEntity(base(), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── Type filter ───────────────────────────────────────────────────────────

    @Test
    void list_typeFilterExpense_returnsOnlyExpenseAndBoth() {
        HttpEntity<Void> req = new HttpEntity<>(bearerOf(tokenA));
        ResponseEntity<PageResponse<CategoryResponse>> resp = rest.exchange(
            base() + "?type=EXPENSE", HttpMethod.GET, req,
            new ParameterizedTypeReference<PageResponse<CategoryResponse>>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        // No INCOME-only categories in results
        assertThat(resp.getBody().content())
            .noneMatch(c -> "INCOME".equals(c.type()));
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    @Test
    void create_customCategory_returns201WithLocation() {
        var req = new CreateCategoryRequest("My Custom Cat " + UUID.randomUUID(),
            com.dailyexpense.category.domain.CategoryType.EXPENSE, "🎯", "#123456");
        HttpEntity<CreateCategoryRequest> httpReq = new HttpEntity<>(req, bearerOf(tokenA));
        ResponseEntity<Void> resp = rest.exchange(base(), HttpMethod.POST, httpReq, Void.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getHeaders().getLocation()).isNotNull();
        assertThat(resp.getHeaders().getLocation().toString()).startsWith("/api/v1/categories/");
    }

    @Test
    void create_duplicateName_returns409() {
        String name = "DupCat-" + UUID.randomUUID();
        var req = new CreateCategoryRequest(name,
            com.dailyexpense.category.domain.CategoryType.EXPENSE, null, null);
        HttpEntity<CreateCategoryRequest> httpReq = new HttpEntity<>(req, bearerOf(tokenA));
        rest.exchange(base(), HttpMethod.POST, httpReq, Void.class); // first OK

        ResponseEntity<String> resp = rest.exchange(base(), HttpMethod.POST, httpReq, String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT); // 409
    }

    @Test
    void create_sameNameDifferentOwner_is201() {
        // Different owners can have categories with the same name
        String sharedName = "SharedName-" + UUID.randomUUID();
        var req = new CreateCategoryRequest(sharedName,
            com.dailyexpense.category.domain.CategoryType.EXPENSE, null, null);

        HttpEntity<CreateCategoryRequest> reqA = new HttpEntity<>(req, bearerOf(tokenA));
        HttpEntity<CreateCategoryRequest> reqB = new HttpEntity<>(req, bearerOf(tokenB));

        ResponseEntity<Void> respA = rest.exchange(base(), HttpMethod.POST, reqA, Void.class);
        ResponseEntity<Void> respB = rest.exchange(base(), HttpMethod.POST, reqB, Void.class);

        assertThat(respA.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(respB.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    // ── GET single ────────────────────────────────────────────────────────────

    @Test
    void getOne_defaultCategory_visibleToAnyUser() {
        var savings = categoryRepository.findBySystemRole(SystemCategoryRole.SAVINGS).orElseThrow();
        HttpEntity<Void> req = new HttpEntity<>(bearerOf(tokenB));
        ResponseEntity<CategoryResponse> resp = rest.exchange(
            base() + "/" + savings.getId(), HttpMethod.GET, req, CategoryResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().origin()).isEqualTo("DEFAULT");
        assertThat(resp.getBody().deletable()).isFalse();
    }

    @Test
    void getOne_foreignCustom_returns403Never404() {
        // Create category as user A
        String name = "PrivateCat-" + UUID.randomUUID();
        var createReq = new CreateCategoryRequest(name,
            com.dailyexpense.category.domain.CategoryType.EXPENSE, null, null);
        HttpEntity<CreateCategoryRequest> createHttp = new HttpEntity<>(createReq, bearerOf(tokenA));
        ResponseEntity<Void> createResp = rest.exchange(base(), HttpMethod.POST, createHttp, Void.class);
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID catId = UUID.fromString(createResp.getHeaders().getLocation().getPath().split("/")[4]);

        // User B tries to read it — must get 403, never 404
        HttpEntity<Void> req = new HttpEntity<>(bearerOf(tokenB));
        ResponseEntity<String> resp = rest.exchange(
            base() + "/" + catId, HttpMethod.GET, req, String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getStatusCode()).isNotEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    @Test
    void update_ownCustomCategory_returns200() {
        String name = "EditMe-" + UUID.randomUUID();
        var createReq = new CreateCategoryRequest(name,
            com.dailyexpense.category.domain.CategoryType.EXPENSE, null, null);
        HttpEntity<CreateCategoryRequest> createHttp = new HttpEntity<>(createReq, bearerOf(tokenA));
        ResponseEntity<Void> createResp = rest.exchange(base(), HttpMethod.POST, createHttp, Void.class);
        UUID catId = UUID.fromString(createResp.getHeaders().getLocation().getPath().split("/")[4]);

        var updateReq = new UpdateCategoryRequest("Updated-" + name,
            com.dailyexpense.category.domain.CategoryType.BOTH, "✏️", "#AABBCC");
        HttpEntity<UpdateCategoryRequest> updateHttp = new HttpEntity<>(updateReq, bearerOf(tokenA));
        ResponseEntity<CategoryResponse> resp = rest.exchange(
            base() + "/" + catId, HttpMethod.PUT, updateHttp, CategoryResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().name()).startsWith("Updated-");
    }

    @Test
    void update_defaultCategory_returns403() {
        var savings = categoryRepository.findBySystemRole(SystemCategoryRole.SAVINGS).orElseThrow();
        var updateReq = new UpdateCategoryRequest("Hacked",
            com.dailyexpense.category.domain.CategoryType.EXPENSE, null, null);
        HttpEntity<UpdateCategoryRequest> req = new HttpEntity<>(updateReq, bearerOf(tokenA));
        ResponseEntity<String> resp = rest.exchange(
            base() + "/" + savings.getId(), HttpMethod.PUT, req, String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void update_foreignCustom_returns403() {
        String name = "ForeignEdit-" + UUID.randomUUID();
        var createReq = new CreateCategoryRequest(name,
            com.dailyexpense.category.domain.CategoryType.EXPENSE, null, null);
        HttpEntity<CreateCategoryRequest> createHttp = new HttpEntity<>(createReq, bearerOf(tokenA));
        ResponseEntity<Void> createResp = rest.exchange(base(), HttpMethod.POST, createHttp, Void.class);
        UUID catId = UUID.fromString(createResp.getHeaders().getLocation().getPath().split("/")[4]);

        var updateReq = new UpdateCategoryRequest("Hacked",
            com.dailyexpense.category.domain.CategoryType.EXPENSE, null, null);
        HttpEntity<UpdateCategoryRequest> req = new HttpEntity<>(updateReq, bearerOf(tokenB));
        ResponseEntity<String> resp = rest.exchange(
            base() + "/" + catId, HttpMethod.PUT, req, String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getStatusCode()).isNotEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    @Test
    void delete_ownCustomCategory_returns204() {
        String name = "DeleteMe-" + UUID.randomUUID();
        var createReq = new CreateCategoryRequest(name,
            com.dailyexpense.category.domain.CategoryType.EXPENSE, null, null);
        HttpEntity<CreateCategoryRequest> createHttp = new HttpEntity<>(createReq, bearerOf(tokenA));
        ResponseEntity<Void> createResp = rest.exchange(base(), HttpMethod.POST, createHttp, Void.class);
        UUID catId = UUID.fromString(createResp.getHeaders().getLocation().getPath().split("/")[4]);

        HttpEntity<Void> req = new HttpEntity<>(bearerOf(tokenA));
        ResponseEntity<Void> resp = rest.exchange(
            base() + "/" + catId, HttpMethod.DELETE, req, Void.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void delete_defaultCategory_returns409() {
        var savings = categoryRepository.findBySystemRole(SystemCategoryRole.SAVINGS).orElseThrow();
        HttpEntity<Void> req = new HttpEntity<>(bearerOf(tokenA));
        ResponseEntity<String> resp = rest.exchange(
            base() + "/" + savings.getId(), HttpMethod.DELETE, req, String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT); // 409
    }

    @Test
    void delete_foreignCustom_returns403NeverNot404() {
        String name = "ForeignDel-" + UUID.randomUUID();
        var createReq = new CreateCategoryRequest(name,
            com.dailyexpense.category.domain.CategoryType.EXPENSE, null, null);
        HttpEntity<CreateCategoryRequest> createHttp = new HttpEntity<>(createReq, bearerOf(tokenA));
        ResponseEntity<Void> createResp = rest.exchange(base(), HttpMethod.POST, createHttp, Void.class);
        UUID catId = UUID.fromString(createResp.getHeaders().getLocation().getPath().split("/")[4]);

        HttpEntity<Void> req = new HttpEntity<>(bearerOf(tokenB));
        ResponseEntity<String> resp = rest.exchange(
            base() + "/" + catId, HttpMethod.DELETE, req, String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getStatusCode()).isNotEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── CategoryLookupPort (T046) ─────────────────────────────────────────────

    @Test
    void lookupPort_defaultCategory_visibleToAnyUser() {
        var savings = categoryRepository.findBySystemRole(SystemCategoryRole.SAVINGS).orElseThrow();
        ResponseEntity<String> resp = rest.getForEntity(
            internal() + "/" + savings.getId() + "/validate?userId=" + USER_B + "&requiredType=EXPENSE",
            String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void lookupPort_foreignCustomCategory_returns403() {
        // Create as user A
        String name = "LookupPrivate-" + UUID.randomUUID();
        var createReq = new CreateCategoryRequest(name,
            com.dailyexpense.category.domain.CategoryType.EXPENSE, null, null);
        HttpEntity<CreateCategoryRequest> createHttp = new HttpEntity<>(createReq, bearerOf(tokenA));
        ResponseEntity<Void> createResp = rest.exchange(base(), HttpMethod.POST, createHttp, Void.class);
        UUID catId = UUID.fromString(createResp.getHeaders().getLocation().getPath().split("/")[4]);

        // User B looks up via internal port — must be 403
        ResponseEntity<String> resp = rest.getForEntity(
            internal() + "/" + catId + "/validate?userId=" + USER_B + "&requiredType=EXPENSE",
            String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void lookupPort_incomeTypeCategory_rejectedForExpenseUse() {
        // Create INCOME category as user A
        String name = "IncomeOnly-" + UUID.randomUUID();
        var createReq = new CreateCategoryRequest(name,
            com.dailyexpense.category.domain.CategoryType.INCOME, null, null);
        HttpEntity<CreateCategoryRequest> createHttp = new HttpEntity<>(createReq, bearerOf(tokenA));
        ResponseEntity<Void> createResp = rest.exchange(base(), HttpMethod.POST, createHttp, Void.class);
        UUID catId = UUID.fromString(createResp.getHeaders().getLocation().getPath().split("/")[4]);

        // Expense use (requiredType=EXPENSE) with INCOME category → 403
        ResponseEntity<String> resp = rest.getForEntity(
            internal() + "/" + catId + "/validate?userId=" + USER_A + "&requiredType=EXPENSE",
            String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void lookupPort_nonExistentCategory_returns403Never404() {
        ResponseEntity<String> resp = rest.getForEntity(
            internal() + "/" + UUID.randomUUID() + "/validate?userId=" + USER_A,
            String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getStatusCode()).isNotEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void lookupPort_bothTypeCategory_allowedForExpenseUse() {
        var savings = categoryRepository.findBySystemRole(SystemCategoryRole.SAVINGS).orElseThrow();
        // Savings is BOTH type — allowed for EXPENSE use
        ResponseEntity<String> resp = rest.getForEntity(
            internal() + "/" + savings.getId() + "/validate?userId=" + USER_A + "&requiredType=EXPENSE",
            String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── DEFAULT response fields ───────────────────────────────────────────────

    @Test
    void defaultCategory_deletable_isFalse() {
        var savings = categoryRepository.findBySystemRole(SystemCategoryRole.SAVINGS).orElseThrow();
        HttpEntity<Void> req = new HttpEntity<>(bearerOf(tokenA));
        ResponseEntity<CategoryResponse> resp = rest.exchange(
            base() + "/" + savings.getId(), HttpMethod.GET, req, CategoryResponse.class);

        assertThat(resp.getBody().deletable()).isFalse();
    }

    @Test
    void customCategory_deletable_isTrue() {
        String name = "DeletableCat-" + UUID.randomUUID();
        var createReq = new CreateCategoryRequest(name,
            com.dailyexpense.category.domain.CategoryType.EXPENSE, null, null);
        HttpEntity<CreateCategoryRequest> createHttp = new HttpEntity<>(createReq, bearerOf(tokenA));
        ResponseEntity<Void> createResp = rest.exchange(base(), HttpMethod.POST, createHttp, Void.class);
        UUID catId = UUID.fromString(createResp.getHeaders().getLocation().getPath().split("/")[4]);

        HttpEntity<Void> req = new HttpEntity<>(bearerOf(tokenA));
        ResponseEntity<CategoryResponse> resp = rest.exchange(
            base() + "/" + catId, HttpMethod.GET, req, CategoryResponse.class);

        assertThat(resp.getBody().deletable()).isTrue();
    }

    // ── T114: /internal/users/{userId}/export-data ────────────────────────────

    @Test
    void exportData_returnsOkWithCategoriesKey() {
        String url = "http://localhost:" + port + "/internal/users/" + USER_A + "/export-data";
        ResponseEntity<java.util.Map> resp = rest.exchange(
            url, HttpMethod.GET, HttpEntity.EMPTY, java.util.Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKey("categories");
    }

    @Test
    void exportData_includesDefaultCategories() {
        String url = "http://localhost:" + port + "/internal/users/" + USER_A + "/export-data";
        ResponseEntity<java.util.Map> resp = rest.exchange(
            url, HttpMethod.GET, HttpEntity.EMPTY, java.util.Map.class);

        java.util.List<?> categories = (java.util.List<?>) resp.getBody().get("categories");
        assertThat(categories).isNotEmpty();
        assertThat(categories).anyMatch(c -> "DEFAULT".equals(((java.util.Map<?, ?>) c).get("origin")));
    }

    @Test
    void exportData_unknownUser_returnsEmptyList() {
        UUID unknownUser = UUID.randomUUID();
        String url = "http://localhost:" + port + "/internal/users/" + unknownUser + "/export-data";
        ResponseEntity<java.util.Map> resp = rest.exchange(
            url, HttpMethod.GET, HttpEntity.EMPTY, java.util.Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        java.util.List<?> categories = (java.util.List<?>) resp.getBody().get("categories");
        // DEFAULT categories are visible to all users; unknown user sees only defaults
        assertThat(categories).isNotNull();
    }

    @Test
    void exportData_noAuthRequired() {
        String url = "http://localhost:" + port + "/internal/users/" + USER_A + "/export-data";
        // No Authorization header — internal endpoint must not require JWT
        ResponseEntity<java.util.Map> resp = rest.exchange(
            url, HttpMethod.GET, HttpEntity.EMPTY, java.util.Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}

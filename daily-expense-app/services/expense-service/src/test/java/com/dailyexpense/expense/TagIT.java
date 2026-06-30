package com.dailyexpense.expense;

import com.dailyexpense.expense.port.CategoryLookupPort;
import com.dailyexpense.expense.port.CategoryValidationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * T062 gate — TagIT.
 * Proves: POST→201, duplicate name→409, GET owns, GET foreign→403,
 * DELETE→204 detaches from Expenses without deleting them, foreign→403.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TagIT extends AbstractExpenseServiceIT {

    @MockBean
    CategoryLookupPort categoryLookupPort;

    private static final UUID USER_A = UUID.randomUUID();
    private static final UUID USER_B = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();

    @BeforeEach
    void setupMocks() {
        when(categoryLookupPort.validate(any(), any(), any()))
            .thenReturn(new CategoryValidationResponse(CATEGORY_ID, "Food", "EXPENSE", "NONE"));
    }

    @Test
    void createTag_returns201WithLocation() throws Exception {
        mockMvc.perform(post("/api/v1/tags")
                .headers(authHeaders(USER_A))
                .content("{\"name\":\"groceries\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.tagId").isNotEmpty())
            .andExpect(jsonPath("$.name").value("groceries"));
    }

    @Test
    void createTag_duplicateName_returns409() throws Exception {
        String body = "{\"name\":\"duplicate-tag-" + UUID.randomUUID() + "\"}";
        // First create
        mockMvc.perform(post("/api/v1/tags").headers(authHeaders(USER_A)).content(body))
            .andExpect(status().isCreated());
        // Second create with same name
        mockMvc.perform(post("/api/v1/tags").headers(authHeaders(USER_A)).content(body))
            .andExpect(status().isConflict());
    }

    @Test
    void getTag_own_returns200() throws Exception {
        String loc = createTagGetLocation(USER_A, "mytag-" + UUID.randomUUID());
        UUID tagId = extractId(loc);

        mockMvc.perform(get("/api/v1/tags/{id}", tagId)
                .headers(authHeaders(USER_A)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tagId").value(tagId.toString()));
    }

    @Test
    void getTag_foreign_returns403() throws Exception {
        String loc = createTagGetLocation(USER_A, "tag-foreign-" + UUID.randomUUID());
        UUID tagId = extractId(loc);

        // USER_B cannot see USER_A's tag
        mockMvc.perform(get("/api/v1/tags/{id}", tagId)
                .headers(authHeaders(USER_B)))
            .andExpect(status().isForbidden());
    }

    @Test
    void deleteTag_returns204_andExpenseRetained() throws Exception {
        // Create tag and expense
        String tagName = "detach-test-" + UUID.randomUUID();
        String tagLoc = createTagGetLocation(USER_A, tagName);
        UUID tagId = extractId(tagLoc);

        // Create expense with this tag
        String expenseBody = """
            {"amount":{"amount":"100.00","currency":"INR"},"date":"2026-06-28",
             "categoryId":"%s","paymentMethod":"UPI","tagIds":["%s"]}
            """.formatted(CATEGORY_ID, tagId);
        var expResult = mockMvc.perform(post("/api/v1/expenses")
                .headers(authHeaders(USER_A)).content(expenseBody))
            .andExpect(status().isCreated()).andReturn();
        UUID expenseId = extractId(expResult.getResponse().getHeader("Location"));

        // Delete tag
        mockMvc.perform(delete("/api/v1/tags/{id}", tagId)
                .headers(authHeaders(USER_A)))
            .andExpect(status().isNoContent());

        // Expense still accessible (tag delete does NOT delete expenses)
        mockMvc.perform(get("/api/v1/expenses/{id}", expenseId)
                .headers(authHeaders(USER_A)))
            .andExpect(status().isOk());
    }

    @Test
    void deleteTag_foreign_returns403() throws Exception {
        String loc = createTagGetLocation(USER_A, "tag-del-foreign-" + UUID.randomUUID());
        UUID tagId = extractId(loc);

        mockMvc.perform(delete("/api/v1/tags/{id}", tagId)
                .headers(authHeaders(USER_B)))
            .andExpect(status().isForbidden());
    }

    @Test
    void deleteTag_nonExistent_returns403() throws Exception {
        // 403-never-404 (INV-1/SEC-3)
        mockMvc.perform(delete("/api/v1/tags/{id}", UUID.randomUUID())
                .headers(authHeaders(USER_A)))
            .andExpect(status().isForbidden());
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private String createTagGetLocation(UUID userId, String name) throws Exception {
        var result = mockMvc.perform(post("/api/v1/tags")
                .headers(authHeaders(userId))
                .content("{\"name\":\"" + name + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        return result.getResponse().getHeader("Location");
    }

    private UUID extractId(String location) {
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }
}

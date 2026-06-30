package com.dailyexpense.expense;

import com.dailyexpense.expense.port.CategoryLookupPort;
import com.dailyexpense.expense.port.CategoryValidationResponse;
import com.dailyexpense.shared.exception.ForbiddenOwnershipException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * T054/T056 gate — ExpenseCrudIT.
 * Proves: 201 create, 400 on validation failures, 403-never-404, EXP-INV-5, ownership isolation.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExpenseCrudIT extends AbstractExpenseServiceIT {

    @MockBean
    CategoryLookupPort categoryLookupPort;

    private static final UUID USER_A = UUID.randomUUID();
    private static final UUID USER_B = UUID.randomUUID();
    private static final UUID EXPENSE_CATEGORY = UUID.randomUUID();
    private static final UUID SAVINGS_CATEGORY = UUID.randomUUID();
    private static final UUID SAVINGS_GOAL = UUID.randomUUID();

    @BeforeEach
    void setupMocks() {
        when(categoryLookupPort.validate(eq(EXPENSE_CATEGORY), any(), any()))
            .thenReturn(new CategoryValidationResponse(EXPENSE_CATEGORY, "Food", "EXPENSE", "NONE"));

        when(categoryLookupPort.validate(eq(SAVINGS_CATEGORY), any(), any()))
            .thenReturn(new CategoryValidationResponse(SAVINGS_CATEGORY, "Savings", "BOTH", "SAVINGS"));

        when(categoryLookupPort.validate(eq(UUID.fromString("00000000-0000-0000-0000-000000000001")), any(), any()))
            .thenThrow(new ForbiddenOwnershipException());
    }

    // ── T054: Create ─────────────────────────────────────────────────────────────

    @Test
    void create_validExpense_returns201WithLocation() throws Exception {
        mockMvc.perform(post("/api/v1/expenses")
                .headers(authHeaders(USER_A))
                .content(expenseJson("500.00", "2026-06-28", EXPENSE_CATEGORY.toString(), "UPI")))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", containsString("/api/v1/expenses/")));
    }

    @Test
    void create_missingAmount_returns400NamingField() throws Exception {
        String body = """
            {"date":"2026-06-28","categoryId":"%s","paymentMethod":"UPI"}
            """.formatted(EXPENSE_CATEGORY);
        mockMvc.perform(post("/api/v1/expenses")
                .headers(authHeaders(USER_A))
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("amount")));
    }

    @Test
    void create_missingDate_returns400NamingField() throws Exception {
        String body = """
            {"amount":{"amount":"100.00","currency":"INR"},"categoryId":"%s","paymentMethod":"UPI"}
            """.formatted(EXPENSE_CATEGORY);
        mockMvc.perform(post("/api/v1/expenses")
                .headers(authHeaders(USER_A))
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("date")));
    }

    @Test
    void create_amountZero_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/expenses")
                .headers(authHeaders(USER_A))
                .content(expenseJson("0.00", "2026-06-28", EXPENSE_CATEGORY.toString(), "CASH")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void create_amountNegative_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/expenses")
                .headers(authHeaders(USER_A))
                .content(expenseJson("-1.00", "2026-06-28", EXPENSE_CATEGORY.toString(), "CASH")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void create_foreignCategory_returns403() throws Exception {
        UUID foreignCat = UUID.fromString("00000000-0000-0000-0000-000000000001");
        mockMvc.perform(post("/api/v1/expenses")
                .headers(authHeaders(USER_A))
                .content(expenseJson("200.00", "2026-06-28", foreignCat.toString(), "UPI")))
            .andExpect(status().isForbidden());
    }

    @Test
    void create_savingsGoalWithNonSavingsCategory_returns400_EXP_INV_5() throws Exception {
        mockMvc.perform(post("/api/v1/expenses")
                .headers(authHeaders(USER_A))
                .content(expenseJsonWithGoal(
                    "300.00", "2026-06-28", EXPENSE_CATEGORY.toString(), "UPI", SAVINGS_GOAL.toString())))
            .andExpect(status().isBadRequest());
    }

    @Test
    void create_savingsGoalWithSavingsCategory_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/expenses")
                .headers(authHeaders(USER_A))
                .content(expenseJsonWithGoal(
                    "1000.00", "2026-06-28", SAVINGS_CATEGORY.toString(), "CASH", SAVINGS_GOAL.toString())))
            .andExpect(status().isCreated());
    }

    @Test
    void create_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/expenses")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(expenseJson("100.00", "2026-06-28", EXPENSE_CATEGORY.toString(), "UPI")))
            .andExpect(status().isUnauthorized());
    }

    // ── T056: Get / Update / Delete with 403-never-404 ───────────────────────────

    @Test
    void getOne_own_returns200() throws Exception {
        String location = createExpense(USER_A, "400.00");
        UUID expenseId = extractId(location);

        mockMvc.perform(get("/api/v1/expenses/" + expenseId)
                .headers(authHeaders(USER_A)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.expenseId").value(expenseId.toString()))
            .andExpect(jsonPath("$.amount.amount").value("400.00"))
            .andExpect(jsonPath("$.paymentMethod").value("UPI"));
    }

    @Test
    void getOne_neverExposePasswordHash_noSuchField() throws Exception {
        String location = createExpense(USER_A, "100.00");
        UUID expenseId = extractId(location);

        mockMvc.perform(get("/api/v1/expenses/" + expenseId)
                .headers(authHeaders(USER_A)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").doesNotExist()); // AL-4: no internal fields exposed
    }

    @Test
    void getOne_foreignExpense_returns403_neverReturns404() throws Exception {
        String location = createExpense(USER_A, "100.00");
        UUID expenseId = extractId(location);

        mockMvc.perform(get("/api/v1/expenses/" + expenseId)
                .headers(authHeaders(USER_B)))
            .andExpect(status().isForbidden()); // 403, never 404
    }

    @Test
    void getOne_nonExistentId_returns403() throws Exception {
        UUID nonExistent = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/expenses/" + nonExistent)
                .headers(authHeaders(USER_A)))
            .andExpect(status().isForbidden()); // 403-never-404 (INV-1/SEC-3)
    }

    @Test
    void update_own_returns200() throws Exception {
        String location = createExpense(USER_A, "100.00");
        UUID expenseId = extractId(location);

        String update = expenseJson("250.00", "2026-06-29", EXPENSE_CATEGORY.toString(), "CASH");
        mockMvc.perform(put("/api/v1/expenses/" + expenseId)
                .headers(authHeaders(USER_A))
                .content(update))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.amount.amount").value("250.00"))
            .andExpect(jsonPath("$.paymentMethod").value("CASH"));
    }

    @Test
    void update_foreignExpense_returns403() throws Exception {
        String location = createExpense(USER_A, "100.00");
        UUID expenseId = extractId(location);

        mockMvc.perform(put("/api/v1/expenses/" + expenseId)
                .headers(authHeaders(USER_B))
                .content(expenseJson("999.00", "2026-06-28", EXPENSE_CATEGORY.toString(), "UPI")))
            .andExpect(status().isForbidden());
    }

    @Test
    void delete_own_returns204() throws Exception {
        String location = createExpense(USER_A, "150.00");
        UUID expenseId = extractId(location);

        mockMvc.perform(delete("/api/v1/expenses/" + expenseId)
                .headers(authHeaders(USER_A)))
            .andExpect(status().isNoContent());

        // After delete, accessing it returns 403 (not 404)
        mockMvc.perform(get("/api/v1/expenses/" + expenseId)
                .headers(authHeaders(USER_A)))
            .andExpect(status().isForbidden());
    }

    @Test
    void delete_foreignExpense_returns403() throws Exception {
        String location = createExpense(USER_A, "200.00");
        UUID expenseId = extractId(location);

        mockMvc.perform(delete("/api/v1/expenses/" + expenseId)
                .headers(authHeaders(USER_B)))
            .andExpect(status().isForbidden());
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private String createExpense(UUID userId, String amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/expenses")
                .headers(authHeaders(userId))
                .content(expenseJson(amount, "2026-06-28", EXPENSE_CATEGORY.toString(), "UPI")))
            .andExpect(status().isCreated())
            .andReturn();
        return result.getResponse().getHeader("Location");
    }

    private UUID extractId(String location) {
        String[] parts = location.split("/");
        return UUID.fromString(parts[parts.length - 1]);
    }
}

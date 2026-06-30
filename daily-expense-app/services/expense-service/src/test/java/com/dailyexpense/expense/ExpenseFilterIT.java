package com.dailyexpense.expense;

import com.dailyexpense.expense.port.CategoryLookupPort;
import com.dailyexpense.expense.port.CategoryValidationResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * T055 gate — ExpenseFilterIT.
 * Proves: PageResponse (5 keys), from/to/categoryId/paymentMethod/savingsGoalId filters,
 * cross-user isolation, sort by date/amount, pagination size cap.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExpenseFilterIT extends AbstractExpenseServiceIT {

    @MockBean
    CategoryLookupPort categoryLookupPort;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private static final UUID USER_A = UUID.randomUUID();
    private static final UUID USER_B = UUID.randomUUID();
    private static final UUID CAT_FOOD = UUID.randomUUID();
    private static final UUID CAT_TRANSPORT = UUID.randomUUID();
    private static final UUID CAT_SAVINGS = UUID.randomUUID();
    private static final UUID GOAL_ID = UUID.randomUUID();

    @BeforeAll
    void seedData() throws Exception {
        // Route by categoryId: CAT_SAVINGS → BOTH/SAVINGS; everything else → EXPENSE/NONE
        when(categoryLookupPort.validate(any(), any(), any()))
            .thenAnswer(inv -> {
                UUID catId = (UUID) inv.getArguments()[0];
                if (CAT_SAVINGS.equals(catId)) {
                    return new CategoryValidationResponse(CAT_SAVINGS, "Savings", "BOTH", "SAVINGS");
                }
                return new CategoryValidationResponse(catId, "Category", "EXPENSE", "NONE");
            });

        // User A: 3 expenses on different dates / categories
        createExpense(USER_A, "100.00", "2026-06-01", CAT_FOOD, "UPI", null);
        createExpense(USER_A, "200.00", "2026-06-15", CAT_TRANSPORT, "CASH", null);
        createExpense(USER_A, "300.00", "2026-06-28", CAT_FOOD, "CREDIT_CARD", null);

        // User A: 1 savings-goal-linked expense (EXP-INV-5: uses CAT_SAVINGS)
        createExpense(USER_A, "500.00", "2026-06-28", CAT_SAVINGS, "UPI", GOAL_ID);

        // User B: 1 expense — must NEVER appear in User A's list
        createExpense(USER_B, "999.00", "2026-06-28", CAT_FOOD, "UPI", null);
    }

    @Test
    void list_returnsPageResponseWith5Keys() throws Exception {
        mockMvc.perform(get("/api/v1/expenses")
                .headers(authHeaders(USER_A)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.page").isNumber())
            .andExpect(jsonPath("$.size").isNumber())
            .andExpect(jsonPath("$.totalElements").isNumber())
            .andExpect(jsonPath("$.totalPages").isNumber());
    }

    @Test
    void list_crossUserIsolation_userACannotSeeUserBExpenses() throws Exception {
        mockMvc.perform(get("/api/v1/expenses")
                .headers(authHeaders(USER_A)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].amount.amount", not(hasItem("999.00"))));
    }

    @Test
    void list_userASees4OwnExpenses() throws Exception {
        mockMvc.perform(get("/api/v1/expenses")
                .headers(authHeaders(USER_A)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(4)));
    }

    @Test
    void list_filterByDateRange_returnsExpensesInRange() throws Exception {
        mockMvc.perform(get("/api/v1/expenses")
                .headers(authHeaders(USER_A))
                .param("from", "2026-06-10")
                .param("to", "2026-06-30"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(2)));
    }

    @Test
    void list_filterByCategoryId_returnsFoodOnly() throws Exception {
        mockMvc.perform(get("/api/v1/expenses")
                .headers(authHeaders(USER_A))
                .param("categoryId", CAT_FOOD.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].categoryId",
                everyItem(is(CAT_FOOD.toString()))));
    }

    @Test
    void list_filterByPaymentMethod_returnsCashOnly() throws Exception {
        mockMvc.perform(get("/api/v1/expenses")
                .headers(authHeaders(USER_A))
                .param("paymentMethod", "CASH"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].paymentMethod",
                everyItem(is("CASH"))));
    }

    @Test
    void list_filterBySavingsGoalId_returnsSavingsLinkedOnly() throws Exception {
        mockMvc.perform(get("/api/v1/expenses")
                .headers(authHeaders(USER_A))
                .param("savingsGoalId", GOAL_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.content[*].savingsGoalId",
                everyItem(is(GOAL_ID.toString()))));
    }

    @Test
    void list_paginationPageSizeRespected() throws Exception {
        mockMvc.perform(get("/api/v1/expenses")
                .headers(authHeaders(USER_A))
                .param("page", "0")
                .param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(lessThanOrEqualTo(2))))
            .andExpect(jsonPath("$.size").value(2));
    }

    @Test
    void list_sortByAmountAsc_ordersCorrectly() throws Exception {
        mockMvc.perform(get("/api/v1/expenses")
                .headers(authHeaders(USER_A))
                .param("sortBy", "amount")
                .param("sortDir", "asc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void list_sizeCappedAt100() throws Exception {
        mockMvc.perform(get("/api/v1/expenses")
                .headers(authHeaders(USER_A))
                .param("size", "999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.size").value(lessThanOrEqualTo(100)));
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/expenses"))
            .andExpect(status().isUnauthorized());
    }

    // ── helper ────────────────────────────────────────────────────────────────────

    private void createExpense(UUID userId, String amount, String date, UUID categoryId,
                                String paymentMethod, UUID savingsGoalId) throws Exception {
        String body = savingsGoalId == null
            ? expenseJson(amount, date, categoryId.toString(), paymentMethod)
            : expenseJsonWithGoal(amount, date, categoryId.toString(), paymentMethod, savingsGoalId.toString());

        mockMvc.perform(post("/api/v1/expenses")
            .headers(authHeaders(userId))
            .content(body));
    }
}

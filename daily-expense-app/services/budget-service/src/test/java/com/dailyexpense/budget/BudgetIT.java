package com.dailyexpense.budget;

import com.dailyexpense.budget.domain.Budget;
import com.dailyexpense.budget.port.CategoryLookupPort;
import com.dailyexpense.budget.repository.BudgetPeriodLedgerRepository;
import com.dailyexpense.budget.repository.BudgetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * T093 — BudgetIT: CRUD, activation (BUD-INV-7), CATEGORY scope, 403-never-404.
 * CategoryLookupPort is @MockBean for isolation; BudgetAlertKafkaRedeliveryTest covers real Kafka.
 */
class BudgetIT extends AbstractBudgetServiceIT {

    @MockBean
    CategoryLookupPort categoryLookupPort;

    @Autowired
    BudgetRepository budgetRepository;

    @Autowired
    BudgetPeriodLedgerRepository ledgerRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private final UUID userId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("DELETE FROM budget_period_ledgers");
        budgetRepository.deleteAll();
        jdbcTemplate.execute("DELETE FROM outbox");
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    @Test
    void createBudget_overall_returns201_withLocation() throws Exception {
        mockMvc.perform(post("/api/v1/budgets")
                .headers(authHeaders(userId))
                .content(overallBudgetJson("10000", "MONTHLY")))
            .andExpect(status().isCreated())
            .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/v1/budgets/")))
            .andExpect(jsonPath("$.scope").value("OVERALL"))
            .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createBudget_category_missingCategoryId_returns409() throws Exception {
        mockMvc.perform(post("/api/v1/budgets")
                .headers(authHeaders(userId))
                .content("{\"scope\":\"CATEGORY\",\"budgetLimit\":5000,\"periodType\":\"MONTHLY\",\"rolloverEnabled\":false}"))
            .andExpect(status().isConflict());
    }

    @Test
    void createBudget_overall_withCategoryId_returns409() throws Exception {
        mockMvc.perform(post("/api/v1/budgets")
                .headers(authHeaders(userId))
                .content("{\"scope\":\"OVERALL\",\"categoryId\":\"" + UUID.randomUUID() + "\",\"budgetLimit\":5000,\"periodType\":\"MONTHLY\",\"rolloverEnabled\":false}"))
            .andExpect(status().isConflict());
    }

    @Test
    void createBudget_zeroLimit_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/budgets")
                .headers(authHeaders(userId))
                .content("{\"scope\":\"OVERALL\",\"budgetLimit\":0,\"periodType\":\"MONTHLY\",\"rolloverEnabled\":false}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createBudget_category_validCategoryId_returns201() throws Exception {
        UUID catId = UUID.randomUUID();
        when(categoryLookupPort.exists(any(), anyString())).thenReturn(true);

        mockMvc.perform(post("/api/v1/budgets")
                .headers(authHeaders(userId))
                .content(categoryBudgetJson(catId.toString(), "5000", "MONTHLY")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.scope").value("CATEGORY"))
            .andExpect(jsonPath("$.categoryId").value(catId.toString()));
    }

    @Test
    void createBudget_category_notFoundCategoryId_returns409() throws Exception {
        UUID catId = UUID.randomUUID();
        when(categoryLookupPort.exists(any(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/v1/budgets")
                .headers(authHeaders(userId))
                .content(categoryBudgetJson(catId.toString(), "5000", "MONTHLY")))
            .andExpect(status().isConflict());
    }

    @Test
    void listBudgets_returns200_withPage() throws Exception {
        createBudget(userId, "10000", "MONTHLY");

        mockMvc.perform(get("/api/v1/budgets")
                .headers(authHeaders(userId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void getBudget_foreignUser_returns403() throws Exception {
        String location = createBudget(userId, "8000", "WEEKLY");
        UUID budgetId = extractId(location);

        mockMvc.perform(get("/api/v1/budgets/{id}", budgetId)
                .headers(authHeaders(otherUserId)))
            .andExpect(status().isForbidden());
    }

    @Test
    void updateBudget_updatesLimit() throws Exception {
        String location = createBudget(userId, "8000", "MONTHLY");
        UUID budgetId = extractId(location);

        mockMvc.perform(put("/api/v1/budgets/{id}", budgetId)
                .headers(authHeaders(userId))
                .content("{\"budgetLimit\":12000}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.budgetLimit").value(12000));
    }

    @Test
    void deleteBudget_returns204() throws Exception {
        String location = createBudget(userId, "5000", "MONTHLY");
        UUID budgetId = extractId(location);

        mockMvc.perform(delete("/api/v1/budgets/{id}", budgetId)
                .headers(authHeaders(userId)))
            .andExpect(status().isNoContent());

        assertThat(budgetRepository.findById(budgetId)).isEmpty();
    }

    @Test
    void deleteBudget_foreignUser_returns403() throws Exception {
        String location = createBudget(userId, "5000", "MONTHLY");
        UUID budgetId = extractId(location);

        mockMvc.perform(delete("/api/v1/budgets/{id}", budgetId)
                .headers(authHeaders(otherUserId)))
            .andExpect(status().isForbidden());

        assertThat(budgetRepository.findById(budgetId)).isPresent();
    }

    // ── Activation (BUD-INV-7) ────────────────────────────────────────────────

    @Test
    void deactivateBudget_setsActiveFalse() throws Exception {
        String location = createBudget(userId, "10000", "MONTHLY");
        UUID budgetId = extractId(location);

        mockMvc.perform(patch("/api/v1/budgets/{id}/activation", budgetId)
                .headers(authHeaders(userId))
                .content("{\"active\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        Budget budget = budgetRepository.findById(budgetId).orElseThrow();
        assertThat(budget.isActive()).isFalse();
    }

    @Test
    void reactivateBudget_setsActiveTrue() throws Exception {
        String location = createBudget(userId, "10000", "MONTHLY");
        UUID budgetId = extractId(location);

        mockMvc.perform(patch("/api/v1/budgets/{id}/activation", budgetId)
                .headers(authHeaders(userId))
                .content("{\"active\":false}"))
            .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/budgets/{id}/activation", budgetId)
                .headers(authHeaders(userId))
                .content("{\"active\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void activation_foreignUser_returns403() throws Exception {
        String location = createBudget(userId, "10000", "MONTHLY");
        UUID budgetId = extractId(location);

        mockMvc.perform(patch("/api/v1/budgets/{id}/activation", budgetId)
                .headers(authHeaders(otherUserId))
                .content("{\"active\":false}"))
            .andExpect(status().isForbidden());
    }

    // ── Rollover toggle ───────────────────────────────────────────────────────

    @Test
    void toggleRollover_enablesRollover() throws Exception {
        String location = createBudget(userId, "10000", "MONTHLY");
        UUID budgetId = extractId(location);

        mockMvc.perform(patch("/api/v1/budgets/{id}/rollover", budgetId)
                .headers(authHeaders(userId))
                .content("{\"rolloverEnabled\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rolloverEnabled").value(true));
    }

    // ── Budget status ─────────────────────────────────────────────────────────

    @Test
    void getBudgetStatus_returns200_withDerivedFields() throws Exception {
        String location = createBudget(userId, "10000", "MONTHLY");
        UUID budgetId = extractId(location);

        mockMvc.perform(get("/api/v1/budgets/{id}/status", budgetId)
                .headers(authHeaders(userId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.budgetLimit").value(10000))
            .andExpect(jsonPath("$.spent").value(0))
            .andExpect(jsonPath("$.remaining").value(10000))
            .andExpect(jsonPath("$.percentUsed").value(0));
    }

    @Test
    void getBudgetStatus_foreignUser_returns403() throws Exception {
        String location = createBudget(userId, "10000", "MONTHLY");
        UUID budgetId = extractId(location);

        mockMvc.perform(get("/api/v1/budgets/{id}/status", budgetId)
                .headers(authHeaders(otherUserId)))
            .andExpect(status().isForbidden());
    }

    // ── Period ledger created on budget creation ──────────────────────────────

    @Test
    void createBudget_opensPeriodLedger() throws Exception {
        String location = createBudget(userId, "10000", "MONTHLY");
        UUID budgetId = extractId(location);

        Budget budget = budgetRepository.findById(budgetId).orElseThrow();
        assertThat(ledgerRepository.findByBudgetIdAndPeriodStart(
            budget.getId(), java.time.LocalDate.now())).isPresent();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String createBudget(UUID uid, String limit, String period) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/budgets")
                .headers(authHeaders(uid))
                .content(overallBudgetJson(limit, period)))
            .andExpect(status().isCreated())
            .andReturn();
        return result.getResponse().getHeader(HttpHeaders.LOCATION);
    }

    private UUID extractId(String location) {
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }
}

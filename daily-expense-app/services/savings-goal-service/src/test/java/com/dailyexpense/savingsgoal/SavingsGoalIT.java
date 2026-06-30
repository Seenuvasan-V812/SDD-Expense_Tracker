package com.dailyexpense.savingsgoal;

import com.dailyexpense.savingsgoal.port.ContributionPort;
import com.dailyexpense.savingsgoal.repository.ContributionEntryRepository;
import com.dailyexpense.savingsgoal.repository.SavingsGoalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * T082 — savings-goal-service integration suite (Testcontainers Postgres).
 * Covers: CRUD, history, lifecycle state machine, auto-complete, Expense-retention-on-delete.
 * ContributionPort is @MockBean — unit-level isolation; ContributionReconcileIT covers real Kafka.
 */
class SavingsGoalIT extends AbstractSavingsGoalServiceIT {

    @MockBean
    ContributionPort contributionPort;

    @Autowired
    SavingsGoalRepository goalRepository;

    @Autowired
    ContributionEntryRepository entryRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private final UUID userId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();

    @BeforeEach
    void clean() {
        entryRepository.deleteAll();
        goalRepository.deleteAll();
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    @Test
    void createGoal_returns201_withLocation() throws Exception {
        mockMvc.perform(post("/api/v1/savings-goals")
                .headers(authHeaders(userId))
                .content(createGoalJson("Emergency Fund", "50000")))
            .andExpect(status().isCreated())
            .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/v1/savings-goals/")));
    }

    @Test
    void createGoal_missingName_returns400() throws Exception {
        String body = """
            {"targetAmount": {"amount": "1000", "currency": "INR"}}
            """;
        mockMvc.perform(post("/api/v1/savings-goals")
                .headers(authHeaders(userId))
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createGoal_zeroAmount_returns400() throws Exception {
        String body = """
            {"name": "Bad", "targetAmount": {"amount": "0", "currency": "INR"}}
            """;
        mockMvc.perform(post("/api/v1/savings-goals")
                .headers(authHeaders(userId))
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    void listGoals_byStatus_returnsCorrectPage() throws Exception {
        createGoal(userId, "Active Goal", "10000");

        mockMvc.perform(get("/api/v1/savings-goals").param("status", "ACTIVE")
                .headers(authHeaders(userId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));
    }

    @Test
    void getGoalDetail_foreignUser_returns403() throws Exception {
        String location = createGoal(userId, "My Goal", "5000");
        UUID goalId = extractId(location);

        mockMvc.perform(get("/api/v1/savings-goals/{id}", goalId)
                .headers(authHeaders(otherUserId)))
            .andExpect(status().isForbidden());
    }

    @Test
    void deleteGoal_returns204_andSavingsGoalDeletedEventInOutbox() throws Exception {
        String location = createGoal(userId, "Vacation Fund", "30000");
        UUID goalId = extractId(location);

        mockMvc.perform(delete("/api/v1/savings-goals/{id}", goalId)
                .headers(authHeaders(userId)))
            .andExpect(status().isNoContent());

        int outboxCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox WHERE event_type='SavingsGoalDeletedEvent'", Integer.class);
        assertThat(outboxCount).isEqualTo(1);
    }

    @Test
    void deleteGoal_foreignUser_returns403() throws Exception {
        String location = createGoal(userId, "My Fund", "5000");
        UUID goalId = extractId(location);

        mockMvc.perform(delete("/api/v1/savings-goals/{id}", goalId)
                .headers(authHeaders(otherUserId)))
            .andExpect(status().isForbidden());

        assertThat(goalRepository.findById(goalId)).isPresent();
    }

    // ── Contribution primary flow ─────────────────────────────────────────────

    @Test
    void recordContribution_primary_createsEntry_source_GOAL_SCREEN() throws Exception {
        String location = createGoal(userId, "Laptop Fund", "80000");
        UUID goalId = extractId(location);

        UUID mockExpenseId = UUID.randomUUID();
        when(contributionPort.createBackingExpense(
            eq(userId), org.mockito.ArgumentMatchers.any(BigDecimal.class), org.mockito.ArgumentMatchers.any(LocalDate.class),
            eq(goalId), anyString()))
            .thenReturn(mockExpenseId);

        mockMvc.perform(post("/api/v1/savings-goals/{id}/contributions", goalId)
                .headers(authHeaders(userId))
                .content(contributionJson("10000", "2026-06-28")))
            .andExpect(status().isCreated());

        long entryCount = entryRepository.count();
        assertThat(entryCount).isEqualTo(1);

        mockMvc.perform(get("/api/v1/savings-goals/{id}/contributions", goalId)
                .headers(authHeaders(userId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].source").value("GOAL_SCREEN"))
            .andExpect(jsonPath("$.content[0].expenseId").value(mockExpenseId.toString()));
    }

    @Test
    void recordContribution_updatesTotalContributed() throws Exception {
        String location = createGoal(userId, "Emergency", "50000");
        UUID goalId = extractId(location);

        UUID mockExpenseId = UUID.randomUUID();
        when(contributionPort.createBackingExpense(any(), any(), any(), any(), any()))
            .thenReturn(mockExpenseId);

        mockMvc.perform(post("/api/v1/savings-goals/{id}/contributions", goalId)
                .headers(authHeaders(userId))
                .content(contributionJson("15000", "2026-06-28")))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/savings-goals/{id}", goalId)
                .headers(authHeaders(userId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalContributed.amount").value("15000.00"));
    }

    // ── Auto-complete (SG-INV-6) ──────────────────────────────────────────────

    @Test
    void autoComplete_firesExactlyOnce_whenTotalReachesTarget() throws Exception {
        String location = createGoal(userId, "Auto Complete Goal", "10000");
        UUID goalId = extractId(location);

        when(contributionPort.createBackingExpense(any(), any(), any(), any(), any()))
            .thenReturn(UUID.randomUUID())
            .thenReturn(UUID.randomUUID());

        // First contribution: partial (5000)
        mockMvc.perform(post("/api/v1/savings-goals/{id}/contributions", goalId)
                .headers(authHeaders(userId))
                .content(contributionJson("5000", "2026-06-28")))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/savings-goals/{id}", goalId)
                .headers(authHeaders(userId)))
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Second contribution: reaches target (5000 more = 10000)
        mockMvc.perform(post("/api/v1/savings-goals/{id}/contributions", goalId)
                .headers(authHeaders(userId))
                .content(contributionJson("5000", "2026-06-28")))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/savings-goals/{id}", goalId)
                .headers(authHeaders(userId)))
            .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Verify SavingsGoalCompletedEvent in outbox exactly once
        int completedEvents = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox WHERE event_type='SavingsGoalCompletedEvent'", Integer.class);
        assertThat(completedEvents).isEqualTo(1);
    }

    @Test
    void autoComplete_alreadyCompleted_noSecondEvent() throws Exception {
        String location = createGoal(userId, "Already Done", "1000");
        UUID goalId = extractId(location);

        when(contributionPort.createBackingExpense(any(), any(), any(), any(), any()))
            .thenReturn(UUID.randomUUID())
            .thenReturn(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/savings-goals/{id}/contributions", goalId)
                .headers(authHeaders(userId))
                .content(contributionJson("1000", "2026-06-28")))
            .andExpect(status().isCreated());

        // Goal is now COMPLETED; try another contribution — should be rejected
        mockMvc.perform(post("/api/v1/savings-goals/{id}/contributions", goalId)
                .headers(authHeaders(userId))
                .content(contributionJson("500", "2026-06-28")))
            .andExpect(status().isConflict());

        int completedEvents = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox WHERE event_type='SavingsGoalCompletedEvent'", Integer.class);
        assertThat(completedEvents).isEqualTo(1);
    }

    // ── Lifecycle state machine (T079) ────────────────────────────────────────

    @Test
    void patchStatus_active_to_paused_returns200() throws Exception {
        String location = createGoal(userId, "Pause Me", "20000");
        UUID goalId = extractId(location);

        mockMvc.perform(patch("/api/v1/savings-goals/{id}/status", goalId)
                .headers(authHeaders(userId))
                .content("{\"status\":\"PAUSED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAUSED"));
    }

    @Test
    void patchStatus_completed_to_anything_returns409() throws Exception {
        String location = createGoal(userId, "Manual Complete", "100");
        UUID goalId = extractId(location);

        mockMvc.perform(patch("/api/v1/savings-goals/{id}/status", goalId)
                .headers(authHeaders(userId))
                .content("{\"status\":\"COMPLETED\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/savings-goals/{id}/status", goalId)
                .headers(authHeaders(userId))
                .content("{\"status\":\"ACTIVE\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void patchStatus_abandoned_to_anything_returns409() throws Exception {
        String location = createGoal(userId, "Abandon Me", "100");
        UUID goalId = extractId(location);

        mockMvc.perform(patch("/api/v1/savings-goals/{id}/status", goalId)
                .headers(authHeaders(userId))
                .content("{\"status\":\"ABANDONED\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/savings-goals/{id}/status", goalId)
                .headers(authHeaders(userId))
                .content("{\"status\":\"ACTIVE\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void patchStatus_foreignUser_returns403() throws Exception {
        String location = createGoal(userId, "Not Yours", "100");
        UUID goalId = extractId(location);

        mockMvc.perform(patch("/api/v1/savings-goals/{id}/status", goalId)
                .headers(authHeaders(otherUserId))
                .content("{\"status\":\"PAUSED\"}"))
            .andExpect(status().isForbidden());
    }

    // ── Expense retention on delete ───────────────────────────────────────────

    @Test
    void deleteGoal_doesNotCascadeExpenses() throws Exception {
        String location = createGoal(userId, "Expense Retainer", "50000");
        UUID goalId = extractId(location);

        // verify goal exists
        assertThat(goalRepository.findById(goalId)).isPresent();

        mockMvc.perform(delete("/api/v1/savings-goals/{id}", goalId)
                .headers(authHeaders(userId)))
            .andExpect(status().isNoContent());

        assertThat(goalRepository.findById(goalId)).isEmpty();
        // Expenses are in expense_db (separate service) — savings-goal-service has no access.
        // The SavingsGoalDeletedEvent triggers expense-service to clear savings_goal_id (T119).
        // This test verifies that savings-goal-service does NOT try to delete expenses.
        int outboxRows = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM outbox WHERE event_type='SavingsGoalDeletedEvent'", Integer.class);
        assertThat(outboxRows).isEqualTo(1);
    }

    // ── Contribution history (T075) ───────────────────────────────────────────

    @Test
    void listContributions_foreignUser_returns403() throws Exception {
        String location = createGoal(userId, "History Goal", "10000");
        UUID goalId = extractId(location);

        mockMvc.perform(get("/api/v1/savings-goals/{id}/contributions", goalId)
                .headers(authHeaders(otherUserId)))
            .andExpect(status().isForbidden());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String createGoal(UUID uid, String name, String target) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/savings-goals")
                .headers(authHeaders(uid))
                .content(createGoalJson(name, target)))
            .andExpect(status().isCreated())
            .andReturn();
        return result.getResponse().getHeader(HttpHeaders.LOCATION);
    }

    private UUID extractId(String location) {
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }
}

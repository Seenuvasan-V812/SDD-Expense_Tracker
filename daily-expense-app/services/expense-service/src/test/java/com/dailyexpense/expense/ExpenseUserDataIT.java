package com.dailyexpense.expense;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * T115 — Internal export-data endpoint: streaming GET /internal/users/{userId}/export-data.
 * AL-1: no category_db / savings_goal_db / budget_db SQL.
 * CQ-10: StreamingResponseBody used; no full in-memory load.
 */
class ExpenseUserDataIT extends AbstractExpenseServiceIT {

    @Test
    void exportData_noAuthRequired_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        mockMvc.perform(get("/internal/users/{userId}/export-data", userId))
            .andExpect(status().isOk());
    }

    @Test
    void exportData_returnsJsonWithExpensesKey() throws Exception {
        UUID userId = UUID.randomUUID();
        MvcResult result = mockMvc.perform(get("/internal/users/{userId}/export-data", userId))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"expenses\"");
    }

    @Test
    void exportData_unknownUser_returnsEmptyExpensesList() throws Exception {
        UUID unknownUser = UUID.randomUUID();
        MvcResult result = mockMvc.perform(get("/internal/users/{userId}/export-data", unknownUser))
            .andExpect(status().isOk())
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"expenses\"");
        assertThat(body).contains("[]");
    }

    @Test
    void exportData_withExpenses_includesExpenseFields() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        // Create an expense via the authenticated API
        String expenseJson = expenseJson("99.99", "2025-01-15", categoryId.toString(), "CARD");
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/expenses")
                .headers(authHeaders(userId))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(expenseJson))
            .andExpect(status().isCreated());

        // Export data should include that expense
        MvcResult result = mockMvc.perform(get("/internal/users/{userId}/export-data", userId))
            .andExpect(status().isOk())
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"expenses\"");
        assertThat(body).contains("99.99");
    }
}

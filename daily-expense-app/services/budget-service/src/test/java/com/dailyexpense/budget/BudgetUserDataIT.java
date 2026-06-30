package com.dailyexpense.budget;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * T117 — Internal export-data endpoint: GET /internal/users/{userId}/export-data.
 * AL-1: no expense_db / category_db / savings_goal_db SQL.
 */
class BudgetUserDataIT extends AbstractBudgetServiceIT {

    @Test
    void exportData_noAuthRequired_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        mockMvc.perform(get("/internal/users/{userId}/export-data", userId))
            .andExpect(status().isOk());
    }

    @Test
    void exportData_returnsJsonWithBudgetsKey() throws Exception {
        UUID userId = UUID.randomUUID();
        MvcResult result = mockMvc.perform(get("/internal/users/{userId}/export-data", userId))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"budgets\"");
    }

    @Test
    void exportData_unknownUser_returnsEmptyBudgetsList() throws Exception {
        UUID unknownUser = UUID.randomUUID();
        MvcResult result = mockMvc.perform(get("/internal/users/{userId}/export-data", unknownUser))
            .andExpect(status().isOk())
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"budgets\"");
        assertThat(body).contains("[]");
    }

    @Test
    void exportData_withBudget_includesBudgetFields() throws Exception {
        UUID userId = UUID.randomUUID();

        // Create a budget via the authenticated API
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/budgets")
                .headers(authHeaders(userId))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(overallBudgetJson("3000", "MONTHLY")))
            .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/internal/users/{userId}/export-data", userId))
            .andExpect(status().isOk())
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("MONTHLY");
        assertThat(body).contains("3000");
    }
}

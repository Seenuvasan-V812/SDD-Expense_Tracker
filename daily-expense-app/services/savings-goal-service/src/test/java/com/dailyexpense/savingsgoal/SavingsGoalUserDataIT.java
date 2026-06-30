package com.dailyexpense.savingsgoal;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * T116 — Internal export-data endpoint: GET /internal/users/{userId}/export-data.
 * AL-1: no expense_db / category_db / budget_db SQL.
 */
class SavingsGoalUserDataIT extends AbstractSavingsGoalServiceIT {

    @Test
    void exportData_noAuthRequired_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        mockMvc.perform(get("/internal/users/{userId}/export-data", userId))
            .andExpect(status().isOk());
    }

    @Test
    void exportData_returnsJsonWithSavingsGoalsKey() throws Exception {
        UUID userId = UUID.randomUUID();
        MvcResult result = mockMvc.perform(get("/internal/users/{userId}/export-data", userId))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"savingsGoals\"");
    }

    @Test
    void exportData_unknownUser_returnsEmptyGoalsList() throws Exception {
        UUID unknownUser = UUID.randomUUID();
        MvcResult result = mockMvc.perform(get("/internal/users/{userId}/export-data", unknownUser))
            .andExpect(status().isOk())
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"savingsGoals\"");
        assertThat(body).contains("[]");
    }

    @Test
    void exportData_withGoal_includesGoalFields() throws Exception {
        UUID userId = UUID.randomUUID();

        // Create a goal via the authenticated API
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/savings-goals")
                .headers(authHeaders(userId))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(createGoalJson("Export Test Goal", "5000.00")))
            .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/internal/users/{userId}/export-data", userId))
            .andExpect(status().isOk())
            .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Export Test Goal");
        assertThat(body).contains("5000.00");
    }
}

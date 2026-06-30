package com.dailyexpense.expense;

import com.dailyexpense.expense.port.CategoryLookupPort;
import com.dailyexpense.expense.port.CategoryValidationResponse;
import com.dailyexpense.expense.repository.ExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * T065 gate — CsvImportIT.
 * Proves: valid rows → SUCCEEDED, formula injection neutralized,
 * missing required field → FAILED, Idempotency-Key dedup, ≤10 MB limit.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CsvImportIT extends AbstractExpenseServiceIT {

    @MockBean
    CategoryLookupPort categoryLookupPort;

    @Autowired
    ExpenseRepository expenseRepository;

    private static final UUID USER_A = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();

    @BeforeEach
    void setupMocks() {
        when(categoryLookupPort.validate(any(), any(), any()))
            .thenReturn(new CategoryValidationResponse(CATEGORY_ID, "Food", "EXPENSE", "NONE"));
    }

    @Test
    void import_validCsv_allRowsSucceeded() throws Exception {
        String csv = "date,amount,payment_method,description\n"
            + "2026-06-28,100.00,UPI,Coffee\n"
            + "2026-06-27,200.00,CASH,Lunch\n";

        mockMvc.perform(multipart("/api/v1/expenses/import")
                .file(new MockMultipartFile("file", "e.csv", "text/csv",
                    csv.getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", bearerToken(USER_A)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRows").value(2))
            .andExpect(jsonPath("$.succeeded").value(2))
            .andExpect(jsonPath("$.failed").value(0));
    }

    @Test
    void import_formulaInjection_neutralized() throws Exception {
        // Row with formula-injection chars in description and merchant
        String csv = "date,amount,payment_method,description,merchant\n"
            + "2026-06-28,50.00,UPI,=SUM(A1:A10),+cmd\n";

        MvcResult result = mockMvc.perform(multipart("/api/v1/expenses/import")
                .file(new MockMultipartFile("file", "e.csv", "text/csv",
                    csv.getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", bearerToken(USER_A)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results[0].status").value("SUCCEEDED"))
            .andReturn();

        // Verify stored expense does NOT have formula chars at start
        long expenseCountAfter = expenseRepository.findAll().stream()
            .filter(e -> USER_A.equals(e.getUserId()))
            .filter(e -> e.getDescription() != null && e.getDescription().startsWith("="))
            .count();
        assertThat(expenseCountAfter).isZero();
    }

    @Test
    void import_missingRequiredDate_rowFailed() throws Exception {
        String csv = "date,amount,payment_method\n"
            + ",100.00,UPI\n"; // empty date

        mockMvc.perform(multipart("/api/v1/expenses/import")
                .file(new MockMultipartFile("file", "e.csv", "text/csv",
                    csv.getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", bearerToken(USER_A)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.failed").value(1))
            .andExpect(jsonPath("$.results[0].status").value("FAILED"))
            .andExpect(jsonPath("$.results[0].error", containsString("date")));
    }

    @Test
    void import_negativeAmount_rowFailed() throws Exception {
        String csv = "date,amount,payment_method\n2026-06-28,-50.00,UPI\n";

        mockMvc.perform(multipart("/api/v1/expenses/import")
                .file(new MockMultipartFile("file", "e.csv", "text/csv",
                    csv.getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", bearerToken(USER_A)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results[0].status").value("FAILED"));
    }

    @Test
    void import_idempotencyKey_dedup() throws Exception {
        String csv = "date,amount,payment_method\n2026-06-28,300.00,UPI\n";
        String idempotencyKey = "idem-key-" + UUID.randomUUID();

        // First import
        mockMvc.perform(multipart("/api/v1/expenses/import")
                .file(new MockMultipartFile("file", "e.csv", "text/csv",
                    csv.getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", bearerToken(USER_A))
                .header("Idempotency-Key", idempotencyKey))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRows").value(1));

        long countAfterFirst = expenseRepository.findAll().stream()
            .filter(e -> USER_A.equals(e.getUserId()))
            .count();

        // Second import with same key — should return cached result, no new expenses
        mockMvc.perform(multipart("/api/v1/expenses/import")
                .file(new MockMultipartFile("file", "e.csv", "text/csv",
                    csv.getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", bearerToken(USER_A))
                .header("Idempotency-Key", idempotencyKey))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRows").value(1));

        long countAfterSecond = expenseRepository.findAll().stream()
            .filter(e -> USER_A.equals(e.getUserId()))
            .count();

        // No new expenses created on second import
        assertThat(countAfterSecond).isEqualTo(countAfterFirst);
    }

    @Test
    void import_exceedsMaxSize_returns400() throws Exception {
        // 10 MB + 1 byte
        byte[] tooBig = new byte[10 * 1024 * 1024 + 1];

        mockMvc.perform(multipart("/api/v1/expenses/import")
                .file(new MockMultipartFile("file", "big.csv", "text/csv", tooBig))
                .header("Authorization", bearerToken(USER_A)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void import_savingsGoalId_succeededWithWarning() throws Exception {
        String csv = "date,amount,payment_method,savings_goal_id\n"
            + "2026-06-28,100.00,UPI," + UUID.randomUUID() + "\n";

        mockMvc.perform(multipart("/api/v1/expenses/import")
                .file(new MockMultipartFile("file", "e.csv", "text/csv",
                    csv.getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", bearerToken(USER_A)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results[0].status").value("SUCCEEDED_WITH_WARNING"));
    }
}

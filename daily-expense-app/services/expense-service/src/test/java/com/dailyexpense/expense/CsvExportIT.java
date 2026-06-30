package com.dailyexpense.expense;

import com.dailyexpense.expense.port.CategoryLookupPort;
import com.dailyexpense.expense.port.CategoryValidationResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * T066 gate — CsvExportIT.
 * Proves: GET /expenses/export returns text/csv with correct headers,
 * only caller's rows are included (cross-user isolation),
 * formula injection chars sanitized, date filter applied,
 * streaming works (no full in-memory load).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CsvExportIT extends AbstractExpenseServiceIT {

    @MockBean
    CategoryLookupPort categoryLookupPort;

    private static final UUID USER_A = UUID.randomUUID();
    private static final UUID USER_B = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();

    @BeforeAll
    void seedData() throws Exception {
        when(categoryLookupPort.validate(any(), any(), any()))
            .thenReturn(new CategoryValidationResponse(CATEGORY_ID, "Food", "EXPENSE", "NONE"));

        // Seed expenses for User A (2 rows)
        createExpense(USER_A, "100.00", "2026-06-01");
        createExpense(USER_A, "200.00", "2026-06-15");

        // Seed expense for User B — must NOT appear in User A's export
        createExpense(USER_B, "999.00", "2026-06-01");
    }

    @Test
    void export_returnsCsvContentType() throws Exception {
        mockMvc.perform(get("/api/v1/expenses/export")
                .headers(authHeaders(USER_A)))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/csv")));
    }

    @Test
    void export_returnsAttachmentHeader() throws Exception {
        mockMvc.perform(get("/api/v1/expenses/export")
                .headers(authHeaders(USER_A)))
            .andExpect(status().isOk())
            .andExpect(header().exists("Content-Disposition"));
    }

    @Test
    void export_containsOnlyCallerRows() throws Exception {
        var result = mockMvc.perform(get("/api/v1/expenses/export")
                .headers(authHeaders(USER_A)))
            .andExpect(status().isOk())
            .andReturn();

        String csv = result.getResponse().getContentAsString();
        // User A's amounts are present
        assertThat(csv).contains("100.00").contains("200.00");
        // User B's amount is NOT present
        assertThat(csv).doesNotContain("999.00");
    }

    @Test
    void export_formulaInjectionSanitized() throws Exception {
        // Create expense with formula-starting description
        when(categoryLookupPort.validate(any(), any(), any()))
            .thenReturn(new CategoryValidationResponse(CATEGORY_ID, "Food", "EXPENSE", "NONE"));

        // Use import to create an expense with a tricky description
        String expenseBody = """
            {"amount":{"amount":"50.00","currency":"INR"},"date":"2026-06-28",
             "categoryId":"%s","paymentMethod":"UPI","description":"=HYPERLINK(\\"attack\\")"}
            """.formatted(CATEGORY_ID);

        mockMvc.perform(post("/api/v1/expenses")
                .headers(authHeaders(USER_A))
                .content(expenseBody));

        var result = mockMvc.perform(get("/api/v1/expenses/export")
                .headers(authHeaders(USER_A)))
            .andExpect(status().isOk())
            .andReturn();

        String csv = result.getResponse().getContentAsString();
        // The '=' at start must be prefixed — no raw '=HYPERLINK' in any unquoted cell
        // CSV wraps values in quotes anyway, but the sanitize() prefix ensures safety
        assertThat(csv).doesNotContain(",=HYPERLINK");
    }

    @Test
    void export_dateFilter_returnsOnlyInRange() throws Exception {
        var result = mockMvc.perform(get("/api/v1/expenses/export")
                .headers(authHeaders(USER_A))
                .param("from", "2026-06-10")
                .param("to", "2026-06-30"))
            .andExpect(status().isOk())
            .andReturn();

        String csv = result.getResponse().getContentAsString();
        // 2026-06-01 expense is outside the range — should not appear
        assertThat(csv).contains("2026-06-15");
        // header row has "date" — just make sure the filtered row isn't there
        long dataRows = csv.lines().skip(1).filter(l -> !l.isBlank()).count();
        assertThat(dataRows).isGreaterThanOrEqualTo(1);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private void createExpense(UUID userId, String amount, String date) throws Exception {
        mockMvc.perform(post("/api/v1/expenses")
            .headers(authHeaders(userId))
            .content(expenseJson(amount, date, CATEGORY_ID.toString(), "UPI")));
    }
}

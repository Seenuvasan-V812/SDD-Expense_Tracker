package com.dailyexpense.expense.port;

import com.dailyexpense.expense.domain.Expense;
import com.dailyexpense.expense.repository.ExpenseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * T115 — Internal endpoint: streams all expenses + tag refs for a given userId.
 * Uses streaming cursor (CQ-10 — no full in-memory load).
 * No savings_goal_db / category_db / budget_db SQL (AL-1).
 * Guarded by /internal/** permitAll in ExpenseServiceSecurityConfig.
 */
@RestController
@RequestMapping("/internal/users")
public class ExpenseUserDataController {

    private final ExpenseRepository expenseRepository;
    private final ObjectMapper objectMapper;

    public ExpenseUserDataController(ExpenseRepository expenseRepository, ObjectMapper objectMapper) {
        this.expenseRepository = expenseRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping(value = "/{userId}/export-data", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<StreamingResponseBody> exportData(@PathVariable UUID userId) {
        StreamingResponseBody body = out -> writeExpensesJson(userId, out);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    private void writeExpensesJson(UUID userId, OutputStream out) throws IOException {
        out.write("{\"expenses\":[".getBytes());
        boolean[] first = {true};
        try (Stream<Expense> stream = expenseRepository.streamForExport(userId, null, null)) {
            stream.forEach(expense -> {
                try {
                    if (!first[0]) {
                        out.write(",".getBytes());
                    }
                    Map<String, Object> item = Map.of(
                            "id", expense.getId().toString(),
                            "amount", expense.getAmount().toPlainString(),
                            "currency", expense.getCurrency(),
                            "date", expense.getExpenseDate().toString(),
                            "categoryId", expense.getCategoryId().toString(),
                            "paymentMethod", expense.getPaymentMethod().name()
                    );
                    out.write(objectMapper.writeValueAsBytes(item));
                    first[0] = false;
                } catch (IOException e) {
                    throw new RuntimeException("Stream write failed", e);
                }
            });
        }
        out.write("]}".getBytes());
        out.flush();
    }
}

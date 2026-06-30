package com.dailyexpense.expense.service;

import com.dailyexpense.expense.domain.Expense;
import com.dailyexpense.expense.repository.ExpenseRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * T066 — Streaming CSV export (CQ-10: no full in-memory load).
 * Rows streamed directly from DB cursor to HTTP response via OutputStream.
 * Formula-injection chars sanitized in every cell.
 */
@Service
public class ExpenseExportService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseExportService.class);

    private static final String[] HEADERS = {
        "date", "amount", "currency", "category_id", "payment_method",
        "description", "merchant", "notes", "savings_goal_id", "tags"
    };

    private static final CSVFormat FORMAT = CSVFormat.RFC4180.builder()
        .setHeader(HEADERS)
        .build();

    private final ExpenseRepository expenseRepository;

    public ExpenseExportService(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    /**
     * Streams CSV for the caller's expenses in [from, to] to the given OutputStream.
     * Uses a server-side Hibernate scroll cursor — no full result set loaded in memory.
     * CQ-10: streaming; only caller's rows.
     */
    @Transactional(readOnly = true)
    public void exportToCsv(UUID userId, LocalDate from, LocalDate to,
                            OutputStream outputStream) throws IOException {
        log.info("CSV export start userId={} from={} to={}", userId, from, to);

        try (Stream<Expense> expenses = expenseRepository.streamForExport(userId, from, to);
             PrintWriter writer = new PrintWriter(
                 new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), false);
             CSVPrinter printer = new CSVPrinter(writer, FORMAT)) {

            expenses.forEach(e -> {
                try {
                    printer.printRecord(
                        sanitize(e.getExpenseDate().toString()),
                        sanitize(e.getAmount().toPlainString()),
                        sanitize(e.getCurrency()),
                        sanitize(e.getCategoryId() != null ? e.getCategoryId().toString() : ""),
                        sanitize(e.getPaymentMethod().name()),
                        sanitize(e.getDescription()),
                        sanitize(e.getMerchant()),
                        sanitize(e.getNotes()),
                        sanitize(e.getSavingsGoalId() != null ? e.getSavingsGoalId().toString() : ""),
                        sanitize(e.getTagIds() != null
                            ? String.join("|", e.getTagIds().stream().map(UUID::toString).toArray(String[]::new))
                            : "")
                    );
                } catch (IOException ex) {
                    throw new RuntimeException("CSV write error", ex);
                }
            });

            printer.flush();
        }
        log.info("CSV export complete userId={}", userId);
    }

    /** Prefix formula-injection trigger chars with apostrophe to neutralize. */
    private String sanitize(String value) {
        if (value == null || value.isEmpty()) return "";
        char c = value.charAt(0);
        if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') {
            return "'" + value;
        }
        return value;
    }
}

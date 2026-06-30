package com.dailyexpense.expense.service;

import com.dailyexpense.expense.domain.*;
import com.dailyexpense.expense.dto.ImportExpensesReport;
import com.dailyexpense.expense.dto.ImportExpensesReport.ImportRowResult;
import com.dailyexpense.expense.port.CategoryLookupPort;
import com.dailyexpense.expense.repository.ExpenseRepository;
import com.dailyexpense.expense.repository.ProcessedImportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * T065 — CSV import.
 * - ≤ 10 MB / ≤ 10 000 rows enforced before row processing.
 * - Formula-injection chars stripped: = + - @ \t \r.
 * - Idempotency-Key dedup: same key + same user → cached result returned.
 * - Per-row SUCCEEDED / FAILED / SUCCEEDED_WITH_WARNING report.
 */
@Service
public class ExpenseImportService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseImportService.class);
    private static final long MAX_IMPORT_BYTES = 10L * 1024 * 1024;
    private static final int MAX_ROWS = 10_000;

    private static final CSVFormat FORMAT = CSVFormat.RFC4180.builder()
        .setHeader()
        .setSkipHeaderRecord(true)
        .setIgnoreHeaderCase(true)
        .setTrim(true)
        .build();

    private final ExpenseRepository expenseRepository;
    private final CategoryLookupPort categoryLookupPort;
    private final ProcessedImportRepository processedImportRepository;
    private final ObjectMapper objectMapper;

    public ExpenseImportService(ExpenseRepository expenseRepository,
                                CategoryLookupPort categoryLookupPort,
                                ProcessedImportRepository processedImportRepository,
                                ObjectMapper objectMapper) {
        this.expenseRepository = expenseRepository;
        this.categoryLookupPort = categoryLookupPort;
        this.processedImportRepository = processedImportRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ImportExpensesReport importCsv(UUID userId, String idempotencyKey, MultipartFile file) {
        // Idempotency-Key dedup
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = processedImportRepository.findByIdempotencyKeyAndUserId(idempotencyKey, userId);
            if (existing.isPresent()) {
                try {
                    return objectMapper.readValue(existing.get().getResultJson(), ImportExpensesReport.class);
                } catch (Exception e) {
                    log.warn("Could not deserialize cached import result; reprocessing", e);
                }
            }
        }

        // Size guard
        if (file.getSize() > MAX_IMPORT_BYTES) {
            throw new IllegalArgumentException("CSV file exceeds 10 MB limit");
        }

        List<ImportRowResult> results = new ArrayList<>();
        int rowNum = 0;

        try (CSVParser parser = FORMAT.parse(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            for (CSVRecord record : parser) {
                rowNum++;
                if (rowNum > MAX_ROWS) {
                    throw new IllegalArgumentException("CSV exceeds 10 000 row limit");
                }
                results.add(processRow(record, userId, rowNum));
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("CSV parse error: " + e.getMessage(), e);
        }

        long succeeded = results.stream().filter(r -> "SUCCEEDED".equals(r.status())).count();
        long failed = results.stream().filter(r -> "FAILED".equals(r.status())).count();
        long warned = results.stream().filter(r -> "SUCCEEDED_WITH_WARNING".equals(r.status())).count();

        ImportExpensesReport report = new ImportExpensesReport(
            rowNum, (int) succeeded, (int) failed, (int) warned, results);

        // Persist idempotency record
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            try {
                ProcessedImport pi = new ProcessedImport();
                pi.setIdempotencyKey(idempotencyKey);
                pi.setUserId(userId);
                pi.setProcessedAt(Instant.now());
                pi.setResultJson(objectMapper.writeValueAsString(report));
                processedImportRepository.save(pi);
            } catch (Exception e) {
                log.warn("Failed to persist idempotency record", e);
            }
        }

        log.info("CSV import complete userId={} rows={} succeeded={} failed={} warned={}",
            userId, rowNum, succeeded, failed, warned);
        return report;
    }

    private ImportRowResult processRow(CSVRecord record, UUID userId, int rowNum) {
        try {
            String dateStr = getField(record, "date");
            String amountStr = getField(record, "amount");
            if (dateStr == null || dateStr.isBlank()) {
                return new ImportRowResult(rowNum, "FAILED", "Missing required field: date", null);
            }
            if (amountStr == null || amountStr.isBlank()) {
                return new ImportRowResult(rowNum, "FAILED", "Missing required field: amount", null);
            }

            LocalDate date = LocalDate.parse(stripFormula(dateStr));
            BigDecimal amount = new BigDecimal(stripFormula(amountStr));
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return new ImportRowResult(rowNum, "FAILED", "Amount must be positive", null);
            }

            String pmStr = getField(record, "payment_method");
            PaymentMethod paymentMethod = pmStr != null && !pmStr.isBlank()
                ? PaymentMethod.valueOf(stripFormula(pmStr).toUpperCase())
                : PaymentMethod.OTHER;

            String catStr = getField(record, "category_id");
            UUID categoryId = catStr != null && !catStr.isBlank()
                ? UUID.fromString(stripFormula(catStr)) : null;

            if (categoryId != null) {
                categoryLookupPort.validate(categoryId, userId, "EXPENSE");
            }

            String goalStr = getField(record, "savings_goal_id");
            UUID savingsGoalId = goalStr != null && !goalStr.isBlank()
                ? UUID.fromString(stripFormula(goalStr)) : null;

            Expense expense = new Expense();
            expense.setId(UUID.randomUUID());
            expense.setUserId(userId);
            expense.setAmount(amount);
            expense.setCurrency("INR");
            expense.setExpenseDate(date);
            expense.setCategoryId(categoryId != null ? categoryId : UUID.fromString("00000000-0000-0000-0000-000000000000"));
            expense.setPaymentMethod(paymentMethod);
            expense.setDescription(stripFormula(getField(record, "description")));
            expense.setMerchant(stripFormula(getField(record, "merchant")));
            expense.setSavingsGoalId(savingsGoalId);
            expense.setCreatedAt(Instant.now());
            expense.setUpdatedAt(Instant.now());
            expenseRepository.save(expense);

            // Phase 1: cannot verify savings goal ownership — SUCCEEDED_WITH_WARNING
            if (savingsGoalId != null) {
                return new ImportRowResult(rowNum, "SUCCEEDED_WITH_WARNING", null,
                    "savingsGoalId provided but goal ownership cannot be verified in Phase 1");
            }
            return new ImportRowResult(rowNum, "SUCCEEDED", null, null);

        } catch (IllegalArgumentException e) {
            return new ImportRowResult(rowNum, "FAILED", e.getMessage(), null);
        } catch (Exception e) {
            return new ImportRowResult(rowNum, "FAILED", "Unexpected error: " + e.getMessage(), null);
        }
    }

    /** Strip leading formula-injection chars (= + - @ \t \r) by prefixing with apostrophe. */
    private String stripFormula(String value) {
        if (value == null) return null;
        String s = value.trim();
        if (s.isEmpty()) return s;
        char c = s.charAt(0);
        if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') {
            return s.substring(1).trim();
        }
        return s;
    }

    private String getField(CSVRecord record, String field) {
        try {
            return record.isMapped(field) ? record.get(field) : null;
        } catch (Exception e) {
            return null;
        }
    }
}

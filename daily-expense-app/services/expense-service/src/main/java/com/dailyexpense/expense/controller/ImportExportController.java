package com.dailyexpense.expense.controller;

import com.dailyexpense.expense.dto.ImportExpensesReport;
import com.dailyexpense.expense.service.ExpenseExportService;
import com.dailyexpense.expense.service.ExpenseImportService;
import com.dailyexpense.shared.security.CallerContext;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDate;

/**
 * T065/T066 — CSV import and streaming export at /api/v1/expenses/import and /export.
 * Export: streams text/csv with no full in-memory load (CQ-10).
 * Import: ≤10 MB / ≤10 000 rows; formula injection neutralized; Idempotency-Key dedup.
 */
@RestController
@RequestMapping("/api/v1/expenses")
public class ImportExportController {

    private final ExpenseImportService importService;
    private final ExpenseExportService exportService;

    public ImportExportController(ExpenseImportService importService,
                                  ExpenseExportService exportService) {
        this.importService = importService;
        this.exportService = exportService;
    }

    /** T065: POST /api/v1/expenses/import — multipart CSV upload. */
    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public ResponseEntity<ImportExpensesReport> importCsv(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal CallerContext caller) {
        ImportExpensesReport report = importService.importCsv(caller.userId(), idempotencyKey, file);
        return ResponseEntity.ok(report);
    }

    /** T066: GET /api/v1/expenses/export — streaming CSV (CQ-10: no full in-memory load). */
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> exportCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal CallerContext caller) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate effectiveTo   = to   != null ? to   : LocalDate.now();
        StreamingResponseBody body = out ->
            exportService.exportToCsv(caller.userId(), effectiveFrom, effectiveTo, out);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"expenses.csv\"")
            .body(body);
    }
}

package com.dailyexpense.expense.controller;

import com.dailyexpense.expense.domain.Expense;
import com.dailyexpense.expense.domain.PaymentMethod;
import com.dailyexpense.expense.dto.CreateExpenseRequest;
import com.dailyexpense.expense.dto.ExpenseResponse;
import com.dailyexpense.expense.dto.UpdateExpenseRequest;
import com.dailyexpense.expense.service.ExpenseService;
import com.dailyexpense.shared.api.PageResponse;
import com.dailyexpense.shared.security.CallerContext;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

/**
 * T054/T055/T056 — Expense CRUD API at /api/v1/expenses.
 * Identity from JWT CallerContext (AL-5). 403-never-404 (INV-1/SEC-3).
 */
@RestController
@RequestMapping("/api/v1/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    // T054 — POST /api/v1/expenses → 201 + Location
    @PostMapping
    public ResponseEntity<Void> create(
            @AuthenticationPrincipal CallerContext caller,
            @Valid @RequestBody CreateExpenseRequest request) {
        Expense created = expenseService.create(caller.userId(), request);
        return ResponseEntity.created(URI.create("/api/v1/expenses/" + created.getId())).build();
    }

    // T055 — GET /api/v1/expenses — paginated, filtered, sorted
    @GetMapping
    public ResponseEntity<PageResponse<ExpenseResponse>> list(
            @AuthenticationPrincipal CallerContext caller,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(required = false) UUID savingsGoalId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "date") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        int cappedSize = Math.min(size, 100);
        return ResponseEntity.ok(expenseService.list(caller.userId(), from, to, categoryId,
            paymentMethod, savingsGoalId, tagId, page, cappedSize, sortBy, sortDir));
    }

    // T056 — GET /api/v1/expenses/{id}
    @GetMapping("/{id}")
    public ResponseEntity<ExpenseResponse> getOne(
            @AuthenticationPrincipal CallerContext caller,
            @PathVariable UUID id) {
        return ResponseEntity.ok(expenseService.getOne(caller.userId(), id));
    }

    // T056 — PUT /api/v1/expenses/{id}
    @PutMapping("/{id}")
    public ResponseEntity<ExpenseResponse> update(
            @AuthenticationPrincipal CallerContext caller,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateExpenseRequest request) {
        return ResponseEntity.ok(expenseService.update(caller.userId(), id, request));
    }

    // T056 — DELETE /api/v1/expenses/{id} → 204
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CallerContext caller,
            @PathVariable UUID id) {
        expenseService.delete(caller.userId(), id);
        return ResponseEntity.noContent().build();
    }
}

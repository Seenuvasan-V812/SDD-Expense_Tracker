package com.dailyexpense.expense.controller;

import com.dailyexpense.expense.dto.CreateRecurringExpenseRequest;
import com.dailyexpense.expense.dto.RecurringExpenseResponse;
import com.dailyexpense.expense.service.RecurringExpenseService;
import com.dailyexpense.shared.security.CallerContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

/**
 * T063 — RecurringExpense CRUD at /api/v1/recurring-expenses.
 * PUT /{expenseId}?scope=THIS|THIS_AND_FUTURE — {expenseId} is a generated Expense ID.
 */
@RestController
@RequestMapping("/api/v1/recurring-expenses")
public class RecurringExpenseController {

    private final RecurringExpenseService recurringExpenseService;

    public RecurringExpenseController(RecurringExpenseService recurringExpenseService) {
        this.recurringExpenseService = recurringExpenseService;
    }

    @PostMapping
    public ResponseEntity<RecurringExpenseResponse> create(
            @Valid @RequestBody CreateRecurringExpenseRequest request,
            @AuthenticationPrincipal CallerContext caller) {
        RecurringExpenseResponse response = recurringExpenseService.create(caller.userId(), request);
        return ResponseEntity
            .created(URI.create("/api/v1/recurring-expenses/" + response.recurringExpenseId()))
            .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecurringExpenseResponse> getOne(
            @PathVariable UUID id,
            @AuthenticationPrincipal CallerContext caller) {
        return ResponseEntity.ok(recurringExpenseService.getOne(caller.userId(), id));
    }

    /** Edit scope=THIS — updates only the specified occurrence Expense. */
    @PutMapping("/{expenseId}")
    public ResponseEntity<Void> edit(
            @PathVariable UUID expenseId,
            @RequestParam(defaultValue = "THIS") String scope,
            @Valid @RequestBody CreateRecurringExpenseRequest request,
            @AuthenticationPrincipal CallerContext caller) {
        if ("THIS_AND_FUTURE".equalsIgnoreCase(scope)) {
            recurringExpenseService.editThisAndFuture(caller.userId(), expenseId, request);
        } else {
            recurringExpenseService.editThisOccurrence(caller.userId(), expenseId, request);
        }
        return ResponseEntity.ok().build();
    }
}

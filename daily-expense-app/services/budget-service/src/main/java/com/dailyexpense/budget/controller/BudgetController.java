package com.dailyexpense.budget.controller;

import com.dailyexpense.budget.dto.*;
import com.dailyexpense.budget.service.BudgetAuthoringService;
import com.dailyexpense.budget.service.BudgetStatusService;
import com.dailyexpense.shared.api.PageResponse;
import com.dailyexpense.shared.security.CallerContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * T086/T087/T088/T092 — Budget REST endpoints.
 * AL-4: controller never exposes domain entities directly — DTO only.
 * 403-never-404: ForbiddenOwnershipException → 403 via GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {

    private final BudgetAuthoringService authoringService;
    private final BudgetStatusService statusService;

    public BudgetController(BudgetAuthoringService authoringService,
                             BudgetStatusService statusService) {
        this.authoringService = authoringService;
        this.statusService = statusService;
    }

    @PostMapping
    public ResponseEntity<BudgetResponse> create(
            @AuthenticationPrincipal CallerContext caller,
            @Valid @RequestBody CreateBudgetRequest req,
            @RequestHeader(value = "Authorization", required = false) String bearerToken) {
        BudgetResponse resp = authoringService.create(caller.userId(), req, bearerToken);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}").buildAndExpand(resp.id()).toUri();
        return ResponseEntity.created(location).body(resp);
    }

    @GetMapping
    public ResponseEntity<PageResponse<BudgetResponse>> list(
            @AuthenticationPrincipal CallerContext caller,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(authoringService.list(caller.userId(), active, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BudgetResponse> get(
            @AuthenticationPrincipal CallerContext caller,
            @PathVariable UUID id) {
        return ResponseEntity.ok(authoringService.get(id, caller.userId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BudgetResponse> update(
            @AuthenticationPrincipal CallerContext caller,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBudgetRequest req) {
        return ResponseEntity.ok(authoringService.update(id, caller.userId(), req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CallerContext caller,
            @PathVariable UUID id) {
        authoringService.delete(id, caller.userId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activation")
    public ResponseEntity<BudgetResponse> setActivation(
            @AuthenticationPrincipal CallerContext caller,
            @PathVariable UUID id,
            @Valid @RequestBody ActivationRequest req) {
        return ResponseEntity.ok(authoringService.setActivation(id, caller.userId(), req));
    }

    @PatchMapping("/{id}/rollover")
    public ResponseEntity<BudgetResponse> setRollover(
            @AuthenticationPrincipal CallerContext caller,
            @PathVariable UUID id,
            @Valid @RequestBody RolloverToggleRequest req) {
        return ResponseEntity.ok(authoringService.setRollover(id, caller.userId(), req));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<BudgetStatusResponse> getStatus(
            @AuthenticationPrincipal CallerContext caller,
            @PathVariable UUID id) {
        return ResponseEntity.ok(statusService.getStatus(id, caller.userId()));
    }
}

package com.dailyexpense.savingsgoal.controller;

import com.dailyexpense.savingsgoal.domain.GoalStatus;
import com.dailyexpense.savingsgoal.domain.SavingsGoal;
import com.dailyexpense.savingsgoal.dto.*;
import com.dailyexpense.savingsgoal.service.ContributionService;
import com.dailyexpense.savingsgoal.service.GoalLifecycleService;
import com.dailyexpense.savingsgoal.service.GoalProjectionService;
import com.dailyexpense.savingsgoal.service.SavingsGoalService;
import com.dailyexpense.shared.api.PageResponse;
import com.dailyexpense.shared.money.MoneyDto;
import com.dailyexpense.shared.security.CallerContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * T072 — Goal CRUD at /api/v1/savings-goals.
 * T075 — GET /contributions history.
 * Identity from JWT CallerContext (AL-5). 403-never-404 (INV-1/SEC-3).
 */
@RestController
@RequestMapping("/api/v1/savings-goals")
public class SavingsGoalController {

    private final SavingsGoalService goalService;
    private final GoalLifecycleService lifecycleService;
    private final ContributionService contributionService;
    private final GoalProjectionService projectionService;

    public SavingsGoalController(SavingsGoalService goalService,
                                  GoalLifecycleService lifecycleService,
                                  ContributionService contributionService,
                                  GoalProjectionService projectionService) {
        this.goalService = goalService;
        this.lifecycleService = lifecycleService;
        this.contributionService = contributionService;
        this.projectionService = projectionService;
    }

    // POST /savings-goals → 201 + Location
    @PostMapping
    public ResponseEntity<Void> create(
            @AuthenticationPrincipal CallerContext caller,
            @Valid @RequestBody CreateSavingsGoalRequest request) {
        SavingsGoal goal = goalService.create(caller.userId(), request);
        return ResponseEntity.created(URI.create("/api/v1/savings-goals/" + goal.getId())).build();
    }

    // GET /savings-goals?status=
    @GetMapping
    public ResponseEntity<PageResponse<SavingsGoalResponse>> list(
            @AuthenticationPrincipal CallerContext caller,
            @RequestParam(required = false) GoalStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int cappedSize = Math.min(size, 100);
        return ResponseEntity.ok(goalService.list(caller.userId(), status, page, cappedSize));
    }

    // GET /savings-goals/{id}
    @GetMapping("/{id}")
    public ResponseEntity<SavingsGoalResponse> getDetail(
            @AuthenticationPrincipal CallerContext caller,
            @PathVariable UUID id) {
        SavingsGoal goal = goalService.findOwnedGoal(id, caller.userId());
        LocalDate projected = projectionService.project(goal);
        SavingsGoalResponse base = goalService.toResponse(goal);
        SavingsGoalResponse withProjection = withProjection(base, projected);
        return ResponseEntity.ok(withProjection);
    }

    // PUT /savings-goals/{id}
    @PutMapping("/{id}")
    public ResponseEntity<SavingsGoalResponse> update(
            @AuthenticationPrincipal CallerContext caller,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSavingsGoalRequest request) {
        return ResponseEntity.ok(goalService.update(id, caller.userId(), request));
    }

    // DELETE /savings-goals/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CallerContext caller,
            @PathVariable UUID id) {
        lifecycleService.delete(id, caller.userId());
        return ResponseEntity.noContent().build();
    }

    // PATCH /savings-goals/{id}/status
    @PatchMapping("/{id}/status")
    public ResponseEntity<SavingsGoalResponse> changeStatus(
            @AuthenticationPrincipal CallerContext caller,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateGoalStatusRequest request) {
        return ResponseEntity.ok(lifecycleService.changeStatus(id, caller.userId(), request));
    }

    // POST /savings-goals/{id}/contributions → 201 + Location
    @PostMapping("/{id}/contributions")
    public ResponseEntity<Void> recordContribution(
            @AuthenticationPrincipal CallerContext caller,
            @PathVariable UUID id,
            @Valid @RequestBody RecordContributionRequest request,
            HttpServletRequest httpRequest) {
        String bearerToken = httpRequest.getHeader("Authorization");
        var entry = contributionService.recordPrimary(id, caller.userId(), request, bearerToken);
        return ResponseEntity.created(
            URI.create("/api/v1/savings-goals/" + id + "/contributions/" + entry.getId())).build();
    }

    // GET /savings-goals/{id}/contributions
    @GetMapping("/{id}/contributions")
    public ResponseEntity<PageResponse<ContributionEntryResponse>> listContributions(
            @AuthenticationPrincipal CallerContext caller,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int cappedSize = Math.min(size, 100);
        return ResponseEntity.ok(contributionService.listHistory(id, caller.userId(), page, cappedSize));
    }

    private SavingsGoalResponse withProjection(SavingsGoalResponse base, LocalDate projected) {
        return new SavingsGoalResponse(
            base.savingsGoalId(), base.name(), base.targetAmount(), base.targetDate(),
            base.description(), base.status(), base.totalContributed(), base.remainingAmount(),
            base.percentAchieved(), projected, base.icon(), base.color(), base.createdAt()
        );
    }
}

package com.dailyexpense.savingsgoal.port;

import com.dailyexpense.savingsgoal.domain.SavingsGoal;
import com.dailyexpense.savingsgoal.repository.ContributionEntryRepository;
import com.dailyexpense.savingsgoal.repository.SavingsGoalRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * T116 — Internal endpoint: returns all savings goals + contribution history for a userId.
 * Guarded by /internal/** permitAll in SavingsGoalServiceSecurityConfig.
 * No expense_db / category_db / budget_db SQL (AL-1).
 */
@RestController
@RequestMapping("/internal/users")
public class SavingsGoalUserDataController {

    private final SavingsGoalRepository savingsGoalRepository;
    private final ContributionEntryRepository contributionEntryRepository;

    public SavingsGoalUserDataController(SavingsGoalRepository savingsGoalRepository,
                                         ContributionEntryRepository contributionEntryRepository) {
        this.savingsGoalRepository = savingsGoalRepository;
        this.contributionEntryRepository = contributionEntryRepository;
    }

    @GetMapping("/{userId}/export-data")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> exportData(@PathVariable UUID userId) {
        List<SavingsGoal> goals = savingsGoalRepository
                .findByUserId(userId, PageRequest.of(0, Integer.MAX_VALUE))
                .getContent();

        List<Map<String, Object>> goalItems = goals.stream()
                .map(g -> {
                    List<Map<String, Object>> contributions = contributionEntryRepository
                            .findBySavingsGoalId(g.getId(), PageRequest.of(0, Integer.MAX_VALUE))
                            .getContent().stream()
                            .map(c -> Map.<String, Object>of(
                                    "id", c.getId().toString(),
                                    "amount", c.getAmount().toPlainString(),
                                    "source", c.getSource().name(),
                                    "entryDate", c.getEntryDate().toString()
                            ))
                            .collect(Collectors.toList());

                    return Map.<String, Object>of(
                            "id", g.getId().toString(),
                            "name", g.getName(),
                            "targetAmount", g.getTargetAmount().toPlainString(),
                            "totalContributed", g.getTotalContributed().toPlainString(),
                            "status", g.getStatus().name(),
                            "contributions", contributions
                    );
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("savingsGoals", goalItems));
    }
}

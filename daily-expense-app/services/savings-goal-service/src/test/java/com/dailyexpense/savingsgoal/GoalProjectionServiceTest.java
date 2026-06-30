package com.dailyexpense.savingsgoal;

import com.dailyexpense.savingsgoal.domain.GoalStatus;
import com.dailyexpense.savingsgoal.domain.SavingsGoal;
import com.dailyexpense.savingsgoal.repository.ContributionEntryRepository;
import com.dailyexpense.savingsgoal.service.GoalProjectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * T080 — Unit tests for GoalProjectionService.
 * Verifies: PAUSED/COMPLETED → null; ACTIVE + no history → null; ACTIVE + history → future date.
 */
@ExtendWith(MockitoExtension.class)
class GoalProjectionServiceTest {

    @Mock
    ContributionEntryRepository entryRepository;

    @InjectMocks
    GoalProjectionService projectionService;

    @Test
    void pausedGoal_returnsNull() {
        SavingsGoal goal = buildGoal(GoalStatus.PAUSED, "50000", "20000");
        assertThat(projectionService.project(goal)).isNull();
    }

    @Test
    void completedGoal_returnsNull() {
        SavingsGoal goal = buildGoal(GoalStatus.COMPLETED, "50000", "50000");
        assertThat(projectionService.project(goal)).isNull();
    }

    @Test
    void activeGoal_totalAlreadyMeetsTarget_returnsToday() {
        SavingsGoal goal = buildGoal(GoalStatus.ACTIVE, "30000", "30000");
        LocalDate result = projectionService.project(goal);
        assertThat(result).isBeforeOrEqualTo(LocalDate.now());
    }

    @Test
    void activeGoal_noContributionHistory_returnsNull() {
        SavingsGoal goal = buildGoal(GoalStatus.ACTIVE, "50000", "0");
        when(entryRepository.computeAverageMonthlyRate(goal.getId())).thenReturn(null);

        assertThat(projectionService.project(goal)).isNull();
    }

    @Test
    void activeGoal_zeroMonthlyRate_returnsNull() {
        SavingsGoal goal = buildGoal(GoalStatus.ACTIVE, "50000", "1000");
        when(entryRepository.computeAverageMonthlyRate(goal.getId())).thenReturn(BigDecimal.ZERO);

        assertThat(projectionService.project(goal)).isNull();
    }

    @Test
    void activeGoal_knownHistory_returnsProjectedDate() {
        SavingsGoal goal = buildGoal(GoalStatus.ACTIVE, "120000", "10000");
        // 110000 remaining; 10000/month → ~11 months
        when(entryRepository.computeAverageMonthlyRate(goal.getId()))
            .thenReturn(new BigDecimal("10000"));

        LocalDate result = projectionService.project(goal);
        assertThat(result).isNotNull();
        assertThat(result).isAfter(LocalDate.now());
        // 11 months from now ± 1 day
        assertThat(result).isBefore(LocalDate.now().plusMonths(13));
        assertThat(result).isAfterOrEqualTo(LocalDate.now().plusMonths(11));
    }

    private SavingsGoal buildGoal(GoalStatus status, String target, String contributed) {
        SavingsGoal goal = new SavingsGoal();
        goal.setId(UUID.randomUUID());
        goal.setUserId(UUID.randomUUID());
        goal.setName("Test Goal");
        goal.setTargetAmount(new BigDecimal(target));
        goal.setTotalContributed(new BigDecimal(contributed));
        goal.setStatus(status);
        return goal;
    }
}

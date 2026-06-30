package com.dailyexpense.savingsgoal;

import com.dailyexpense.savingsgoal.domain.GoalStatus;
import com.dailyexpense.savingsgoal.domain.SavingsGoal;
import com.dailyexpense.savingsgoal.repository.SavingsGoalRepository;
import com.dailyexpense.savingsgoal.service.GoalLifecycleService;
import com.dailyexpense.savingsgoal.service.SavingsGoalService;
import com.dailyexpense.shared.outbox.OutboxPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * T118 — goals.completed counter increments when SG-INV-6 auto-complete fires.
 * 0 PII in tags.
 */
@ExtendWith(MockitoExtension.class)
class GoalLifecycleMetricsTest {

    @Mock
    SavingsGoalRepository goalRepository;

    @Mock
    OutboxPublisher outboxPublisher;

    @Mock
    SavingsGoalService savingsGoalService;

    SimpleMeterRegistry meterRegistry;
    GoalLifecycleService lifecycleService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        lifecycleService = new GoalLifecycleService(
            goalRepository, outboxPublisher, new ObjectMapper(), savingsGoalService, meterRegistry);
    }

    @Test
    void autoComplete_incrementsGoalsCompletedCounter() {
        UUID goalId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SavingsGoal goal = activeGoal(goalId, userId, new BigDecimal("1000"), new BigDecimal("1000"));

        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));
        when(goalRepository.tryComplete(eq(goalId), eq(GoalStatus.COMPLETED), eq(GoalStatus.ACTIVE), any()))
            .thenReturn(1);

        double before = meterRegistry.counter("goals.completed").count();
        lifecycleService.checkAutoComplete(goalId, userId);
        double after = meterRegistry.counter("goals.completed").count();

        assertThat(after).isEqualTo(before + 1);
    }

    @Test
    void autoComplete_alreadyCompleted_noIncrement() {
        UUID goalId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SavingsGoal goal = activeGoal(goalId, userId, new BigDecimal("1000"), new BigDecimal("1000"));

        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));
        when(goalRepository.tryComplete(any(), any(), any(), any())).thenReturn(0);

        double before = meterRegistry.counter("goals.completed").count();
        lifecycleService.checkAutoComplete(goalId, userId);
        double after = meterRegistry.counter("goals.completed").count();

        assertThat(after).isEqualTo(before);
    }

    @Test
    void autoComplete_belowTarget_noIncrement() {
        UUID goalId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        SavingsGoal goal = activeGoal(goalId, userId, new BigDecimal("1000"), new BigDecimal("500"));

        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));

        double before = meterRegistry.counter("goals.completed").count();
        lifecycleService.checkAutoComplete(goalId, userId);
        double after = meterRegistry.counter("goals.completed").count();

        assertThat(after).isEqualTo(before);
    }

    @Test
    void counter_hasNoPiiInTags() {
        Counter counter = meterRegistry.counter("goals.completed");
        counter.getId().getTags().forEach(tag -> {
            assertThat(tag.getKey()).doesNotContainIgnoringCase("userId");
            assertThat(tag.getKey()).doesNotContainIgnoringCase("email");
            assertThat(tag.getKey()).doesNotContainIgnoringCase("amount");
        });
    }

    private SavingsGoal activeGoal(UUID id, UUID userId, BigDecimal target, BigDecimal contributed) {
        SavingsGoal g = new SavingsGoal();
        g.setId(id);
        g.setUserId(userId);
        g.setName("Test Goal");
        g.setTargetAmount(target);
        g.setTotalContributed(contributed);
        g.setStatus(GoalStatus.ACTIVE);
        g.setCreatedAt(Instant.now());
        g.setUpdatedAt(Instant.now());
        return g;
    }
}

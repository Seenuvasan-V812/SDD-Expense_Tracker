package com.dailyexpense.savingsgoal.service;

import com.dailyexpense.savingsgoal.domain.GoalStatus;
import com.dailyexpense.savingsgoal.domain.SavingsGoal;
import com.dailyexpense.savingsgoal.dto.SavingsGoalResponse;
import com.dailyexpense.savingsgoal.dto.UpdateGoalStatusRequest;
import com.dailyexpense.savingsgoal.repository.SavingsGoalRepository;
import com.dailyexpense.shared.exception.BusinessConflictException;
import com.dailyexpense.shared.outbox.EventEnvelope;
import com.dailyexpense.shared.outbox.OutboxPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * T078 — SG-INV-6 auto-complete: fires exactly once when total>=target while ACTIVE.
 * T079 — PATCH /{id}/status state machine: illegal transitions → 409.
 * T081 — SavingsGoalDeletedEvent written to outbox on delete; Expenses NOT cascaded.
 *
 * The auto-complete guard: after updating total_contributed, this service re-reads the goal
 * within the same @Transactional. If two concurrent reconcile calls both see ACTIVE and
 * both cross the threshold, only one succeeds (READ COMMITTED — second re-reads COMPLETED).
 */
@Service
public class GoalLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(GoalLifecycleService.class);

    private final SavingsGoalRepository goalRepository;
    private final OutboxPublisher outboxPublisher;
    private final ObjectMapper objectMapper;
    private final SavingsGoalService savingsGoalService;
    private final Counter goalsCompletedCounter;

    public GoalLifecycleService(SavingsGoalRepository goalRepository,
                                OutboxPublisher outboxPublisher,
                                ObjectMapper objectMapper,
                                SavingsGoalService savingsGoalService,
                                MeterRegistry meterRegistry) {
        this.goalRepository = goalRepository;
        this.outboxPublisher = outboxPublisher;
        this.objectMapper = objectMapper;
        this.savingsGoalService = savingsGoalService;
        this.goalsCompletedCounter = Counter.builder("goals.completed")
            .description("Total savings goals auto-completed")
            .register(meterRegistry);
    }

    // T079 — PATCH /{id}/status
    @Transactional
    public SavingsGoalResponse changeStatus(UUID goalId, UUID userId, UpdateGoalStatusRequest request) {
        SavingsGoal goal = savingsGoalService.findOwnedGoal(goalId, userId);
        GoalStatus from = goal.getStatus();
        GoalStatus to = request.status();

        assertValidTransition(from, to);

        goal.setStatus(to);
        goal.setUpdatedAt(Instant.now());
        goalRepository.save(goal);
        log.info("savingsGoal={} status {} → {} by user={}", goalId, from, to, userId);
        return savingsGoalService.toResponse(goal);
    }

    // T078 — auto-complete check (SG-INV-6). Called after every total_contributed update.
    // Uses atomic conditional UPDATE (tryComplete) so exactly one concurrent caller wins the
    // ACTIVE→COMPLETED transition; duplicate calls return 0 rows affected and skip the event.
    @Transactional
    public void checkAutoComplete(UUID goalId, UUID userId) {
        SavingsGoal goal = goalRepository.findById(goalId).orElse(null);
        if (goal == null) return;

        if (goal.getStatus() != GoalStatus.ACTIVE) return;

        if (goal.getTotalContributed().compareTo(goal.getTargetAmount()) < 0) return;

        // Atomic conditional: UPDATE ... WHERE status = ACTIVE → returns 0 if already COMPLETED
        int updated = goalRepository.tryComplete(
            goalId, GoalStatus.COMPLETED, GoalStatus.ACTIVE, Instant.now());

        if (updated == 0) {
            log.debug("savingsGoal={} already COMPLETED — skipping duplicate auto-complete", goalId);
            return;
        }

        String payload = buildPayload(Map.of(
            "goalId", goalId.toString(),
            "userId", userId.toString(),
            "totalContributed", goal.getTotalContributed().toPlainString(),
            "targetAmount", goal.getTargetAmount().toPlainString()
        ));

        outboxPublisher.publish(EventEnvelope.builder()
            .eventType("SavingsGoalCompletedEvent")
            .userId(userId)
            .payload(payload)
            .build());

        goalsCompletedCounter.increment();
        log.info("savingsGoal={} auto-completed for user={}", goalId, userId);
    }

    // T081 — delete: emit SavingsGoalDeletedEvent; Expenses NOT cascaded (SG-INV-8).
    @Transactional
    public void delete(UUID goalId, UUID userId) {
        SavingsGoal goal = savingsGoalService.findOwnedGoal(goalId, userId);

        String payload = buildPayload(Map.of(
            "goalId", goalId.toString(),
            "userId", userId.toString()
        ));

        outboxPublisher.publish(EventEnvelope.builder()
            .eventType("SavingsGoalDeletedEvent")
            .userId(userId)
            .payload(payload)
            .build());

        goalRepository.delete(goal);
        log.info("savingsGoal={} deleted by user={}, SavingsGoalDeletedEvent written to outbox", goalId, userId);
    }

    // Revert COMPLETED → ACTIVE used by reconciliation when total drops below target.
    @Transactional
    public void reopenIfCompleted(UUID goalId, UUID userId) {
        SavingsGoal goal = goalRepository.findById(goalId).orElse(null);
        if (goal == null) return;

        if (goal.getStatus() == GoalStatus.COMPLETED
                && goal.getTotalContributed().compareTo(goal.getTargetAmount()) < 0) {
            goal.setStatus(GoalStatus.ACTIVE);
            goal.setUpdatedAt(Instant.now());
            goalRepository.save(goal);
            log.info("savingsGoal={} reverted COMPLETED→ACTIVE (total dropped below target)", goalId);
        }
    }

    private void assertValidTransition(GoalStatus from, GoalStatus to) {
        boolean valid = switch (from) {
            case ACTIVE   -> to == GoalStatus.PAUSED
                              || to == GoalStatus.COMPLETED
                              || to == GoalStatus.ABANDONED;
            case PAUSED   -> to == GoalStatus.ACTIVE || to == GoalStatus.ABANDONED;
            case COMPLETED, ABANDONED -> false;
        };
        if (!valid) {
            throw new BusinessConflictException(
                "Illegal status transition: " + from + " → " + to);
        }
    }

    private String buildPayload(Map<String, String> fields) {
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (Exception e) {
            return "{}";
        }
    }
}

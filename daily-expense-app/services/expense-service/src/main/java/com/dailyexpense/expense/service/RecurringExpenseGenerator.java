package com.dailyexpense.expense.service;

import com.dailyexpense.expense.domain.RecurringExpense;
import com.dailyexpense.expense.repository.RecurringExpenseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * T064 — Daily scheduler: generates Expense occurrences from due RecurringExpense templates.
 * Idempotent on the same date (service layer checks existsByRecurringExpenseIdAndExpenseDate).
 * Each template generates in its own transaction — failure of one does not abort the batch.
 * Disabled in tests via scheduling.enabled=false.
 */
@Component
@ConditionalOnProperty(name = "scheduling.enabled", matchIfMissing = true)
public class RecurringExpenseGenerator {

    private static final Logger log = LoggerFactory.getLogger(RecurringExpenseGenerator.class);

    private final RecurringExpenseRepository recurringExpenseRepository;
    private final RecurringExpenseService recurringExpenseService;

    public RecurringExpenseGenerator(RecurringExpenseRepository recurringExpenseRepository,
                                     RecurringExpenseService recurringExpenseService) {
        this.recurringExpenseRepository = recurringExpenseRepository;
        this.recurringExpenseService = recurringExpenseService;
    }

    @Scheduled(cron = "0 5 0 * * *")
    public void runDailyGeneration() {
        generateDue(LocalDate.now());
    }

    /** Called from tests with a specific reference date for deterministic generation. */
    public void generateDue(LocalDate referenceDate) {
        List<RecurringExpense> due = recurringExpenseRepository.findDueTemplates(referenceDate);
        log.info("RecurringExpenseGenerator: {} templates due on {}", due.size(), referenceDate);
        for (RecurringExpense template : due) {
            try {
                recurringExpenseService.generateOccurrence(template.getId(), referenceDate);
            } catch (Exception e) {
                log.error("Generation failed for template={} date={}: {}",
                    template.getId(), referenceDate, e.getMessage(), e);
                try {
                    recurringExpenseService.publishGenerationFailedEvent(
                        template.getId(), e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(),
                        referenceDate);
                } catch (Exception pub) {
                    log.error("Failed to publish RecurringGenerationFailedEvent for template={}",
                        template.getId(), pub);
                }
            }
        }
    }
}

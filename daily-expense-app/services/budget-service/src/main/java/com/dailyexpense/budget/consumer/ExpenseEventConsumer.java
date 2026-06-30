package com.dailyexpense.budget.consumer;

import com.dailyexpense.budget.domain.Budget;
import com.dailyexpense.budget.domain.BudgetPeriodLedger;
import com.dailyexpense.budget.domain.BudgetScope;
import com.dailyexpense.budget.repository.BudgetPeriodLedgerRepository;
import com.dailyexpense.budget.repository.BudgetRepository;
import com.dailyexpense.budget.service.BudgetEvaluationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * T089 — Spending feed consumer for budget-service.
 * BUD-INV-4: NEVER reads expense_db or category_db SQL — all data arrives via events.
 * Idempotency: ProcessedEventGuard (insert-before-process, DuplicateKeyException → skip).
 * Matching: CATEGORY scope → userId + categoryId; OVERALL scope → userId only.
 */
@Component
public class ExpenseEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ExpenseEventConsumer.class);

    private final ProcessedEventGuard processedEventGuard;
    private final BudgetRepository budgetRepository;
    private final BudgetPeriodLedgerRepository ledgerRepository;
    private final BudgetEvaluationService evaluationService;
    private final ObjectMapper objectMapper;

    public ExpenseEventConsumer(ProcessedEventGuard processedEventGuard,
                                 BudgetRepository budgetRepository,
                                 BudgetPeriodLedgerRepository ledgerRepository,
                                 BudgetEvaluationService evaluationService,
                                 ObjectMapper objectMapper) {
        this.processedEventGuard = processedEventGuard;
        this.budgetRepository = budgetRepository;
        this.ledgerRepository = ledgerRepository;
        this.evaluationService = evaluationService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${kafka.topics.expenses:expenses}", groupId = "${spring.kafka.consumer.group-id:budget-service}")
    @Transactional
    public void onExpenseEvent(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventType = root.path("eventType").asText();

            if (!"ExpenseCreatedEvent".equals(eventType)) {
                return;
            }

            UUID eventId = UUID.fromString(root.path("eventId").asText());
            if (!processedEventGuard.markAndCheck(eventId, eventType)) {
                log.debug("Duplicate expense event skipped: eventId={}", eventId);
                return;
            }

            JsonNode payload = objectMapper.readTree(root.path("payload").asText());
            UUID userId = UUID.fromString(payload.path("userId").asText());
            UUID categoryId = payload.has("categoryId") && !payload.path("categoryId").isNull()
                ? UUID.fromString(payload.path("categoryId").asText()) : null;
            BigDecimal amount = new BigDecimal(payload.path("amount").asText());
            LocalDate expenseDate = LocalDate.parse(payload.path("date").asText());

            applyToMatchingBudgets(userId, categoryId, amount, expenseDate);

        } catch (Exception e) {
            log.error("Failed to process expense event: {}", e.getMessage(), e);
        }
    }

    private void applyToMatchingBudgets(UUID userId, UUID categoryId,
                                         BigDecimal amount, LocalDate expenseDate) {
        List<Budget> budgets = budgetRepository.findByUserIdAndActive(userId, true,
            PageRequest.of(0, 1000)).getContent();

        Instant now = Instant.now();
        for (Budget budget : budgets) {
            boolean matches = switch (budget.getScope()) {
                case CATEGORY -> categoryId != null && categoryId.equals(budget.getCategoryId());
                case OVERALL  -> true;
            };
            if (!matches) continue;

            Optional<BudgetPeriodLedger> ledgerOpt = ledgerRepository.findActiveLedger(
                budget.getId(), expenseDate);
            if (ledgerOpt.isEmpty()) {
                log.debug("No active ledger for budgetId={} on date={}", budget.getId(), expenseDate);
                continue;
            }

            BudgetPeriodLedger ledger = ledgerOpt.get();
            int updated = ledgerRepository.addSpent(ledger.getId(), amount, now);
            if (updated == 0) {
                log.warn("addSpent had no effect: ledgerId={}", ledger.getId());
                continue;
            }
            // Reload after update so evaluate sees current values
            ledger = ledgerRepository.findById(ledger.getId()).orElse(ledger);

            evaluationService.evaluate(ledger, budget, userId);
        }
    }
}

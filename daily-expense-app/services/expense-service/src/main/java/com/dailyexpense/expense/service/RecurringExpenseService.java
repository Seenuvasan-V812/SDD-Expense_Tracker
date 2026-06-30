package com.dailyexpense.expense.service;

import com.dailyexpense.expense.domain.Expense;
import com.dailyexpense.expense.domain.RecurringExpense;
import com.dailyexpense.expense.domain.RecurringFrequency;
import com.dailyexpense.expense.dto.CreateRecurringExpenseRequest;
import com.dailyexpense.expense.dto.RecurringExpenseResponse;
import com.dailyexpense.expense.port.CategoryLookupPort;
import com.dailyexpense.expense.repository.ExpenseRepository;
import com.dailyexpense.expense.repository.RecurringExpenseRepository;
import com.dailyexpense.shared.exception.ForbiddenOwnershipException;
import com.dailyexpense.shared.money.MoneyDto;
import com.dailyexpense.shared.outbox.EventEnvelope;
import com.dailyexpense.shared.outbox.OutboxPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.UUID;

/**
 * T063/T064 — RecurringExpense CRUD + occurrence generation.
 * REC-INV-2: THIS_AND_FUTURE sets old template end_date = occurrence.date − 1 day, creates new template.
 */
@Service
public class RecurringExpenseService {

    private static final Logger log = LoggerFactory.getLogger(RecurringExpenseService.class);

    private final RecurringExpenseRepository recurringExpenseRepository;
    private final ExpenseRepository expenseRepository;
    private final CategoryLookupPort categoryLookupPort;
    private final OutboxPublisher outboxPublisher;

    public RecurringExpenseService(RecurringExpenseRepository recurringExpenseRepository,
                                   ExpenseRepository expenseRepository,
                                   CategoryLookupPort categoryLookupPort,
                                   OutboxPublisher outboxPublisher) {
        this.recurringExpenseRepository = recurringExpenseRepository;
        this.expenseRepository = expenseRepository;
        this.categoryLookupPort = categoryLookupPort;
        this.outboxPublisher = outboxPublisher;
    }

    // ── T063: Create template ───────────────────────────────────────────────────

    @Transactional
    public RecurringExpenseResponse create(UUID userId, CreateRecurringExpenseRequest request) {
        validateAmount(request.amount().amount());
        categoryLookupPort.validate(request.categoryId(), userId, "EXPENSE");

        RecurringExpense template = new RecurringExpense();
        template.setId(UUID.randomUUID());
        template.setUserId(userId);
        template.setAmount(request.amount().amount());
        template.setCurrency(request.amount().currency());
        template.setCategoryId(request.categoryId());
        template.setPaymentMethod(request.paymentMethod());
        template.setFrequency(request.frequency());
        template.setAnchorDate(request.anchorDate());
        template.setEndDate(request.endDate());
        template.setMaxOccurrences(request.maxOccurrences());
        template.setDescription(request.description());
        template.setMerchant(request.merchant());
        template.setNotes(request.notes());
        template.setTagIds(request.tagIds());
        template.setNextRunDate(request.anchorDate());
        template.setCreatedAt(Instant.now());
        template.setUpdatedAt(Instant.now());

        RecurringExpense saved = recurringExpenseRepository.save(template);
        log.info("RecurringExpense template created id={} userId={}", saved.getId(), userId);
        return toResponse(saved);
    }

    // ── T063: Get ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RecurringExpenseResponse getOne(UUID userId, UUID templateId) {
        RecurringExpense t = findOrForbid(templateId, userId);
        return toResponse(t);
    }

    // ── T063: Edit scope=THIS ───────────────────────────────────────────────────

    @Transactional
    public void editThisOccurrence(UUID userId, UUID expenseId,
                                   CreateRecurringExpenseRequest request) {
        Expense expense = expenseRepository.findById(expenseId)
            .orElseThrow(ForbiddenOwnershipException::new);
        if (!expense.getUserId().equals(userId)) throw new ForbiddenOwnershipException();
        if (expense.getRecurringExpenseId() == null) {
            throw new IllegalArgumentException("Expense is not a recurring occurrence");
        }

        categoryLookupPort.validate(request.categoryId(), userId, "EXPENSE");
        expense.setAmount(request.amount().amount());
        expense.setCategoryId(request.categoryId());
        expense.setPaymentMethod(request.paymentMethod());
        expense.setDescription(request.description());
        expense.setMerchant(request.merchant());
        expense.setNotes(request.notes());
        expense.setTagIds(request.tagIds());
        expense.setUpdatedAt(Instant.now());
        expenseRepository.save(expense);
        log.info("RecurringExpense occurrence updated (THIS) expenseId={}", expenseId);
    }

    // ── T063: Edit scope=THIS_AND_FUTURE (REC-INV-2) ──────────────────────────

    @Transactional
    public void editThisAndFuture(UUID userId, UUID expenseId,
                                  CreateRecurringExpenseRequest request) {
        Expense expense = expenseRepository.findById(expenseId)
            .orElseThrow(ForbiddenOwnershipException::new);
        if (!expense.getUserId().equals(userId)) throw new ForbiddenOwnershipException();
        if (expense.getRecurringExpenseId() == null) {
            throw new IllegalArgumentException("Expense is not a recurring occurrence");
        }

        categoryLookupPort.validate(request.categoryId(), userId, "EXPENSE");

        // Truncate old template: end_date = occurrence.date − 1 day
        RecurringExpense oldTemplate = findOrForbid(expense.getRecurringExpenseId(), userId);
        oldTemplate.setEndDate(expense.getExpenseDate().minusDays(1));
        oldTemplate.setUpdatedAt(Instant.now());
        recurringExpenseRepository.save(oldTemplate);

        // Create new forward template starting from this occurrence's date
        RecurringExpense newTemplate = new RecurringExpense();
        newTemplate.setId(UUID.randomUUID());
        newTemplate.setUserId(userId);
        newTemplate.setAmount(request.amount().amount());
        newTemplate.setCurrency(request.amount().currency());
        newTemplate.setCategoryId(request.categoryId());
        newTemplate.setPaymentMethod(request.paymentMethod());
        newTemplate.setFrequency(request.frequency());
        newTemplate.setAnchorDate(expense.getExpenseDate());
        newTemplate.setEndDate(request.endDate());
        newTemplate.setMaxOccurrences(request.maxOccurrences());
        newTemplate.setDescription(request.description());
        newTemplate.setMerchant(request.merchant());
        newTemplate.setNotes(request.notes());
        newTemplate.setTagIds(request.tagIds());
        newTemplate.setNextRunDate(expense.getExpenseDate());
        newTemplate.setCreatedAt(Instant.now());
        newTemplate.setUpdatedAt(Instant.now());
        RecurringExpense saved = recurringExpenseRepository.save(newTemplate);

        log.info("RecurringExpense split THIS_AND_FUTURE oldTemplate={} newTemplate={} userId={}",
            oldTemplate.getId(), saved.getId(), userId);
    }

    // ── T064: Generate occurrence (called by RecurringExpenseGenerator) ─────────

    @Transactional
    public void generateOccurrence(UUID templateId, LocalDate targetDate) {
        RecurringExpense template = recurringExpenseRepository.findById(templateId)
            .orElseThrow(() -> new IllegalStateException("Template not found: " + templateId));

        // Idempotency: skip if already generated for this date
        if (expenseRepository.existsByRecurringExpenseIdAndExpenseDate(templateId, targetDate)) {
            log.debug("Idempotent skip: already generated for template={} date={}", templateId, targetDate);
            return;
        }

        Expense occurrence = new Expense();
        occurrence.setId(UUID.randomUUID());
        occurrence.setUserId(template.getUserId());
        occurrence.setAmount(template.getAmount());
        occurrence.setCurrency(template.getCurrency());
        occurrence.setExpenseDate(targetDate);
        occurrence.setCategoryId(template.getCategoryId());
        occurrence.setPaymentMethod(template.getPaymentMethod());
        occurrence.setDescription(template.getDescription());
        occurrence.setMerchant(template.getMerchant());
        occurrence.setNotes(template.getNotes());
        occurrence.setRecurringExpenseId(templateId);
        occurrence.setTagIds(new HashSet<>(template.getTagIds()));
        occurrence.setCreatedAt(Instant.now());
        occurrence.setUpdatedAt(Instant.now());
        expenseRepository.save(occurrence);

        outboxPublisher.publish(EventEnvelope.builder()
            .eventType("RecurringOccurrenceGenerated")
            .userId(template.getUserId())
            .producer("expense-service")
            .payload("{\"expenseId\":\"" + occurrence.getId()
                + "\",\"templateId\":\"" + templateId + "\"}")
            .build());

        template.setNextRunDate(nextRunDate(targetDate, template.getFrequency()));
        template.setGeneratedCount(template.getGeneratedCount() + 1);
        template.setUpdatedAt(Instant.now());
        recurringExpenseRepository.save(template);

        log.info("Generated occurrence expenseId={} templateId={} date={}",
            occurrence.getId(), templateId, targetDate);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishGenerationFailedEvent(UUID templateId, String errorMsg, LocalDate targetDate) {
        RecurringExpense template = recurringExpenseRepository.findById(templateId).orElse(null);
        UUID userId = template != null ? template.getUserId() : UUID.fromString("00000000-0000-0000-0000-000000000000");
        outboxPublisher.publish(EventEnvelope.builder()
            .eventType("RecurringGenerationFailedEvent")
            .userId(userId)
            .producer("expense-service")
            .payload("{\"templateId\":\"" + templateId
                + "\",\"targetDate\":\"" + targetDate
                + "\",\"error\":\"" + errorMsg.replace("\"", "'") + "\"}")
            .build());
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private RecurringExpense findOrForbid(UUID templateId, UUID userId) {
        return recurringExpenseRepository.findByIdAndUserId(templateId, userId)
            .orElseThrow(ForbiddenOwnershipException::new);
    }

    private void validateAmount(java.math.BigDecimal amount) {
        if (amount == null || amount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be greater than 0");
        }
    }

    private LocalDate nextRunDate(LocalDate current, RecurringFrequency freq) {
        return switch (freq) {
            case DAILY -> current.plusDays(1);
            case WEEKLY -> current.plusWeeks(1);
            case MONTHLY -> current.plusMonths(1);
            case YEARLY -> current.plusYears(1);
        };
    }

    public RecurringExpenseResponse toResponse(RecurringExpense r) {
        return new RecurringExpenseResponse(
            r.getId(), MoneyDto.ofInr(r.getAmount()), r.getCategoryId(),
            r.getPaymentMethod().name(), r.getFrequency().name(),
            r.getAnchorDate(), r.getEndDate(), r.getMaxOccurrences(),
            r.getGeneratedCount(), r.getNextRunDate(),
            r.getDescription(), r.getMerchant(), r.getNotes(),
            r.getTagIds(), r.getCreatedAt(), r.getUpdatedAt()
        );
    }
}

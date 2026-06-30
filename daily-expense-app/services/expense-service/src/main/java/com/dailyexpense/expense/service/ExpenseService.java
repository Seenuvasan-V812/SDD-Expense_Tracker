package com.dailyexpense.expense.service;

import com.dailyexpense.expense.domain.Expense;
import com.dailyexpense.expense.domain.PaymentMethod;
import com.dailyexpense.expense.dto.CreateExpenseRequest;
import com.dailyexpense.expense.dto.ExpenseResponse;
import com.dailyexpense.expense.dto.UpdateExpenseRequest;
import com.dailyexpense.expense.port.CategoryLookupPort;
import com.dailyexpense.expense.port.CategoryValidationResponse;
import com.dailyexpense.expense.port.ContributionEventsPort;
import com.dailyexpense.expense.port.SpendingFeedPort;
import com.dailyexpense.expense.repository.ExpenseRepository;
import com.dailyexpense.expense.repository.ReceiptRepository;
import com.dailyexpense.shared.api.PageResponse;
import com.dailyexpense.shared.exception.ForbiddenOwnershipException;
import com.dailyexpense.shared.money.MoneyDto;
import com.dailyexpense.shared.outbox.EventEnvelope;
import com.dailyexpense.shared.outbox.OutboxPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * T054/T055/T056/T057 — Core expense CRUD with outbox atomicity.
 * userId always from CallerContext (AL-5); 403-never-404 (INV-1/SEC-3).
 * EXP-INV-5: savingsGoalId present → categoryId must be Savings Category.
 */
@Service
public class ExpenseService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseService.class);

    private final ExpenseRepository expenseRepository;
    private final ReceiptRepository receiptRepository;
    private final CategoryLookupPort categoryLookupPort;
    private final OutboxPublisher outboxPublisher;
    private final ContributionEventsPort contributionEventsPort;
    private final SpendingFeedPort spendingFeedPort;
    private final Counter expensesCreatedCounter;

    public ExpenseService(ExpenseRepository expenseRepository,
                          ReceiptRepository receiptRepository,
                          CategoryLookupPort categoryLookupPort,
                          OutboxPublisher outboxPublisher,
                          ContributionEventsPort contributionEventsPort,
                          SpendingFeedPort spendingFeedPort,
                          MeterRegistry meterRegistry) {
        this.expenseRepository = expenseRepository;
        this.receiptRepository = receiptRepository;
        this.categoryLookupPort = categoryLookupPort;
        this.outboxPublisher = outboxPublisher;
        this.contributionEventsPort = contributionEventsPort;
        this.spendingFeedPort = spendingFeedPort;
        this.expensesCreatedCounter = Counter.builder("expenses.created")
            .description("Total expenses created")
            .register(meterRegistry);
    }

    // ── T054: Create ────────────────────────────────────────────────────────────

    @Transactional
    public Expense create(UUID userId, CreateExpenseRequest request) {
        validateAmount(request.amount().amount());

        CategoryValidationResponse category = categoryLookupPort.validate(
            request.categoryId(), userId, "EXPENSE");

        if (request.savingsGoalId() != null && !category.isSavingsCategory()) {
            throw new IllegalArgumentException(
                "EXP-INV-5: when savingsGoalId is set, categoryId must be the Savings Category");
        }

        Expense expense = new Expense();
        expense.setId(UUID.randomUUID());
        expense.setUserId(userId);
        expense.setAmount(request.amount().amount());
        expense.setCurrency(request.amount().currency());
        expense.setExpenseDate(request.date());
        expense.setCategoryId(request.categoryId());
        expense.setPaymentMethod(request.paymentMethod());
        expense.setDescription(request.description());
        expense.setMerchant(request.merchant());
        expense.setNotes(request.notes());
        expense.setSavingsGoalId(request.savingsGoalId());
        expense.setTagIds(request.tagIds());
        expense.setCreatedAt(Instant.now());
        expense.setUpdatedAt(Instant.now());

        Expense saved = expenseRepository.save(expense);

        outboxPublisher.publish(EventEnvelope.builder()
            .eventType("ExpenseCreated")
            .userId(userId)
            .producer("expense-service")
            .payload(toPayload(saved))
            .build());

        spendingFeedPort.expenseCreated(userId, saved.getId(), saved.getAmount(),
            saved.getCategoryId(), saved.getExpenseDate());

        if (saved.getSavingsGoalId() != null) {
            contributionEventsPort.expenseLinkedToGoal(
                userId, saved.getId(), saved.getSavingsGoalId(), saved.getAmount());
        }

        expensesCreatedCounter.increment();
        log.info("Expense created id={} userId={}", saved.getId(), userId);
        return saved;
    }

    // ── T055: List with filters ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> list(UUID userId, LocalDate from, LocalDate to,
                                              UUID categoryId, PaymentMethod paymentMethod,
                                              UUID savingsGoalId, UUID tagId,
                                              int page, int size, String sortBy, String sortDir) {
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir)
            ? Sort.Direction.ASC : Sort.Direction.DESC;
        String field = "amount".equalsIgnoreCase(sortBy) ? "amount" : "expenseDate";
        PageRequest pageable = PageRequest.of(page, size, Sort.by(direction, field));

        Specification<Expense> spec = buildSpec(userId, from, to, categoryId, paymentMethod,
            savingsGoalId, tagId);
        Page<Expense> results = expenseRepository.findAll(spec, pageable);

        // Batch receipt check — one query for the whole page (avoids N+1)
        Set<UUID> ids = results.getContent().stream().map(Expense::getId).collect(Collectors.toSet());
        Set<UUID> withReceipts = ids.isEmpty()
            ? Set.of() : receiptRepository.findExpenseIdsWithReceiptsIn(ids);

        return PageResponse.ofSpringPage(results.map(e -> toResponse(e, withReceipts.contains(e.getId()))));
    }

    // ── T056: Get single ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ExpenseResponse getOne(UUID userId, UUID expenseId) {
        Expense expense = findOrForbid(expenseId, userId);
        boolean hasReceipt = receiptRepository.existsByExpenseId(expenseId);
        return toResponse(expense, hasReceipt);
    }

    // ── T056: Update ────────────────────────────────────────────────────────────

    @Transactional
    public ExpenseResponse update(UUID userId, UUID expenseId, UpdateExpenseRequest request) {
        validateAmount(request.amount().amount());

        Expense expense = findOrForbid(expenseId, userId);

        CategoryValidationResponse category = categoryLookupPort.validate(
            request.categoryId(), userId, "EXPENSE");

        if (request.savingsGoalId() != null && !category.isSavingsCategory()) {
            throw new IllegalArgumentException(
                "EXP-INV-5: when savingsGoalId is set, categoryId must be the Savings Category");
        }

        BigDecimal oldAmount = expense.getAmount();
        UUID oldGoalId = expense.getSavingsGoalId();

        expense.setAmount(request.amount().amount());
        expense.setCurrency(request.amount().currency());
        expense.setExpenseDate(request.date());
        expense.setCategoryId(request.categoryId());
        expense.setPaymentMethod(request.paymentMethod());
        expense.setDescription(request.description());
        expense.setMerchant(request.merchant());
        expense.setNotes(request.notes());
        expense.setSavingsGoalId(request.savingsGoalId());
        expense.setTagIds(request.tagIds());
        expense.setUpdatedAt(Instant.now());

        Expense saved = expenseRepository.save(expense);

        outboxPublisher.publish(EventEnvelope.builder()
            .eventType("ExpenseUpdated")
            .userId(userId)
            .producer("expense-service")
            .payload(toPayload(saved))
            .build());

        spendingFeedPort.expenseUpdated(userId, saved.getId(), oldAmount, saved.getAmount(),
            saved.getCategoryId(), saved.getExpenseDate());

        handleGoalLinkChanges(userId, saved, oldGoalId, oldAmount);

        log.info("Expense updated id={} userId={}", expenseId, userId);
        boolean hasReceipt = receiptRepository.existsByExpenseId(expenseId);
        return toResponse(saved, hasReceipt);
    }

    // ── T056: Delete ────────────────────────────────────────────────────────────

    @Transactional
    public void delete(UUID userId, UUID expenseId) {
        Expense expense = findOrForbid(expenseId, userId);

        BigDecimal amount = expense.getAmount();
        UUID categoryId = expense.getCategoryId();
        LocalDate date = expense.getExpenseDate();
        UUID goalId = expense.getSavingsGoalId();

        expenseRepository.delete(expense);

        outboxPublisher.publish(EventEnvelope.builder()
            .eventType("ExpenseDeleted")
            .userId(userId)
            .producer("expense-service")
            .payload("{\"expenseId\":\"" + expenseId + "\"}")
            .build());

        spendingFeedPort.expenseDeleted(userId, expenseId, amount, categoryId, date);

        if (goalId != null) {
            contributionEventsPort.expenseUnlinkedFromGoal(userId, expenseId, goalId, amount);
        }

        log.info("Expense deleted id={} userId={}", expenseId, userId);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private Expense findOrForbid(UUID expenseId, UUID userId) {
        Expense expense = expenseRepository.findById(expenseId)
            .orElseThrow(ForbiddenOwnershipException::new); // 403, never 404 (INV-1/SEC-3)
        if (!expense.getUserId().equals(userId)) {
            throw new ForbiddenOwnershipException();
        }
        return expense;
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be greater than 0");
        }
    }

    private void handleGoalLinkChanges(UUID userId, Expense saved, UUID oldGoalId, BigDecimal oldAmount) {
        UUID newGoalId = saved.getSavingsGoalId();
        if (oldGoalId == null && newGoalId != null) {
            contributionEventsPort.expenseLinkedToGoal(userId, saved.getId(), newGoalId, saved.getAmount());
        } else if (oldGoalId != null && newGoalId == null) {
            contributionEventsPort.expenseUnlinkedFromGoal(userId, saved.getId(), oldGoalId, oldAmount);
        } else if (oldGoalId != null && oldGoalId.equals(newGoalId) && !oldAmount.equals(saved.getAmount())) {
            contributionEventsPort.expenseGoalLinkChanged(userId, saved.getId(), newGoalId, oldAmount, saved.getAmount());
        }
    }

    private Specification<Expense> buildSpec(UUID userId, LocalDate from, LocalDate to,
                                             UUID categoryId, PaymentMethod paymentMethod,
                                             UUID savingsGoalId, UUID tagId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("expenseDate"), to));
            if (categoryId != null) predicates.add(cb.equal(root.get("categoryId"), categoryId));
            if (paymentMethod != null) predicates.add(cb.equal(root.get("paymentMethod"), paymentMethod));
            if (savingsGoalId != null) predicates.add(cb.equal(root.get("savingsGoalId"), savingsGoalId));
            if (tagId != null) predicates.add(cb.isMember(tagId, root.<java.util.Set<UUID>>get("tagIds")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    ExpenseResponse toResponse(Expense e, boolean hasReceipt) {
        return new ExpenseResponse(
            e.getId(),
            MoneyDto.ofInr(e.getAmount()),
            e.getExpenseDate(),
            e.getCategoryId(),
            e.getPaymentMethod().name(),
            e.getDescription(),
            e.getMerchant(),
            e.getNotes(),
            e.getTagIds(),
            hasReceipt,
            e.getSavingsGoalId(),
            e.getRecurringExpenseId(),
            e.getCreatedAt(),
            e.getUpdatedAt()
        );
    }

    private String toPayload(Expense e) {
        return "{\"expenseId\":\"" + e.getId()
            + "\",\"userId\":\"" + e.getUserId()
            + "\",\"amount\":\"" + e.getAmount()
            + "\",\"categoryId\":\"" + e.getCategoryId()
            + "\",\"date\":\"" + e.getExpenseDate() + "\"}";
    }
}

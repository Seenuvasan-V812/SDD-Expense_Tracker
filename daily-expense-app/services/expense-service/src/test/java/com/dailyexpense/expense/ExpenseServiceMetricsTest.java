package com.dailyexpense.expense;

import com.dailyexpense.expense.domain.Expense;
import com.dailyexpense.expense.domain.PaymentMethod;
import com.dailyexpense.expense.dto.CreateExpenseRequest;
import com.dailyexpense.expense.port.CategoryLookupPort;
import com.dailyexpense.expense.port.CategoryValidationResponse;
import com.dailyexpense.expense.port.ContributionEventsPort;
import com.dailyexpense.expense.port.SpendingFeedPort;
import com.dailyexpense.expense.repository.ExpenseRepository;
import com.dailyexpense.expense.repository.ReceiptRepository;
import com.dailyexpense.expense.service.ExpenseService;
import com.dailyexpense.shared.money.MoneyDto;
import com.dailyexpense.shared.outbox.OutboxPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * T118 — expenses.created counter increments when an expense is created.
 * 0 PII in tags.
 */
@ExtendWith(MockitoExtension.class)
class ExpenseServiceMetricsTest {

    @Mock
    ExpenseRepository expenseRepository;

    @Mock
    ReceiptRepository receiptRepository;

    @Mock
    CategoryLookupPort categoryLookupPort;

    @Mock
    OutboxPublisher outboxPublisher;

    @Mock
    ContributionEventsPort contributionEventsPort;

    @Mock
    SpendingFeedPort spendingFeedPort;

    SimpleMeterRegistry meterRegistry;
    ExpenseService expenseService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        expenseService = new ExpenseService(
            expenseRepository, receiptRepository, categoryLookupPort,
            outboxPublisher, contributionEventsPort, spendingFeedPort, meterRegistry);
    }

    @Test
    void create_incrementsExpensesCreatedCounter() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        CategoryValidationResponse catResp = new CategoryValidationResponse(categoryId, "Food", "EXPENSE", null);
        when(categoryLookupPort.validate(categoryId, userId, "EXPENSE")).thenReturn(catResp);
        when(expenseRepository.save(any())).thenAnswer(inv -> {
            Expense e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            e.setCreatedAt(Instant.now());
            e.setUpdatedAt(Instant.now());
            return e;
        });

        CreateExpenseRequest req = new CreateExpenseRequest(
            new MoneyDto(new BigDecimal("100.00"), "INR"),
            LocalDate.now(), categoryId, PaymentMethod.UPI,
            null, null, null, null, null);

        double before = meterRegistry.counter("expenses.created").count();
        expenseService.create(userId, req);
        double after = meterRegistry.counter("expenses.created").count();

        assertThat(after).isEqualTo(before + 1);
    }

    @Test
    void counter_hasNoPiiInTags() {
        Counter counter = meterRegistry.counter("expenses.created");
        counter.getId().getTags().forEach(tag -> {
            assertThat(tag.getKey()).doesNotContainIgnoringCase("userId");
            assertThat(tag.getKey()).doesNotContainIgnoringCase("email");
            assertThat(tag.getKey()).doesNotContainIgnoringCase("amount");
        });
    }
}

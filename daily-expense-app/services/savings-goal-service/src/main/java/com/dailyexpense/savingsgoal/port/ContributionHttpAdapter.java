package com.dailyexpense.savingsgoal.port;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * T073 — HTTP adapter: calls expense-service POST /api/v1/expenses to create a backing Expense
 * under the Savings Category. No expense_db SQL — pure HTTP (AL-1).
 */
public class ContributionHttpAdapter implements ContributionPort {

    private static final Logger log = LoggerFactory.getLogger(ContributionHttpAdapter.class);

    private final RestTemplate restTemplate;
    private final String expenseServiceBaseUrl;
    private final String savingsCategoryId;

    public ContributionHttpAdapter(RestTemplateBuilder builder,
                                   String expenseServiceBaseUrl,
                                   String savingsCategoryId) {
        this.restTemplate = builder.build();
        this.expenseServiceBaseUrl = expenseServiceBaseUrl;
        this.savingsCategoryId = savingsCategoryId;
    }

    @Override
    public UUID createBackingExpense(UUID userId, BigDecimal amount, LocalDate date,
                                     UUID savingsGoalId, String bearerToken) {
        String body = """
            {
              "amount": {"amount": "%s", "currency": "INR"},
              "date": "%s",
              "categoryId": "%s",
              "paymentMethod": "UPI",
              "savingsGoalId": "%s"
            }
            """.formatted(amount.toPlainString(), date, savingsCategoryId, savingsGoalId);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, bearerToken);
        headers.set(HttpHeaders.CONTENT_TYPE, "application/json");

        ResponseEntity<Void> response = restTemplate.exchange(
            expenseServiceBaseUrl + "/api/v1/expenses",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            Void.class
        );

        String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
        if (location == null) {
            throw new IllegalStateException("expense-service did not return a Location header");
        }
        String expenseId = location.substring(location.lastIndexOf('/') + 1);
        log.info("Created backing expense={} for goal={} user={}", expenseId, savingsGoalId, userId);
        return UUID.fromString(expenseId);
    }
}

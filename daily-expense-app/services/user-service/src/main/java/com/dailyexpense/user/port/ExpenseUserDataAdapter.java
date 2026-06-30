package com.dailyexpense.user.port;

import com.dailyexpense.user.dto.UserExportSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * T112/T115 — Fetches expense data from expense-service's internal endpoint (streamed).
 * No expense_db SQL (AL-1).
 */
@Component
public class ExpenseUserDataAdapter implements UserDataPort {

    private static final Logger log = LoggerFactory.getLogger(ExpenseUserDataAdapter.class);

    private final RestTemplate internalRestTemplate;
    private final String expenseServiceUrl;

    public ExpenseUserDataAdapter(
            RestTemplate internalRestTemplate,
            @Value("${internal.services.expense-service.url:http://localhost:8083}") String expenseServiceUrl) {
        this.internalRestTemplate = internalRestTemplate;
        this.expenseServiceUrl = expenseServiceUrl;
    }

    @Override
    public UserExportSegment exportUserData(UUID userId) {
        try {
            String url = expenseServiceUrl + "/internal/users/" + userId + "/export-data";
            String json = internalRestTemplate.getForObject(url, String.class);
            return new UserExportSegment("expenses", json != null ? json : "{\"expenses\":[]}");
        } catch (Exception e) {
            log.warn("expense-service export unavailable; returning empty segment: {}", e.getMessage());
            return new UserExportSegment("expenses", "{\"expenses\":[]}");
        }
    }
}

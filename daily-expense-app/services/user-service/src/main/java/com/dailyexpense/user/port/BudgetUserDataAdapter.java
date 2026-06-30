package com.dailyexpense.user.port;

import com.dailyexpense.user.dto.UserExportSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * T112/T117 — Fetches budget data from budget-service's internal endpoint.
 * No budget_db SQL (AL-1).
 */
@Component
public class BudgetUserDataAdapter implements UserDataPort {

    private static final Logger log = LoggerFactory.getLogger(BudgetUserDataAdapter.class);

    private final RestTemplate internalRestTemplate;
    private final String budgetServiceUrl;

    public BudgetUserDataAdapter(
            RestTemplate internalRestTemplate,
            @Value("${internal.services.budget-service.url:http://localhost:8085}") String budgetServiceUrl) {
        this.internalRestTemplate = internalRestTemplate;
        this.budgetServiceUrl = budgetServiceUrl;
    }

    @Override
    public UserExportSegment exportUserData(UUID userId) {
        try {
            String url = budgetServiceUrl + "/internal/users/" + userId + "/export-data";
            String json = internalRestTemplate.getForObject(url, String.class);
            return new UserExportSegment("budgets", json != null ? json : "{\"budgets\":[]}");
        } catch (Exception e) {
            log.warn("budget-service export unavailable; returning empty segment: {}", e.getMessage());
            return new UserExportSegment("budgets", "{\"budgets\":[]}");
        }
    }
}

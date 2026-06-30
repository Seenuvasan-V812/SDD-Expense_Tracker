package com.dailyexpense.user.port;

import com.dailyexpense.user.dto.UserExportSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * T112/T116 — Fetches savings-goal data from savings-goal-service's internal endpoint.
 * No savings_goal_db SQL (AL-1).
 */
@Component
public class SavingsGoalUserDataAdapter implements UserDataPort {

    private static final Logger log = LoggerFactory.getLogger(SavingsGoalUserDataAdapter.class);

    private final RestTemplate internalRestTemplate;
    private final String savingsGoalServiceUrl;

    public SavingsGoalUserDataAdapter(
            RestTemplate internalRestTemplate,
            @Value("${internal.services.savings-goal-service.url:http://localhost:8084}") String savingsGoalServiceUrl) {
        this.internalRestTemplate = internalRestTemplate;
        this.savingsGoalServiceUrl = savingsGoalServiceUrl;
    }

    @Override
    public UserExportSegment exportUserData(UUID userId) {
        try {
            String url = savingsGoalServiceUrl + "/internal/users/" + userId + "/export-data";
            String json = internalRestTemplate.getForObject(url, String.class);
            return new UserExportSegment("savingsGoals", json != null ? json : "{\"savingsGoals\":[]}");
        } catch (Exception e) {
            log.warn("savings-goal-service export unavailable; returning empty segment: {}", e.getMessage());
            return new UserExportSegment("savingsGoals", "{\"savingsGoals\":[]}");
        }
    }
}

package com.dailyexpense.user.port;

import com.dailyexpense.user.dto.UserExportSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * T112/T114 — Fetches category data for a user from category-service's internal endpoint.
 * No category_db SQL (AL-1); HTTP only via internalRestTemplate.
 */
@Component
public class CategoryUserDataAdapter implements UserDataPort {

    private static final Logger log = LoggerFactory.getLogger(CategoryUserDataAdapter.class);

    private final RestTemplate internalRestTemplate;
    private final String categoryServiceUrl;

    public CategoryUserDataAdapter(
            RestTemplate internalRestTemplate,
            @Value("${internal.services.category-service.url:http://localhost:8082}") String categoryServiceUrl) {
        this.internalRestTemplate = internalRestTemplate;
        this.categoryServiceUrl = categoryServiceUrl;
    }

    @Override
    public UserExportSegment exportUserData(UUID userId) {
        try {
            String url = categoryServiceUrl + "/internal/users/" + userId + "/export-data";
            String json = internalRestTemplate.getForObject(url, String.class);
            return new UserExportSegment("categories", json != null ? json : "{\"categories\":[]}");
        } catch (Exception e) {
            log.warn("category-service export unavailable; returning empty segment: {}", e.getMessage());
            return new UserExportSegment("categories", "{\"categories\":[]}");
        }
    }
}

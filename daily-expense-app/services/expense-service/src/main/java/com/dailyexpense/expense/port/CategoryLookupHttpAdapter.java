package com.dailyexpense.expense.port;

import com.dailyexpense.shared.exception.ForbiddenOwnershipException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * T067 — Calls category-service /internal/categories/{id}/validate.
 * Not a @Component — registered via CategoryLookupConfig when category-service.base-url is set.
 * No category_db SQL (AL-1).
 */
public class CategoryLookupHttpAdapter implements CategoryLookupPort {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public CategoryLookupHttpAdapter(RestTemplate restTemplate, String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    @Override
    public CategoryValidationResponse validate(UUID categoryId, UUID userId, String requiredType) {
        String url = baseUrl + "/internal/categories/" + categoryId
            + "/validate?userId=" + userId + "&requiredType=" + requiredType;
        try {
            return restTemplate.getForObject(url, CategoryValidationResponse.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND
                || e.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new ForbiddenOwnershipException();
            }
            throw e;
        }
    }
}

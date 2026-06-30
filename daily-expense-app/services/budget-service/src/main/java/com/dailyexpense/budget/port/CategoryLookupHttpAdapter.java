package com.dailyexpense.budget.port;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/** HTTP adapter — calls category-service GET /api/v1/categories/{id} to verify existence. */
public class CategoryLookupHttpAdapter implements CategoryLookupPort {

    private static final Logger log = LoggerFactory.getLogger(CategoryLookupHttpAdapter.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public CategoryLookupHttpAdapter(RestTemplate restTemplate, String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    @Override
    public boolean exists(UUID categoryId, String bearerToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (bearerToken != null && !bearerToken.isBlank()) {
                headers.set("Authorization", bearerToken);
            }
            ResponseEntity<Void> response = restTemplate.exchange(
                baseUrl + "/api/v1/categories/" + categoryId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Void.class
            );
            return response.getStatusCode() == HttpStatus.OK;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (Exception e) {
            log.warn("CategoryLookupHttpAdapter: failed to verify categoryId={}: {}", categoryId, e.getMessage());
            return false;
        }
    }
}

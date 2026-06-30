package com.dailyexpense.expense;

import com.dailyexpense.expense.port.CategoryLookupHttpAdapter;
import com.dailyexpense.expense.port.CategoryValidationResponse;
import com.dailyexpense.shared.exception.ForbiddenOwnershipException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * T067 gate — CategoryLookupHttpAdapterTest.
 * Proves: HTTP call to category-service /internal/categories/{id}/validate;
 * not-found → ForbiddenOwnershipException; forbidden → ForbiddenOwnershipException.
 * No category_db SQL (AL-1) — adapter purely uses HTTP.
 * Uses MockRestServiceServer (no Spring Boot context required).
 */
class CategoryLookupHttpAdapterTest {

    private static final String BASE_URL = "http://category-service";

    RestTemplate restTemplate;
    MockRestServiceServer mockServer;
    CategoryLookupHttpAdapter adapter;

    @BeforeEach
    void setup() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        adapter = new CategoryLookupHttpAdapter(restTemplate, BASE_URL);
    }

    @Test
    void validate_categoryFound_returnsResponse() {
        UUID catId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String json = """
            {"categoryId":"%s","name":"Groceries","type":"EXPENSE","systemRole":"NONE"}
            """.formatted(catId);

        mockServer.expect(requestTo(
                BASE_URL + "/internal/categories/" + catId + "/validate?userId=" + userId + "&requiredType=EXPENSE"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        CategoryValidationResponse result = adapter.validate(catId, userId, "EXPENSE");

        assertThat(result.categoryId()).isEqualTo(catId);
        assertThat(result.name()).isEqualTo("Groceries");
        assertThat(result.type()).isEqualTo("EXPENSE");
        assertThat(result.systemRole()).isEqualTo("NONE");
        mockServer.verify();
    }

    @Test
    void validate_categoryNotFound_throwsForbiddenOwnershipException() {
        UUID catId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockServer.expect(requestTo(
                BASE_URL + "/internal/categories/" + catId + "/validate?userId=" + userId + "&requiredType=EXPENSE"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> adapter.validate(catId, userId, "EXPENSE"))
            .isInstanceOf(ForbiddenOwnershipException.class);
        mockServer.verify();
    }

    @Test
    void validate_categoryForbidden_throwsForbiddenOwnershipException() {
        UUID catId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockServer.expect(requestTo(
                BASE_URL + "/internal/categories/" + catId + "/validate?userId=" + userId + "&requiredType=EXPENSE"))
            .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> adapter.validate(catId, userId, "EXPENSE"))
            .isInstanceOf(ForbiddenOwnershipException.class);
        mockServer.verify();
    }

    @Test
    void validate_savingsCategory_returnsSavingsSystemRole() {
        UUID catId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String json = """
            {"categoryId":"%s","name":"Savings","type":"BOTH","systemRole":"SAVINGS"}
            """.formatted(catId);

        mockServer.expect(requestTo(
                BASE_URL + "/internal/categories/" + catId + "/validate?userId=" + userId + "&requiredType=EXPENSE"))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        CategoryValidationResponse result = adapter.validate(catId, userId, "EXPENSE");
        assertThat(result.isSavingsCategory()).isTrue();
        assertThat(result.isExpenseCompatible()).isTrue();
    }
}

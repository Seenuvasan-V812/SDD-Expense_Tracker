package com.dailyexpense.expense.config;

import com.dailyexpense.expense.port.CategoryLookupHttpAdapter;
import com.dailyexpense.expense.port.CategoryLookupPort;
import com.dailyexpense.expense.port.CategoryLookupPortStub;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * T067 — Wires CategoryLookupPort conditionally.
 * HTTP adapter used when category-service.base-url is set (production).
 * Stub falls through when the property is absent (local / test).
 */
@Configuration
public class CategoryLookupConfig {

    @Bean
    public RestTemplate categoryRestTemplate() {
        return new RestTemplate();
    }

    @Bean
    @ConditionalOnProperty(name = "category-service.base-url")
    public CategoryLookupPort categoryLookupHttpAdapter(
            RestTemplate categoryRestTemplate,
            @Value("${category-service.base-url}") String baseUrl) {
        return new CategoryLookupHttpAdapter(categoryRestTemplate, baseUrl);
    }

    @Bean
    @ConditionalOnMissingBean(CategoryLookupPort.class)
    public CategoryLookupPort categoryLookupPortStub() {
        return new CategoryLookupPortStub();
    }
}

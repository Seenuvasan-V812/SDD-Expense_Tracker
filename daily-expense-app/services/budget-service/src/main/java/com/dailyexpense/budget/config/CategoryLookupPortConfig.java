package com.dailyexpense.budget.config;

import com.dailyexpense.budget.port.CategoryLookupHttpAdapter;
import com.dailyexpense.budget.port.CategoryLookupPort;
import com.dailyexpense.budget.port.CategoryLookupPortStub;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class CategoryLookupPortConfig {

    @Bean
    public CategoryLookupPort categoryLookupPort(
            @Value("${category.service.base-url:}") String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return new CategoryLookupPortStub();
        }
        return new CategoryLookupHttpAdapter(new RestTemplate(), baseUrl);
    }
}

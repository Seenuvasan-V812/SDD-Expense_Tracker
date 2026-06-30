package com.dailyexpense.savingsgoal.config;

import com.dailyexpense.savingsgoal.port.ContributionHttpAdapter;
import com.dailyexpense.savingsgoal.port.ContributionPort;
import com.dailyexpense.savingsgoal.port.ContributionPortStub;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ContributionPortConfig {

    @Bean
    public ContributionPort contributionPort(
            RestTemplateBuilder builder,
            @Value("${contribution.expense-service.base-url:}") String baseUrl,
            @Value("${contribution.savings-category-id:}") String savingsCategoryId) {

        if (baseUrl == null || baseUrl.isBlank()) {
            return new ContributionPortStub();
        }
        return new ContributionHttpAdapter(builder, baseUrl, savingsCategoryId);
    }
}

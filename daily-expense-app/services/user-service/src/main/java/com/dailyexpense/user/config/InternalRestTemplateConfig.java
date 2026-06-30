package com.dailyexpense.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * T112 — RestTemplate for internal service-to-service HTTP calls.
 * Separate from any public-facing HTTP client; no auth headers attached.
 */
@Configuration
public class InternalRestTemplateConfig {

    @Bean
    public RestTemplate internalRestTemplate() {
        return new RestTemplate();
    }
}

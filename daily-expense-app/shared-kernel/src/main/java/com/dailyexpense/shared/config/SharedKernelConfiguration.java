package com.dailyexpense.shared.config;

import com.dailyexpense.shared.exception.GlobalExceptionHandler;
import com.dailyexpense.shared.observability.PiiMasker;
import com.dailyexpense.shared.observability.RequestLoggingFilter;
import com.dailyexpense.shared.observability.TraceIdFilter;
import com.dailyexpense.shared.security.JwtAuthenticationFilter;
import com.dailyexpense.shared.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Auto-configuration for shared-kernel beans.
 * Services inherit these beans automatically via spring.factories / AutoConfiguration.imports.
 * JWT_SECRET is injected from the environment (SEC-6) — never hardcoded.
 */
@AutoConfiguration
public class SharedKernelConfiguration {

    @Bean
    public JwtService jwtService(
            @Value("${jwt.secret:#{systemEnvironment['JWT_SECRET']}}") String secret) {
        return new JwtService(secret);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService) {
        return new JwtAuthenticationFilter(jwtService);
    }

    @Bean
    public PiiMasker piiMasker() {
        return new PiiMasker();
    }

    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilter() {
        FilterRegistrationBean<TraceIdFilter> reg = new FilterRegistrationBean<>(new TraceIdFilter());
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE); // TraceId must be set before any logging
        return reg;
    }

    @Bean
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter() {
        FilterRegistrationBean<RequestLoggingFilter> reg = new FilterRegistrationBean<>(new RequestLoggingFilter());
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 1); // after TraceIdFilter
        return reg;
    }

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}

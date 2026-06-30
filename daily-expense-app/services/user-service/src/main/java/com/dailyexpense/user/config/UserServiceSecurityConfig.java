package com.dailyexpense.user.config;

import com.dailyexpense.shared.security.JwtAuthenticationFilter;
import com.dailyexpense.user.security.AuthRateLimitFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

@Configuration
@EnableWebSecurity
public class UserServiceSecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.rate-limit.login-ip-limit:20}")
    private int loginIpLimit;

    @Value("${app.rate-limit.window-seconds:900}")
    private int windowSecs;

    public UserServiceSecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public AuthRateLimitFilter authRateLimitFilter() {
        return new AuthRateLimitFilter(loginIpLimit, windowSecs);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(authRateLimitFilter(), AbstractPreAuthenticatedProcessingFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.setContentType("application/json");
                            res.getWriter().write(
                                "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                        })
                        .accessDeniedHandler((req, res, e) -> {
                            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            res.setContentType("application/json");
                            res.getWriter().write(
                                "{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied\"}");
                        })
                )
                .build();
    }
}

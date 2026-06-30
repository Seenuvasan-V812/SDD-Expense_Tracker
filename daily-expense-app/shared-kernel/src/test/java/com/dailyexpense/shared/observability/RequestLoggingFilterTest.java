package com.dailyexpense.shared.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED → GREEN: RequestLoggingFilter — logs method/path/status/latency/traceId; no PII (CQ-11/CQ-13).
 */
class RequestLoggingFilterTest {

    private RequestLoggingFilter filter;
    private ListAppender<ILoggingEvent> logCapture;
    private Logger requestLogger;

    @BeforeEach
    void setUp() {
        filter = new RequestLoggingFilter();
        MDC.put("traceId", "test-trace-001");

        requestLogger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        logCapture = new ListAppender<>();
        logCapture.start();
        requestLogger.addAppender(logCapture);
        requestLogger.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        requestLogger.detachAppender(logCapture);
        MDC.clear();
    }

    @Test
    void logLineContainsFiveFields() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/expenses");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        filter.doFilterInternal(request, response, (req, res) -> {});

        List<ILoggingEvent> logs = logCapture.list;
        assertThat(logs).isNotEmpty();
        String logMessage = logs.get(0).getFormattedMessage();
        // Must contain: method, path, status, latency (ms), traceId
        assertThat(logMessage).contains("GET");
        assertThat(logMessage).contains("/api/v1/expenses");
        assertThat(logMessage).contains("200");
        assertThat(logMessage).containsPattern("\\d+ms");
        assertThat(logMessage).contains("test-trace-001");
    }

    @Test
    void logLineContainsNoEmail() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
        request.addParameter("email", "user@example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, res) -> {});

        String allLogs = logCapture.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", String::concat);
        assertThat(allLogs).doesNotContain("user@example.com");
    }

    @Test
    void logLineContainsNoAuthorizationHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.addHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.secret.sig");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, res) -> {});

        String allLogs = logCapture.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", String::concat);
        assertThat(allLogs).doesNotContain("Bearer");
        assertThat(allLogs).doesNotContain("eyJ");
    }

    @Test
    void logLineContainsNoPassword() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setContent("{\"email\":\"x@y.com\",\"password\":\"secret123\"}".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, res) -> {});

        String allLogs = logCapture.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", String::concat);
        assertThat(allLogs).doesNotContain("secret123");
    }

    @Test
    void filterChainIsCalled() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/expenses/abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        final boolean[] chainCalled = {false};

        filter.doFilterInternal(request, response, (req, res) -> chainCalled[0] = true);

        assertThat(chainCalled[0]).isTrue();
    }
}

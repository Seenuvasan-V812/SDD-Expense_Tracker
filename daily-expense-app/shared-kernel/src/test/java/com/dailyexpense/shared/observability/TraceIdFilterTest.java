package com.dailyexpense.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED → GREEN: TraceIdFilter — every request gets a distinct traceId in MDC; cleared after response (CQ-11).
 */
@ExtendWith(MockitoExtension.class)
class TraceIdFilterTest {

    private TraceIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TraceIdFilter();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void everyRequestHasTraceIdInMdc() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        final String[] captured = {null};

        filter.doFilterInternal(request, response, (req, res) -> {
            captured[0] = MDC.get("traceId");
        });

        assertThat(captured[0]).isNotNull().isNotBlank();
    }

    @Test
    void traceIdsAreDistinctAcrossRequests() throws ServletException, IOException {
        List<String> traceIds = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, (req, res) -> {
                traceIds.add(MDC.get("traceId"));
            });
        }

        assertThat(traceIds).hasSize(5);
        assertThat(traceIds.stream().distinct().count()).isEqualTo(5);
    }

    @Test
    void traceIdIsClearedAfterResponse() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, res) -> {});

        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void traceIdDoesNotBleedBetweenRequests() throws ServletException, IOException {
        MockHttpServletRequest r1 = new MockHttpServletRequest();
        MockHttpServletResponse rs1 = new MockHttpServletResponse();
        final String[] first = {null};
        filter.doFilterInternal(r1, rs1, (req, res) -> first[0] = MDC.get("traceId"));

        // After first request, MDC is clear
        assertThat(MDC.get("traceId")).isNull();

        MockHttpServletRequest r2 = new MockHttpServletRequest();
        MockHttpServletResponse rs2 = new MockHttpServletResponse();
        final String[] second = {null};
        filter.doFilterInternal(r2, rs2, (req, res) -> second[0] = MDC.get("traceId"));

        assertThat(first[0]).isNotEqualTo(second[0]);
    }

    @Test
    void incomingTraceIdHeaderIsRespected() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "incoming-trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        final String[] captured = {null};

        filter.doFilterInternal(request, response, (req, res) -> {
            captured[0] = MDC.get("traceId");
        });

        assertThat(captured[0]).isEqualTo("incoming-trace-123");
    }
}

package com.edu360.api.platformops.release;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class RequestMetricFilterTest {

    @Test
    void recordsMappedTemplateWithoutReadingRawRequestPath() throws Exception {
        RequestMetricCollector collector = mock(RequestMetricCollector.class);
        RequestMetricFilter filter = new RequestMetricFilter(collector);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE))
                .thenReturn("/api/platform/releases/{id}/health");
        when(response.getStatus()).thenReturn(503);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(collector).record("/api/platform/releases/{id}/health", 503);
    }
}

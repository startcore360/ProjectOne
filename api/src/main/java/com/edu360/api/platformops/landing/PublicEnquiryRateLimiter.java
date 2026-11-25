package com.edu360.api.platformops.landing;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.edu360.api.exception.ApiException;

/** Simple sliding-window per-client rate limiter for public landing enquiry submissions. */
@Component
public class PublicEnquiryRateLimiter {
    private static final int MAX_REQUESTS_PER_WINDOW = 10;
    private static final long WINDOW_MILLIS = 15 * 60 * 1000L;

    private final Map<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    public void checkAllowed(String clientKey) {
        String key = (clientKey == null || clientKey.isBlank()) ? "unknown" : clientKey.trim();
        long now = Instant.now().toEpochMilli();
        Deque<Long> timestamps = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > WINDOW_MILLIS) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_REQUESTS_PER_WINDOW) {
                throw new ApiException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "RATE_LIMIT_EXCEEDED",
                        "Too many enquiry requests. Please wait a few minutes and try again.");
            }
            timestamps.addLast(now);
        }
    }
}

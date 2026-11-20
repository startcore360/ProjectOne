package com.edu360.api.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import tools.jackson.databind.json.JsonMapper;

/** E8-S5: a retried request with the same Idempotency-Key never runs the action twice. */
class PlatformIdempotentExecutorTest {

    record Result(String id, String state) {}

    private static final UUID CALLER = UUID.randomUUID();

    private PlatformIdempotencyService idempotencyService;
    private JsonMapper mapper;
    private PlatformIdempotentExecutor executor;

    @BeforeEach
    void setUp() {
        idempotencyService = mock(PlatformIdempotencyService.class);
        mapper = JsonMapper.builder().findAndAddModules().build();
        executor = new PlatformIdempotentExecutor(idempotencyService, mapper);
    }

    @Test
    void firstCallRunsTheActionAndStoresTheResponse() {
        UUID claimId = UUID.randomUUID();
        when(idempotencyService.claim(eq(CALLER), eq("createTenant"), eq("k1"), any()))
                .thenReturn(new PlatformIdempotencyService.Claim(claimId, false, null, null));
        Result result = new Result("t1", "TRIAL");

        ResponseEntity<Result> response = executor.execute(CALLER, "createTenant", "k1", Map.of("a", 1),
                HttpStatus.CREATED, Result.class, () -> result);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(result);
        verify(idempotencyService).complete(CALLER, claimId, 201, result);
    }

    @Test
    void replayReturnsTheStoredResponseWithoutRunningTheActionAgain() {
        when(idempotencyService.claim(eq(CALLER), eq("createTenant"), eq("k1"), any()))
                .thenReturn(new PlatformIdempotencyService.Claim(UUID.randomUUID(), true, 201,
                        mapper.valueToTree(new Result("t1", "TRIAL"))));
        AtomicInteger runs = new AtomicInteger();

        ResponseEntity<Result> response = executor.execute(CALLER, "createTenant", "k1", Map.of("a", 1),
                HttpStatus.CREATED, Result.class, () -> {
                    runs.incrementAndGet();
                    return new Result("t2", "TRIAL");
                });

        assertThat(runs.get()).isZero();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(new Result("t1", "TRIAL"));
        verify(idempotencyService, never()).complete(any(), any(), anyInt(), any());
    }

    @Test
    void failedActionReleasesTheClaimSoTheSameKeyCanBeRetried() {
        UUID claimId = UUID.randomUUID();
        when(idempotencyService.claim(eq(CALLER), eq("suspendTenant"), eq("k2"), any()))
                .thenReturn(new PlatformIdempotencyService.Claim(claimId, false, null, null));

        assertThatThrownBy(() -> executor.execute(CALLER, "suspendTenant", "k2", Map.of("a", 1),
                HttpStatus.OK, Result.class, () -> {
                    throw new IllegalStateException("stale");
                })).isInstanceOf(IllegalStateException.class);

        verify(idempotencyService).release(CALLER, claimId);
        verify(idempotencyService, never()).complete(any(), any(), anyInt(), any());
    }
}

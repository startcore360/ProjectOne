package com.edu360.api.idempotency;

import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * E8-S5: runs one platform write under an Idempotency-Key (D5 "IdempotencyKey" operations).
 *
 * WHAT: the first call with a key runs the action and stores its response; a retry with the same key and the
 * same request returns the stored response instead of running the action again; the same key with a
 * different request is refused by PlatformIdempotencyService (409).
 * HOW: a thin wrapper around PlatformIdempotencyService.claim / complete / release, so every controller uses
 * the identical sequence instead of repeating it. If the action throws, the claim is released so the client
 * can retry with the same key.
 * WHY a separate bean: calling claim/complete from inside PlatformIdempotencyService itself would bypass its
 * own @Transactional(REQUIRES_NEW) proxies.
 */
@Component
public class PlatformIdempotentExecutor {

    private final PlatformIdempotencyService idempotencyService;
    private final JsonMapper mapper;

    public PlatformIdempotentExecutor(PlatformIdempotencyService idempotencyService, JsonMapper mapper) {
        this.idempotencyService = idempotencyService;
        this.mapper = mapper;
    }

    public <T> ResponseEntity<T> execute(UUID callerUserId, String scope, String idempotencyKey,
            Object requestFingerprint, HttpStatus successStatus, Class<T> responseType, Supplier<T> action) {
        PlatformIdempotencyService.Claim claim =
                idempotencyService.claim(callerUserId, scope, idempotencyKey, requestFingerprint);
        if (claim.replay()) {
            int status = claim.responseStatus() == null ? successStatus.value() : claim.responseStatus();
            return ResponseEntity.status(status).body(mapper.convertValue(claim.responseBody(), responseType));
        }
        try {
            T result = action.get();
            idempotencyService.complete(callerUserId, claim.id(), successStatus.value(), result);
            return ResponseEntity.status(successStatus).body(result);
        } catch (RuntimeException ex) {
            idempotencyService.release(callerUserId, claim.id());
            throw ex;
        }
    }
}

package com.edu360.api.idempotency;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.exception.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Platform-side Idempotency-Key storage. Uses edu_platform and platform.idempotency_key, never the
 * tenant-scoped app.idempotency_key. OD-39 expiry and cleanup are intentionally not implemented.
 */
@Service
public class PlatformIdempotencyService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    @Autowired
    public PlatformIdempotencyService(
            @Qualifier("platformDataSource") ObjectProvider<DataSource> dataSourceProvider,
            ObjectMapper mapper) {
        this(dataSourceProvider.getIfAvailable(), mapper);
    }

    /** Direct constructor for isolated tests; the test profile has no edu_platform pool. */
    public PlatformIdempotencyService(DataSource dataSource, ObjectMapper mapper) {
        this.jdbc = dataSource == null ? null : new JdbcTemplate(dataSource);
        this.mapper = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public record Claim(UUID id, boolean replay, Integer responseStatus, JsonNode responseBody) {
    }

    @Transactional(transactionManager = "platformTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public Claim claim(UUID callerUserId, String scope, String idempotencyKey, Object requestBody) {
        if (callerUserId == null || scope == null || scope.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_SCOPE_REQUIRED",
                    "An authenticated platform caller and operation scope are required.");
        }
        if (idempotencyKey == null || idempotencyKey.length() < 8 || idempotencyKey.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency-Key must contain 8 to 100 characters.");
        }

        JdbcTemplate jdbc = requireJdbc();
        String requestHash = requestHash(requestBody, mapper);
        UUID proposedId = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO platform.idempotency_key
                        (platform_idempotency_key_id, scope, request_key, caller_user_id, request_hash,
                         status, created_by, updated_by)
                    VALUES (?, ?, ?, ?, ?, 'IN_PROGRESS', ?, ?)
                    ON CONFLICT (scope, caller_user_id, request_key) DO NOTHING
                    """,
                    proposedId, scope, idempotencyKey, callerUserId, requestHash, callerUserId, callerUserId);

            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT platform_idempotency_key_id, request_hash, status, response_status, response_body
                    FROM platform.idempotency_key
                    WHERE scope = ? AND caller_user_id = ? AND request_key = ?
                    FOR UPDATE
                    """, scope, callerUserId, idempotencyKey);
            if (rows.isEmpty()) {
                throw requestInProgress();
            }

            Map<String, Object> row = rows.get(0);
            UUID storedId = UUID.fromString(String.valueOf(row.get("platform_idempotency_key_id")));
            if (!requestHash.equals(String.valueOf(row.get("request_hash")).trim())) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                        "This Idempotency-Key was already used with a different request body.");
            }
            if ("IN_PROGRESS".equals(row.get("status"))) {
                if (proposedId.equals(storedId)) {
                    return new Claim(storedId, false, null, null);
                }
                throw requestInProgress();
            }

            Object storedBody = row.get("response_body");
            JsonNode responseBody = storedBody == null ? null : mapper.readTree(storedBody.toString());
            Number responseStatus = (Number) row.get("response_status");
            return new Claim(storedId, true, responseStatus == null ? null : responseStatus.intValue(), responseBody);
        } catch (ApiException ex) {
            throw ex;
        } catch (DataAccessException | JsonProcessingException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "IDEMPOTENCY_STORE_UNAVAILABLE",
                    "The platform idempotency store is temporarily unavailable.");
        }
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public void complete(UUID callerUserId, UUID claimId, int responseStatus, Object responseBody) {
        if (callerUserId == null || claimId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_CLAIM_REQUIRED",
                    "A claimed platform idempotency key is required.");
        }
        JdbcTemplate jdbc = requireJdbc();
        try {
            String json = mapper.writeValueAsString(responseBody);
            int updated = jdbc.update("""
                    UPDATE platform.idempotency_key
                    SET status = 'COMPLETED', response_status = ?, response_body = CAST(? AS jsonb),
                        completed_at = now(), updated_by = ?
                    WHERE platform_idempotency_key_id = ? AND caller_user_id = ? AND status = 'IN_PROGRESS'
                    """, responseStatus, json, callerUserId, claimId, callerUserId);
            if (updated != 1) {
                throw requestInProgress();
            }
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Response cannot be serialized for idempotent replay.", ex);
        }
    }

    @Transactional(transactionManager = "platformTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void release(UUID callerUserId, UUID claimId) {
        if (callerUserId == null || claimId == null || jdbc == null) {
            return;
        }
        jdbc.update("""
                DELETE FROM platform.idempotency_key
                WHERE platform_idempotency_key_id = ? AND caller_user_id = ? AND status = 'IN_PROGRESS'
                """, claimId, callerUserId);
    }

    private JdbcTemplate requireJdbc() {
        if (jdbc == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "IDEMPOTENCY_STORE_UNAVAILABLE",
                    "The platform idempotency store is unavailable in this runtime.");
        }
        return jdbc;
    }

    static String requestHash(Object requestBody, ObjectMapper mapper) {
        try {
            byte[] canonicalJson = mapper.writeValueAsBytes(requestBody);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonicalJson));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalArgumentException("Request body cannot be hashed.", ex);
        }
    }

    private static ApiException requestInProgress() {
        return new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_REQUEST_IN_PROGRESS",
                "An identical platform request is already being processed.");
    }
}

package com.edu360.api.platformops;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.edu360.api.exception.ApiException;

/** Shared strong ETag and If-Match checks for platform resources exposing row_version. */
@Component
public class PlatformConcurrency {

    public String etag(int rowVersion) {
        if (rowVersion < 1) {
            throw new IllegalArgumentException("rowVersion must be positive.");
        }
        return "\"" + rowVersion + "\"";
    }

    public void requireIfMatch(String ifMatch, int currentRowVersion) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, "IF_MATCH_REQUIRED",
                    "If-Match is required for this update.");
        }
        String candidate = ifMatch.trim();
        if (candidate.length() >= 2 && candidate.startsWith("\"") && candidate.endsWith("\"")) {
            candidate = candidate.substring(1, candidate.length() - 1);
        }
        final int requestedVersion;
        try {
            if ("*".equals(candidate) || candidate.startsWith("W/")) {
                throw new NumberFormatException();
            }
            requestedVersion = Integer.parseInt(candidate);
        } catch (NumberFormatException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IF_MATCH",
                    "If-Match must contain the resource ETag version.");
        }
        if (requestedVersion != currentRowVersion) {
            throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                    "The resource changed since it was read. Refresh and retry.");
        }
    }
}

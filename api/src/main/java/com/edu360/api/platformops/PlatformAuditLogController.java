package com.edu360.api.platformops;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platformops.dto.AuditLogEntryResponse;
import com.edu360.api.platformops.dto.AuditLogPageResponse;
import com.edu360.api.platformops.role.PlatformAuditWriter;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/v1/platform/audit-logs", "/api/platform/audit-logs"})
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "Platform Ops", description = "Platform Admin / Product Owner read-mostly operations")
public class PlatformAuditLogController {
    private static final int MAX_EXPORT_ROWS = 50_000;
    private static final int EXPORT_BATCH_SIZE = 500;

    private final PlatformPermissionService platformPermissionService;
    private final PlatformAuditOutboxEventRepository auditOutboxEventRepository;
    private final PlatformAuditWriter platformAuditWriter;

    @GetMapping
    @io.swagger.v3.oas.annotations.Operation(summary = "List filtered platform audit events (PLATFORM_AUDIT_VIEW)")
    public ResponseEntity<AuditLogPageResponse> listAuditLogs(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) String actionCode,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        PlatformPrincipal principal = requireAuditViewer(authentication);
        validateRange(from, to);
        int safeSize = Math.min(Math.max(size, 1), 200);
        Page<PlatformAuditOutboxEvent> result = auditOutboxEventRepository.findFiltered(
                tenantId, actorUserId, blankToNull(actionCode), blankToNull(outcome),
                from, to, null, PageRequest.of(Math.max(page, 0), safeSize));
        return ResponseEntity.ok(new AuditLogPageResponse(
                result.getContent().stream().map(AuditLogEntryResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @io.swagger.v3.oas.annotations.Operation(summary = "Stream filtered platform audit events as redacted CSV")
    public ResponseEntity<StreamingResponseBody> exportAuditLogs(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) String actionCode,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            Authentication authentication) {
        PlatformPrincipal principal = requireAuditViewer(authentication);
        validateRange(from, to);
        String action = blankToNull(actionCode), result = blankToNull(outcome);
        long matchingRows = auditOutboxEventRepository.countFiltered(
                tenantId, actorUserId, action, result, from, to, null);
        UUID exportId = UUID.randomUUID();
        if (matchingRows > MAX_EXPORT_ROWS) {
            platformAuditWriter.record(principal, null, "PLATFORM_AUDIT_EXPORT_REJECTED", "EXPORT",
                    "PLATFORM_AUDIT_EXPORT", "AUDIT_LOG_EXPORT", exportId, PlatformAuditWriter.FAILED);
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Audit export exceeds the 50,000 row limit; narrow the filters.");
        }

        // Record the export itself, then exclude that one event from the exported result set.
        platformAuditWriter.record(principal, null, "PLATFORM_AUDIT_EXPORTED", "EXPORT",
                "PLATFORM_AUDIT_EXPORT", "AUDIT_LOG_EXPORT", exportId, PlatformAuditWriter.SUCCESS);
        StreamingResponseBody stream = output -> writeCsv(output, tenantId, actorUserId, action,
                result, from, to, exportId);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"platform-audit-logs.csv\"")
                .body(stream);
    }

    private void writeCsv(OutputStream output, UUID tenantId, UUID actorUserId, String actionCode,
            String outcome, Instant from, Instant to, UUID excludeEntityId) throws java.io.IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
        writer.write("auditOutboxId,tenantId,occurredAt,actorKind,actorUserId,operation,actionCode,entityType,entityId,outcome,correlationId,eventType,payload\r\n");
        int pageNumber = 0;
        while (true) {
            Page<PlatformAuditOutboxEvent> page = auditOutboxEventRepository.findFiltered(
                    tenantId, actorUserId, actionCode, outcome, from, to, excludeEntityId,
                    PageRequest.of(pageNumber, EXPORT_BATCH_SIZE));
            for (PlatformAuditOutboxEvent event : page.getContent()) {
                var payload = PlatformAuditPayloadRedactor.redact(event.getPayload());
                writeRow(writer,
                        event.getAuditOutboxId(), event.getTenantId(), event.getOccurredAt(),
                        event.getActorKind(), event.getActorUserId(), event.getOperation(),
                        event.getActionCode(), event.getEntityType(), event.getEntityId(),
                        event.getOutcome(), event.getCorrelationId(), event.getEventType(),
                        payload == null ? null : payload.toString());
            }
            writer.flush();
            if (!page.hasNext()) break;
            pageNumber++;
        }
    }

    private static void writeRow(BufferedWriter writer, Object... values) throws java.io.IOException {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) writer.write(',');
            writer.write(csvCell(values[i] == null ? "" : values[i].toString()));
        }
        writer.write("\r\n");
    }

    private static String csvCell(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private PlatformPrincipal requireAuditViewer(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof PlatformPrincipal principal)) {
            throw new UnauthorizedException("PLATFORM_AUDIT_VIEW is a platform-side action; no platform principal on this request.");
        }
        platformPermissionService.authorize(principal, "PLATFORM_AUDIT_VIEW");
        return principal;
    }

    private static void validateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The from filter must be before or equal to to.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

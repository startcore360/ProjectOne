package com.edu360.api.platformops.rbac;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.role.PlatformAuditWriter;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/v1/platform", "/api/platform"})
@RequiredArgsConstructor
public class RbacMappingReportController {
    private final PlatformPermissionService permissions;
    private final RbacMappingReportService reports;
    private final PlatformAuditWriter audit;

    @GetMapping("/rbac-mapping-report")
    public ResponseEntity<RbacMappingReport> report(Authentication authentication) {
        PlatformPrincipal actor = requireActor(authentication);
        permissions.authorize(actor, "RBAC_MAPPING_CHECK");
        RbacMappingReport result = reports.generate();
        audit.record(actor, null, "RBAC_MAPPING_REPORT_VIEWED", "VIEW_SENSITIVE",
                "RBAC_MAPPING_CHECK", "RBAC_MAPPING_REPORT", UUID.randomUUID(), PlatformAuditWriter.SUCCESS);
        return ResponseEntity.ok(result);
    }

    @GetMapping(value = "/rbac-mapping-report/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(Authentication authentication) {
        PlatformPrincipal actor = requireActor(authentication);
        permissions.authorize(actor, "RBAC_MAPPING_CHECK");
        RbacMappingReport result = reports.generate();
        StringBuilder csv = new StringBuilder("scope,tenantId,tenantName,roleCode,roleName,actionCode,result,detail\r\n");
        result.mismatches().forEach(row -> append(csv, row));
        result.noBaseTemplate().forEach(row -> append(csv, row));
        audit.record(actor, null, "RBAC_MAPPING_REPORT_EXPORTED", "EXPORT",
                "RBAC_MAPPING_CHECK", "RBAC_MAPPING_REPORT", UUID.randomUUID(), PlatformAuditWriter.SUCCESS);
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"rbac-mapping-report.csv\"")
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private PlatformPrincipal requireActor(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof PlatformPrincipal principal)) {
            throw new UnauthorizedException("RBAC_MAPPING_CHECK requires a platform principal.");
        }
        return principal;
    }

    private static void append(StringBuilder csv, RbacMappingReportRow row) {
        Object[] values = {row.scope(), row.tenantId(), row.tenantName(), row.roleCode(),
                row.roleName(), row.actionCode(), row.result(), row.detail()};
        for (int i = 0; i < values.length; i++) {
            if (i > 0) csv.append(',');
            String value = values[i] == null ? "" : values[i].toString();
            if (!value.isEmpty() && ("=+-@".indexOf(value.charAt(0)) >= 0
                    || value.charAt(0) == '\t' || value.charAt(0) == '\r')) {
                value = "'" + value;
            }
            csv.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        csv.append("\r\n");
    }
}

package com.edu360.api.academic;

import com.edu360.api.config.TenantPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Student Status", description = "Student lifecycle status changes [STUDENT_STATUS_CHANGE / Action #41]")
public class StudentStatusController {

    private final StudentStatusService studentStatusService;

    @GetMapping({"/v1/tenants/{tenantId}/students/{id}", "/api/tenants/{tenantId}/students/{id}"})
    @Operation(summary = "Get student profile status")
    public ResponseEntity<StudentStatusResponse> getStudent(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            Authentication authentication
    ) {
        extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(studentStatusService.getStudent(tenantId, id));
    }

    @PostMapping({"/v1/tenants/{tenantId}/students/{id}/status-changes", "/api/tenants/{tenantId}/students/{id}/status-changes"})
    @Operation(summary = "Change a student's status [STUDENT_STATUS_CHANGE / Action #41]")
    public ResponseEntity<StudentStatusResponse> changeStatus(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody ChangeStudentStatusRequest request,
            Authentication authentication
    ) {
        TenantPrincipal principal = extractPrincipal(authentication, tenantId);
        return ResponseEntity.ok(studentStatusService.changeStudentStatus(tenantId, principal, id, request));
    }

    private TenantPrincipal extractPrincipal(Authentication authentication, UUID tenantId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal p)) {
            throw new AccessDeniedException("Authentication required");
        }
        if (!tenantId.equals(p.tenantId())) {
            throw new AccessDeniedException("Cross-tenant access forbidden");
        }
        return p;
    }
}

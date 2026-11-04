package com.edu360.api.institution;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.institution.dto.StudentLoginSwitchRequest;
import com.edu360.api.institution.dto.StudentLoginSwitchResponse;
import com.edu360.api.rbac.RbacPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * WHAT: STUDENT_LOGIN_SWITCH - see StudentLoginSwitchService's javadoc.
 */
@RestController
@RequestMapping("/api/tenants/{tenantId}/classes/{classId}")
@RequiredArgsConstructor
@Tag(name = "Institution setup", description = "School-class institution setup (STUDENT_LOGIN_SWITCH)")
public class SchoolClassController {

    private final StudentLoginSwitchService studentLoginSwitchService;

    @PutMapping("/student-login")
    @Operation(summary = "Turn on/off view-only login for a school class (STUDENT_LOGIN_SWITCH)")
    public ResponseEntity<StudentLoginSwitchResponse> setStudentLogin(
            @PathVariable UUID tenantId,
            @PathVariable UUID classId,
            @Valid @RequestBody StudentLoginSwitchRequest request,
            Authentication authentication) {

        if (!(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new UnauthorizedException("STUDENT_LOGIN_SWITCH is a tenant-side action; no tenant principal on this request.");
        }
        if (!tenantId.equals(principal.tenantId())) {
            throw new UnauthorizedException("tenantId in the URL does not match the authenticated tenant.");
        }

        RbacPrincipal rbacPrincipal = new RbacPrincipal(principal.tenantId(), principal.userAccountId());
        return ResponseEntity.ok(studentLoginSwitchService.setStudentLogin(rbacPrincipal, classId, request));
    }
}

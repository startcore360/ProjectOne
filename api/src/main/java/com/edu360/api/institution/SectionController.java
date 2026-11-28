package com.edu360.api.institution;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.institution.dto.ClassTeacherAssignRequest;
import com.edu360.api.institution.dto.ClassTeacherAssignResponse;
import com.edu360.api.institution.dto.MentorAssignRequest;
import com.edu360.api.institution.dto.MentorAssignResponse;
import com.edu360.api.rbac.RbacPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * WHAT: MENTOR_ASSIGN (college sections) and CLASS_TEACHER_ASSIGN (school sections) - see
 * MentorAssignmentService's and ClassTeacherAssignmentService's javadocs for the full rules,
 * including why CLASS_TEACHER_ASSIGN's unit is "section" here (OD-24 is still open).
 */
@RestController
@RequestMapping({"/v1/tenants/{tenantId}/sections/{sectionId}", "/api/tenants/{tenantId}/sections/{sectionId}"})
@RequiredArgsConstructor
@Tag(name = "Institution setup", description = "Section-level institution setup (MENTOR_ASSIGN, CLASS_TEACHER_ASSIGN)")
public class SectionController {

    private final MentorAssignmentService mentorAssignmentService;
    private final ClassTeacherAssignmentService classTeacherAssignmentService;

    @PutMapping("/mentor")
    @Operation(summary = "Assign a mentor / class coordinator to a college section (MENTOR_ASSIGN)")
    public ResponseEntity<MentorAssignResponse> assignMentor(
            @PathVariable UUID tenantId,
            @PathVariable UUID sectionId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody MentorAssignRequest request,
            Authentication authentication) {

        RbacPrincipal principal = tenantPrincipal(tenantId, authentication);
        var response = mentorAssignmentService.assignMentor(principal, sectionId, ifMatch, request);
        return ResponseEntity.ok().eTag(String.format("\"%d\"", response.rowVersion())).body(response);
    }

    @PutMapping("/class-teacher")
    @Operation(summary = "Assign a Class Teacher to a school section (CLASS_TEACHER_ASSIGN)")
    public ResponseEntity<ClassTeacherAssignResponse> assignClassTeacher(
            @PathVariable UUID tenantId,
            @PathVariable UUID sectionId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ClassTeacherAssignRequest request,
            Authentication authentication) {

        RbacPrincipal principal = tenantPrincipal(tenantId, authentication);
        var response = classTeacherAssignmentService.assignClassTeacher(principal, sectionId, ifMatch, request);
        return ResponseEntity.ok().eTag(String.format("\"%d\"", response.rowVersion())).body(response);
    }

    private RbacPrincipal tenantPrincipal(UUID tenantId, Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new UnauthorizedException("This is a tenant-side action; no tenant principal on this request.");
        }
        if (!tenantId.equals(principal.tenantId())) {
            throw new UnauthorizedException("tenantId in the URL does not match the authenticated tenant.");
        }
        return new RbacPrincipal(principal.tenantId(), principal.userAccountId());
    }
}

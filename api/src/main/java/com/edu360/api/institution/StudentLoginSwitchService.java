package com.edu360.api.institution;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.institution.dto.StudentLoginSwitchRequest;
import com.edu360.api.institution.dto.StudentLoginSwitchResponse;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

import lombok.RequiredArgsConstructor;

/**
 * WHAT: STUDENT_LOGIN_SWITCH ("Turn on view-only login for chosen school classes") - School
 * Admin, own school. Flips app.school_class.student_login_enabled - the exact column the schema
 * comment names for this action ("View-only login for chosen classes (STUDENT_LOGIN_SWITCH)
 * [PR-04]").
 *
 * ORDER OF CHECKS: load the class (read-only) to learn its institutionId, authorize at
 * RbacScope.institution(classRow.institutionId) - "Own school" - then persist.
 */
@Service
@RequiredArgsConstructor
public class StudentLoginSwitchService {

    private final SchoolClassRepository schoolClassRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;

    @Transactional
    public StudentLoginSwitchResponse setStudentLogin(
            RbacPrincipal principal, UUID classId, StudentLoginSwitchRequest request) {
        SchoolClass schoolClass = schoolClassRepository.findByTenantIdAndClassId(principal.tenantId(), classId)
                .orElseThrow(() -> new ResourceNotFoundException("Class " + classId + " was not found."));

        permissionEvaluatorService.authorize(principal, "STUDENT_LOGIN_SWITCH",
                RbacScope.institution(schoolClass.getInstitutionId()), RbacTarget.NONE);

        int expectedVersion = parseIfMatchVersion(ifMatch);
        if (schoolClass.getRowVersion() == null || schoolClass.getRowVersion() != expectedVersion) {
            throw new BusinessRuleViolationException("VERSION_CONFLICT", "School class was changed by another user. Reload and retry with the latest ETag.");
        }

        int updated = schoolClassRepository.updateStudentLogin(principal.tenantId(), classId,
                expectedVersion, request.enabled(), principal.userAccountId());
        if (updated != 1) {
            throw new BusinessRuleViolationException("VERSION_CONFLICT", "School class was changed by another user. Reload and retry with the latest ETag.");
        }
        return new StudentLoginSwitchResponse(schoolClass.getClassId(), request.enabled(), expectedVersion + 1);
    }

    private int parseIfMatchVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new BusinessRuleViolationException("IF_MATCH_REQUIRED", "If-Match header with the class ETag is required.");
        }
        String value = ifMatch.trim();
        if (value.startsWith("W/")) value = value.substring(2).trim();
        value = value.replaceAll("\\\"", "");
        try {
            int version = Integer.parseInt(value);
            if (version < 1) throw new NumberFormatException();
            return version;
        } catch (NumberFormatException ex) {
            throw new BusinessRuleViolationException("IF_MATCH_REQUIRED", "If-Match must contain a positive class row version.");
        }
    }
}

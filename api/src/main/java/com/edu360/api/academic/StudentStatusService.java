package com.edu360.api.academic;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.identity.StudentProfile;
import com.edu360.api.identity.StudentProfileRepository;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudentStatusService {

    private final StudentProfileRepository repository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final AuditLogService auditLogService;

    private static final Set<String> VALID_STATUSES = Set.of(
            "ACTIVE", "ON_HOLD", "WITHDRAWN", "TRANSFERRED", "DROPPED", "DETAINED", "GRADUATED"
    );

    @Transactional
    public StudentStatusResponse changeStudentStatus(UUID tenantId, TenantPrincipal principal, UUID studentId, ChangeStudentStatusRequest request) {
        StudentProfile student = repository.findById(studentId)
                .filter(s -> s.getTenantId().equals(tenantId))
                .orElseThrow(() -> new BusinessRuleViolationException("STUDENT_NOT_FOUND", "Student record not found"));

        permissionEvaluatorService.authorize(
                new RbacPrincipal(tenantId, principal.userAccountId()),
                "STUDENT_STATUS_CHANGE",
                student.getInstitutionId() != null ? RbacScope.institution(student.getInstitutionId()) : RbacScope.tenant(),
                RbacTarget.NONE
        );

        String upperStatus = request.status().toUpperCase();
        if (!VALID_STATUSES.contains(upperStatus)) {
            throw new BusinessRuleViolationException("INVALID_STATUS",
                    "Student status must be one of: " + VALID_STATUSES);
        }

        if (request.reason() == null || request.reason().isBlank()) {
            throw new BusinessRuleViolationException("REASON_REQUIRED",
                    "Mandatory statutory reason required for student status change");
        }

        // Rule: hard delete prohibited. If deletion requested, it sets WITHDRAWN with statutory reason
        student.setStatus(upperStatus);
        student.setStatusReason(request.reason());
        student.setStatusChangedAt(Instant.now());
        student = repository.save(student);

        auditLogService.record(
                AuditEventType.ACCOUNT_CREATED,
                null,
                principal.userAccountId().toString(),
                tenantId.toString(),
                "Student " + studentId + " status transitioned to " + upperStatus + ": " + request.reason()
        );

        log.info("Student {} status changed to {} by {} in tenant {}",
                studentId, upperStatus, principal.userAccountId(), tenantId);

        return new StudentStatusResponse(
                student.getStudentProfileId(),
                student.getInstitutionId(),
                student.getStudentCode(),
                student.getStatus(),
                student.getStatusReason(),
                student.getStatusChangedAt()
        );
    }

    @Transactional(readOnly = true)
    public StudentStatusResponse getStudent(UUID tenantId, TenantPrincipal principal, UUID studentId) {
        StudentProfile student = repository.findById(studentId)
                .filter(s -> s.getTenantId().equals(tenantId))
                .orElseThrow(() -> new BusinessRuleViolationException("STUDENT_NOT_FOUND", "Student record not found"));

        permissionEvaluatorService.authorize(
                new RbacPrincipal(tenantId, principal.userAccountId()),
                "STUDENT_STATUS_CHANGE",
                student.getInstitutionId() != null ? RbacScope.institution(student.getInstitutionId()) : RbacScope.tenant(),
                RbacTarget.NONE
        );

        return new StudentStatusResponse(
                student.getStudentProfileId(),
                student.getInstitutionId(),
                student.getStudentCode(),
                student.getStatus(),
                student.getStatusReason(),
                student.getStatusChangedAt()
        );
    }
}

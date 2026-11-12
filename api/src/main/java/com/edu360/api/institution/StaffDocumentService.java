package com.edu360.api.institution;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.audit.AuditEntry;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.files.FileAccessPolicy;
import com.edu360.api.files.FileObject;
import com.edu360.api.files.FileObjectId;
import com.edu360.api.files.FileObjectRepository;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.rbac.ScopeType;

@Service
public class StaffDocumentService {
    private static final List<String> DOCUMENT_TYPES = List.of("DEGREE", "ID_PROOF", "OTHER");
    private final StaffProfileRepository staffProfiles;
    private final StaffDocumentRepository documents;
    private final FileObjectRepository files;
    private final FileAccessPolicy fileAccessPolicy;
    private final JdbcTemplate jdbc;
    private final PermissionEvaluatorService permissions;
    private final AuditLogService auditLog;

    public StaffDocumentService(StaffProfileRepository staffProfiles, StaffDocumentRepository documents,
            FileObjectRepository files, FileAccessPolicy fileAccessPolicy, JdbcTemplate jdbc,
            PermissionEvaluatorService permissions, AuditLogService auditLog) {
        this.staffProfiles = staffProfiles;
        this.documents = documents;
        this.files = files;
        this.fileAccessPolicy = fileAccessPolicy;
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.auditLog = auditLog;
    }

    @Transactional
    public StaffDocumentResponse attach(TenantPrincipal actor, UUID staffProfileId,
            String documentTypeValue, UUID fileId) {
        String documentType = documentTypeValue == null ? ""
                : documentTypeValue.trim().toUpperCase(Locale.ROOT);
        if (!DOCUMENT_TYPES.contains(documentType) || fileId == null) {
            throw new BusinessRuleViolationException("VALIDATION_FAILED",
                    "documentType must be DEGREE, ID_PROOF or OTHER and fileId is required.");
        }
        RbacPrincipal caller = new RbacPrincipal(actor.tenantId(), actor.userAccountId());
        StaffProfile profile = staffProfiles.findByIdTenantIdAndIdStaffProfileId(actor.tenantId(), staffProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff record not found."));
        authorizeTargetAssignments(caller, profile.getStaffProfileId());

        FileObject file = files.findById(new FileObjectId(actor.tenantId(), fileId))
                .orElseThrow(() -> new ResourceNotFoundException("File not found."));
        if (!fileAccessPolicy.canAttach(file, actor, "STAFF_DOCUMENT", staffProfileId)) {
            throw new ResourceNotFoundException("Eligible uploaded file or staff record not found.");
        }
        if (documents.existsByIdTenantIdAndFileId(actor.tenantId(), fileId)) {
            throw new BusinessRuleViolationException("FILE_ALREADY_ATTACHED",
                    "This uploaded file is already attached to a staff document.");
        }

        UUID documentId = UUID.randomUUID();
        StaffDocument document = new StaffDocument();
        document.setId(new StaffDocumentId(actor.tenantId(), documentId));
        document.setStaffProfileId(staffProfileId);
        document.setDocumentType(documentType);
        document.setFileId(fileId);
        document.setVerificationStatus("UPLOADED");
        document.setCreatedBy(actor.userAccountId());
        document.setUpdatedBy(actor.userAccountId());
        StaffDocument saved = documents.save(document);

        auditLog.record(new AuditEntry(AuditEventType.STAFF_DOCUMENT_ATTACHED,
                "STAFF_RECORD_MANAGE", "STAFF_DOCUMENT", documentId,
                file.getInstitutionId(), null,
                java.util.Map.of("staff_profile_id", staffProfileId.toString(),
                        "file_id", fileId.toString(), "document_type", documentType),
                1, "P", "SUCCESS"));
        return StaffDocumentResponse.from(saved);
    }

    @Transactional
    public StaffDocumentResponse verify(TenantPrincipal actor, UUID staffDocumentId, String decision, String remark) {
        String normalized = decision == null ? "" : decision.trim().toUpperCase(Locale.ROOT);
        if (!List.of("VERIFIED", "REJECTED").contains(normalized)) {
            throw new BusinessRuleViolationException("VALIDATION_FAILED",
                    "verificationStatus must be VERIFIED or REJECTED.");
        }
        if (remark != null && remark.length() > 1000) {
            throw new BusinessRuleViolationException("VALIDATION_FAILED", "remark cannot exceed 1000 characters.");
        }
        StaffDocument document = documents.findByIdTenantIdAndIdStaffDocumentId(actor.tenantId(), staffDocumentId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff document not found."));
        if (!"UPLOADED".equals(document.getVerificationStatus())) {
            throw new BusinessRuleViolationException("DOCUMENT_ALREADY_VERIFIED",
                    "Only an uploaded, undecided staff document can be verified.");
        }
        if (actor.userAccountId().equals(document.getCreatedBy())) {
            throw new BusinessRuleViolationException("SELF_VERIFICATION_NOT_ALLOWED",
                    "The uploader cannot verify or reject their own document.");
        }
        StaffProfile profile = staffProfiles.findByIdTenantIdAndIdStaffProfileId(
                actor.tenantId(), document.getStaffProfileId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff document not found."));
        authorizeVerificationScope(new RbacPrincipal(actor.tenantId(), actor.userAccountId()),
                profile.getStaffProfileId());
        document.setVerificationStatus(normalized);
        document.setVerifiedBy(actor.userAccountId());
        document.setVerifiedAt(java.time.Instant.now());
        document.setUpdatedBy(actor.userAccountId());
        StaffDocument saved = documents.save(document);
        auditLog.record(new AuditEntry(AuditEventType.STAFF_DOCUMENT_VERIFIED,
                "STAFF_DOCUMENT_VERIFY", "STAFF_DOCUMENT", staffDocumentId,
                null, java.util.Map.of("verification_status", "UPLOADED"),
                java.util.Map.of("verification_status", normalized, "remark", remark == null ? "" : remark.trim()), 1, "P", "SUCCESS"));
        return StaffDocumentResponse.from(saved);
    }

    private void authorizeVerificationScope(RbacPrincipal caller, UUID staffProfileId) {
        var active = permissions.activeAssignment(caller)
                .orElseThrow(() -> new com.edu360.api.exception.UnauthorizedException(
                        "An active staff-document verification role is required."));
        ScopeType scopeType = active.getScopeType();
        if (scopeType == ScopeType.TENANT) {
            permissions.authorize(caller, "STAFF_DOCUMENT_VERIFY", RbacScope.tenant(), RbacTarget.NONE);
            boolean exists = Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM app.staff_assignment
                     WHERE tenant_id = ? AND staff_profile_id = ? AND status = 'ACTIVE')
                    """, Boolean.class, caller.tenantId(), staffProfileId));
            if (!exists) throw new ResourceNotFoundException("Staff document not found.");
            return;
        }
        List<AssignmentScope> scopes = jdbc.query("""
                SELECT institution_id, department_id
                  FROM app.staff_assignment
                 WHERE tenant_id = ? AND staff_profile_id = ?
                   AND institution_id IS NOT NULL AND status = 'ACTIVE'
                """, (rs, row) -> new AssignmentScope(
                        rs.getObject("institution_id", UUID.class),
                        rs.getObject("department_id", UUID.class)),
                caller.tenantId(), staffProfileId);
        for (AssignmentScope target : scopes) {
            if (scopeType == ScopeType.INSTITUTION
                    && !active.getInstitutionId().equals(target.institutionId())) continue;
            if (scopeType == ScopeType.DEPARTMENT
                    && (!active.getInstitutionId().equals(target.institutionId())
                    || !active.getDepartmentId().equals(target.departmentId()))) continue;
            RbacScope targetScope = scopeType == ScopeType.DEPARTMENT
                    ? RbacScope.department(target.institutionId(), target.departmentId())
                    : RbacScope.institution(target.institutionId());
            permissions.authorize(caller, "STAFF_DOCUMENT_VERIFY", targetScope, RbacTarget.NONE);
            return;
        }
        throw new ResourceNotFoundException("Staff document not found in the active role scope.");
    }

    private void authorizeTargetAssignments(RbacPrincipal caller, UUID staffProfileId) {
        var active = permissions.activeAssignment(caller)
                .orElseThrow(() -> new com.edu360.api.exception.UnauthorizedException(
                        "An active staff-management role is required."));
        ScopeType scopeType = active.getScopeType();
        if (scopeType == ScopeType.TENANT) {
            permissions.authorize(caller, "STAFF_RECORD_MANAGE", RbacScope.tenant(), RbacTarget.NONE);
            boolean exists = Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM app.staff_assignment
                     WHERE tenant_id = ? AND staff_profile_id = ?)
                    """, Boolean.class, caller.tenantId(), staffProfileId));
            if (!exists) throw new ResourceNotFoundException("Staff record not found.");
            return;
        }
        List<AssignmentScope> scopes = jdbc.query("""
                SELECT institution_id, department_id
                  FROM app.staff_assignment
                 WHERE tenant_id = ? AND staff_profile_id = ?
                   AND institution_id IS NOT NULL AND status = 'ACTIVE'
                """, (rs, row) -> new AssignmentScope(
                        rs.getObject("institution_id", UUID.class),
                        rs.getObject("department_id", UUID.class)),
                caller.tenantId(), staffProfileId);
        for (AssignmentScope target : scopes) {
            if (scopeType == ScopeType.INSTITUTION
                    && !active.getInstitutionId().equals(target.institutionId())) continue;
            if (scopeType == ScopeType.DEPARTMENT
                    && (!active.getInstitutionId().equals(target.institutionId())
                    || !active.getDepartmentId().equals(target.departmentId()))) continue;
            RbacScope targetScope = scopeType == ScopeType.DEPARTMENT
                    ? RbacScope.department(target.institutionId(), target.departmentId())
                    : RbacScope.institution(target.institutionId());
            permissions.authorize(caller, "STAFF_RECORD_MANAGE", targetScope, RbacTarget.NONE);
            return;
        }
        throw new ResourceNotFoundException("Staff record not found in the active role scope.");
    }

    private record AssignmentScope(UUID institutionId, UUID departmentId) {}
}

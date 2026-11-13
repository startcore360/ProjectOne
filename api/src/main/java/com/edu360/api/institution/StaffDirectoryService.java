package com.edu360.api.institution;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.audit.AuditEntry;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.DuplicateResourceException;
import com.edu360.api.exception.ResourceNotFoundException;

import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.rbac.ScopeType;

@Service
public class StaffDirectoryService {
    private static final Set<String> EMPLOYMENT_STATUSES = Set.of("ACTIVE", "INACTIVE", "LEFT");
    private static final Set<String> SERVICE_EVENT_TYPES = Set.of(
            "JOINED", "DESIGNATION_CHANGE", "TRANSFER", "CONFIRMATION",
            "LEAVE_OF_ABSENCE", "LEFT", "OTHER");

    private final StaffProfileRepository staffProfiles;
    private final StaffAssignmentRepository assignments;
    private final StaffServiceRecordRepository serviceRecords;
    private final StaffDocumentRepository documents;
    private final InstitutionRepository institutions;
    private final DepartmentRepository departments;
    private final InstitutionIdFormatService idFormats;
    private final PermissionEvaluatorService permissions;
    private final AuditLogService audit;
    private final JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entityManager;

    public StaffDirectoryService(StaffProfileRepository staffProfiles,
            StaffAssignmentRepository assignments, StaffServiceRecordRepository serviceRecords,
            StaffDocumentRepository documents, InstitutionRepository institutions,
            DepartmentRepository departments, InstitutionIdFormatService idFormats,
            PermissionEvaluatorService permissions, AuditLogService audit, JdbcTemplate jdbc) {
        this.staffProfiles = staffProfiles;
        this.assignments = assignments;
        this.serviceRecords = serviceRecords;
        this.documents = documents;
        this.institutions = institutions;
        this.departments = departments;
        this.idFormats = idFormats;
        this.permissions = permissions;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public StaffListResponse list(RbacPrincipal actor, String query, int limit) {
        validateLimit(limit);
        var active = permissions.activeAssignment(actor)
                .orElseThrow(() -> new com.edu360.api.exception.UnauthorizedException("An active staff role is required."));
        UUID institutionId = active.getScopeType() == ScopeType.TENANT ? null : active.getInstitutionId();
        UUID departmentId = active.getScopeType() == ScopeType.DEPARTMENT ? active.getDepartmentId() : null;
        RbacScope scope = activeScope(active.getScopeType(), institutionId, departmentId);
        permissions.authorize(actor, "STAFF_RECORD_VIEW", scope, RbacTarget.NONE);
        List<StaffProfile> profiles = staffProfiles.searchTenantStaff(actor.tenantId(),
                institutionId, departmentId, normalizeQuery(query), limit);
        boolean includeSensitive = permissions.can(actor, "STAFF_RECORD_MANAGE", scope, RbacTarget.NONE).allowed();
        if (includeSensitive) {
            profiles.forEach(profile -> recordSensitiveView(actor, profile, institutionId));
        }
        List<StaffProfileResponse> items = profiles.stream()
                .map(profile -> StaffProfileResponse.from(profile, includeSensitive)).toList();
        return new StaffListResponse(items, new StaffListResponse.PageInfo(limit, null, false, null));
    }

    @Transactional
    public StaffProfileResponse create(RbacPrincipal actor, StaffProfileRequest request) {
        var active = permissions.activeAssignment(actor)
                .orElseThrow(() -> new com.edu360.api.exception.UnauthorizedException("An active staff role is required."));
        UUID institutionId = active.getScopeType() == ScopeType.TENANT ? null : active.getInstitutionId();
        RbacScope scope = activeScope(active.getScopeType(), institutionId,
                active.getScopeType() == ScopeType.DEPARTMENT ? active.getDepartmentId() : null);
        permissions.authorize(actor, "STAFF_RECORD_MANAGE", scope, RbacTarget.NONE);

        validateProfile(request.fullName(), request.personalEmail(), request.personalMobile(),
                request.nationalIdLast4(), request.employmentStatus(), request.joinedOn(), request.leftOn());
        int joiningYear = request.joinedOn() == null ? LocalDate.now().getYear() : request.joinedOn().getYear();
        String staffCode = idFormats.resolveStaffCode(actor.tenantId(), institutionId,
                request.staffCode(), joiningYear);

        StaffProfile profile = new StaffProfile();
        UUID id = UUID.randomUUID();
        profile.setId(new StaffProfileId(actor.tenantId(), id));
        profile.setStaffCode(staffCode);
        profile.setFullName(request.fullName().trim());
        profile.setDateOfBirth(request.dateOfBirth());
        profile.setPersonalEmail(trimToNull(request.personalEmail()));
        profile.setPersonalMobile(trimToNull(request.personalMobile()));
        profile.setNationalIdLast4(trimToNull(request.nationalIdLast4()));
        profile.setPhotoFileId(request.photoFileId());
        profile.setEmploymentStatus(request.employmentStatus() == null ? "ACTIVE" : request.employmentStatus().trim().toUpperCase());
        profile.setJoinedOn(request.joinedOn());
        profile.setLeftOn(request.leftOn());
        profile.setCreatedBy(actor.userAccountId());
        profile.setUpdatedBy(actor.userAccountId());
        StaffProfile saved = staffProfiles.saveAndFlush(profile);
        entityManager.refresh(saved);
        audit.record(new AuditEntry(AuditEventType.STAFF_PROFILE_CREATED, "STAFF_RECORD_MANAGE",
                "STAFF_PROFILE", id, institutionId, null, Map.of("employment_status", saved.getEmploymentStatus()),
                1, "P", "SUCCESS"));
        return StaffProfileResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public StaffProfileResponse get(RbacPrincipal actor, UUID staffProfileId) {
        StaffProfile profile = findScopedProfile(actor, staffProfileId, "STAFF_RECORD_VIEW");
        var active = permissions.activeAssignment(actor)
                .orElseThrow(() -> new com.edu360.api.exception.UnauthorizedException("An active staff role is required."));
        UUID institutionId = active.getScopeType() == ScopeType.TENANT ? null : active.getInstitutionId();
        RbacScope scope = activeScope(active.getScopeType(), institutionId,
                active.getScopeType() == ScopeType.DEPARTMENT ? active.getDepartmentId() : null);
        boolean includeSensitive = permissions.can(actor, "STAFF_RECORD_MANAGE", scope, RbacTarget.NONE).allowed();
        if (includeSensitive) recordSensitiveView(actor, profile, institutionId);
        return StaffProfileResponse.from(profile, includeSensitive);
    }

    @Transactional
    public StaffProfileResponse update(RbacPrincipal actor, UUID staffProfileId,
            String ifMatch, StaffProfileUpdateRequest request) {
        int expectedVersion = parseIfMatch(ifMatch);
        Integer current = jdbc.queryForObject("""
                SELECT row_version FROM app.staff_profile
                 WHERE tenant_id=? AND staff_profile_id=? FOR UPDATE
                """, Integer.class, actor.tenantId(), staffProfileId);
        if (current == null || current != expectedVersion) {
            throw new BusinessRuleViolationException("VERSION_CONFLICT", "The staff record has changed. Reload it and retry.");
        }
        StaffProfile profile = findScopedProfile(actor, staffProfileId, "STAFF_RECORD_MANAGE");
        if (request.staffCode() != null && !request.staffCode().isBlank()
                && !request.staffCode().equalsIgnoreCase(profile.getStaffCode())) {
            if (staffProfiles.existsByIdTenantIdAndStaffCode(actor.tenantId(), request.staffCode().trim().toUpperCase())) {
                throw new DuplicateResourceException("Staff code is already used in this tenant.");
            }
            profile.setStaffCode(request.staffCode().trim().toUpperCase());
        }
        if (request.fullName() != null) profile.setFullName(request.fullName().trim());
        if (request.dateOfBirth() != null) profile.setDateOfBirth(request.dateOfBirth());
        if (request.personalEmail() != null) profile.setPersonalEmail(trimToNull(request.personalEmail()));
        if (request.personalMobile() != null) profile.setPersonalMobile(trimToNull(request.personalMobile()));
        if (request.nationalIdLast4() != null) profile.setNationalIdLast4(trimToNull(request.nationalIdLast4()));
        if (request.photoFileId() != null) profile.setPhotoFileId(request.photoFileId());
        if (request.employmentStatus() != null) profile.setEmploymentStatus(request.employmentStatus().trim().toUpperCase());
        if (request.joinedOn() != null) profile.setJoinedOn(request.joinedOn());
        if (request.leftOn() != null) profile.setLeftOn(request.leftOn());
        validateProfile(profile.getFullName(), profile.getPersonalEmail(), profile.getPersonalMobile(),
                profile.getNationalIdLast4(), profile.getEmploymentStatus(), profile.getJoinedOn(), profile.getLeftOn());
        profile.setUpdatedBy(actor.userAccountId());
        StaffProfile saved = staffProfiles.saveAndFlush(profile);
        entityManager.refresh(saved);
        audit.record(new AuditEntry(AuditEventType.STAFF_PROFILE_UPDATED, "STAFF_RECORD_MANAGE",
                "STAFF_PROFILE", staffProfileId, null, null,
                Map.of("employment_status", saved.getEmploymentStatus()), 1, "P", "SUCCESS"));
        return StaffProfileResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<StaffAssignmentResponse> listAssignments(RbacPrincipal actor, UUID staffProfileId) {
        findScopedProfile(actor, staffProfileId, "STAFF_RECORD_VIEW");
        return assignments.findByTenantIdAndStaffProfileIdOrderByEffectiveFromDesc(
                actor.tenantId(), staffProfileId).stream()
                .map(StaffAssignmentResponse::from).toList();
    }

    @Transactional
    public StaffAssignmentResponse addAssignment(RbacPrincipal actor, UUID staffProfileId,
            StaffAssignmentRequest request) {
        StaffProfile profile = staffProfiles.findByIdTenantIdAndIdStaffProfileId(actor.tenantId(), staffProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff record not found."));
        validateAssignmentScope(actor, request.institutionId(), request.departmentId());
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw new BusinessRuleViolationException("VALIDATION_FAILED", "effectiveTo cannot precede effectiveFrom.");
        }
        Boolean overlapping = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                      FROM app.staff_assignment
                     WHERE tenant_id = ?
                       AND staff_profile_id = ?
                       AND institution_id IS NOT DISTINCT FROM ?
                       AND department_id IS NOT DISTINCT FROM ?
                       AND status = 'ACTIVE'
                       AND effective_from <= COALESCE(?, DATE '9999-12-31')
                       AND COALESCE(effective_to, DATE '9999-12-31') >= ?
                )
                """, Boolean.class, actor.tenantId(), staffProfileId, request.institutionId(),
                request.departmentId(), request.effectiveTo(), request.effectiveFrom());
        if (Boolean.TRUE.equals(overlapping)) {
            throw new DuplicateResourceException("An active staff assignment overlaps the requested effective date range for this scope.");
        }
        StaffAssignment assignment = new StaffAssignment();
        assignment.setStaffAssignmentId(UUID.randomUUID());
        assignment.setTenantId(actor.tenantId());
        assignment.setStaffProfileId(staffProfileId);
        assignment.setInstitutionId(request.institutionId());
        assignment.setDepartmentId(request.departmentId());
        assignment.setDesignation(request.designation().trim());
        assignment.setEffectiveFrom(request.effectiveFrom());
        assignment.setEffectiveTo(request.effectiveTo());
        assignment.setStatus(request.effectiveTo() != null && request.effectiveTo().isBefore(LocalDate.now()) ? "ENDED" : "ACTIVE");
        assignment.setCreatedBy(actor.userAccountId());
        assignment.setUpdatedBy(actor.userAccountId());
        StaffAssignment saved = assignments.saveAndFlush(assignment);
        entityManager.refresh(saved);
        audit.record(new AuditEntry(AuditEventType.STAFF_ASSIGNMENT_CREATED, "STAFF_RECORD_MANAGE",
                "STAFF_ASSIGNMENT", saved.getStaffAssignmentId(), saved.getInstitutionId(), null,
                Map.of("staff_profile_id", staffProfileId.toString()), 1, "N", "SUCCESS"));
        return StaffAssignmentResponse.from(saved);
    }

    @Transactional
    public StaffAssignmentResponse endAssignment(RbacPrincipal actor, UUID staffProfileId,
            UUID assignmentId, String ifMatch, EndStaffAssignmentRequest request) {
        int expectedVersion = parseIfMatch(ifMatch);
        Integer current = jdbc.queryForObject("""
                SELECT row_version FROM app.staff_assignment
                 WHERE tenant_id=? AND staff_assignment_id=? AND staff_profile_id=? FOR UPDATE
                """, Integer.class, actor.tenantId(), assignmentId, staffProfileId);
        if (current == null || current != expectedVersion) {
            throw new BusinessRuleViolationException("VERSION_CONFLICT", "The assignment has changed. Reload it and retry.");
        }
        StaffAssignment assignment = assignments.findByTenantIdAndStaffAssignmentIdAndStaffProfileId(
                actor.tenantId(), assignmentId, staffProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff assignment not found."));
        authorizeScope(actor, assignment.getInstitutionId(), assignment.getDepartmentId(), "STAFF_RECORD_MANAGE");
        if (!"ACTIVE".equals(assignment.getStatus())) {
            throw new BusinessRuleViolationException("ASSIGNMENT_NOT_ACTIVE", "Only an active assignment can be ended.");
        }
        if (request.effectiveTo().isBefore(assignment.getEffectiveFrom())) {
            throw new BusinessRuleViolationException("VALIDATION_FAILED", "effectiveTo cannot precede effectiveFrom.");
        }
        List<String> conflicts = new java.util.ArrayList<>();
        conflicts.addAll(jdbc.query("""
                SELECT 'PUBLISHED_TIMETABLE_SLOT:' || ts.timetable_slot_id::text
                  FROM app.timetable_slot ts
                  JOIN app.subject_allocation sa
                    ON sa.tenant_id = ts.tenant_id
                   AND sa.subject_allocation_id = ts.subject_allocation_id
                 WHERE sa.tenant_id = ?
                   AND sa.staff_assignment_id = ?
                   AND sa.status = 'ACTIVE'
                   AND ts.status = 'PUBLISHED'
                   AND ts.valid_from <= ?
                   AND COALESCE(ts.valid_to, DATE '9999-12-31') >= ?
                """, (rs, rowNum) -> rs.getString(1),
                actor.tenantId(), assignmentId, request.effectiveTo(), request.effectiveTo()));
        conflicts.addAll(jdbc.query("""
                SELECT 'ACTIVE_SUBJECT_ALLOCATION:' || subject_allocation_id::text
                  FROM app.subject_allocation
                 WHERE tenant_id = ? AND staff_assignment_id = ? AND status = 'ACTIVE'
                """, (rs, rowNum) -> rs.getString(1), actor.tenantId(), assignmentId));
        conflicts.addAll(jdbc.query("""
                SELECT 'CURRENT_CLASS_TEACHER:' || class_teacher_assignment_id::text
                  FROM app.class_teacher_assignment
                 WHERE tenant_id = ? AND staff_assignment_id = ?
                   AND (effective_to IS NULL OR effective_to >= ?)
                """, (rs, rowNum) -> rs.getString(1), actor.tenantId(), assignmentId, request.effectiveTo()));
        conflicts.addAll(jdbc.query("""
                SELECT 'CURRENT_MENTOR:' || mentor_assignment_id::text
                  FROM app.mentor_assignment
                 WHERE tenant_id = ? AND staff_assignment_id = ?
                   AND (effective_to IS NULL OR effective_to >= ?)
                """, (rs, rowNum) -> rs.getString(1), actor.tenantId(), assignmentId, request.effectiveTo()));
        if (!conflicts.isEmpty()) {
            throw new BusinessRuleViolationException("STAFF_ASSIGNMENT_IN_USE",
                    "Assignment cannot be ended while dependent teaching allocations remain active. Reassign or end these records first: "
                            + String.join(", ", conflicts));
        }
        assignment.setEffectiveTo(request.effectiveTo());
        assignment.setStatus("ENDED");
        assignment.setUpdatedBy(actor.userAccountId());
        StaffAssignment saved = assignments.saveAndFlush(assignment);
        entityManager.refresh(saved);
        audit.record(new AuditEntry(AuditEventType.STAFF_ASSIGNMENT_ENDED, "STAFF_RECORD_MANAGE",
                "STAFF_ASSIGNMENT", assignmentId, assignment.getInstitutionId(),
                Map.of("status", "ACTIVE"), Map.of("status", "ENDED"), 1, "N", "SUCCESS"));
        return StaffAssignmentResponse.from(saved);
    }

    @Transactional
    public StaffServiceRecordResponse addServiceRecord(RbacPrincipal actor, UUID staffProfileId,
            StaffServiceRecordRequest request) {
        StaffProfile profile = findScopedProfile(actor, staffProfileId, "STAFF_RECORD_MANAGE");
        String eventType = request.eventType().trim().toUpperCase();
        if (!SERVICE_EVENT_TYPES.contains(eventType)) {
            throw new BusinessRuleViolationException("VALIDATION_FAILED", "eventType is not an approved SQL-defined service event.");
        }
        if (request.details().trim().isEmpty() || request.details().length() > 2000) {
            throw new BusinessRuleViolationException("VALIDATION_FAILED", "details must contain 1 to 2000 characters.");
        }
        if (request.staffAssignmentId() != null) {
            assignments.findByTenantIdAndStaffAssignmentIdAndStaffProfileId(
                    actor.tenantId(), request.staffAssignmentId(), staffProfileId)
                    .orElseThrow(() -> new ResourceNotFoundException("Staff assignment not found."));
        }
        if (request.documentFileId() != null
                && !documents.existsByIdTenantIdAndStaffProfileIdAndFileId(
                        actor.tenantId(), staffProfileId, request.documentFileId())) {
            throw new ResourceNotFoundException("Staff document not found.");
        }
        StaffServiceRecord record = new StaffServiceRecord();
        UUID id = UUID.randomUUID();
        record.setId(new StaffServiceRecordId(actor.tenantId(), id));
        record.setStaffProfileId(profile.getStaffProfileId());
        record.setEventType(eventType);
        record.setEffectiveOn(request.effectiveOn());
        record.setDetails(request.details().trim());
        record.setStaffAssignmentId(request.staffAssignmentId());
        record.setDocumentFileId(request.documentFileId());
        record.setCreatedBy(actor.userAccountId());
        record.setUpdatedBy(actor.userAccountId());
        StaffServiceRecord saved = serviceRecords.saveAndFlush(record);
        entityManager.refresh(saved);
        audit.record(new AuditEntry(AuditEventType.STAFF_SERVICE_RECORD_ADDED, "STAFF_RECORD_MANAGE",
                "STAFF_SERVICE_RECORD", id, null, null,
                Map.of("event_type", eventType), 1, "P", "SUCCESS"));
        return StaffServiceRecordResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<StaffServiceRecordResponse> listServiceRecords(RbacPrincipal actor,
            UUID staffProfileId, int limit) {
        validateLimit(limit);
        findScopedProfile(actor, staffProfileId, "STAFF_RECORD_VIEW");
        return serviceRecords.findByIdTenantIdAndStaffProfileIdOrderByEffectiveOnDesc(
                        actor.tenantId(), staffProfileId).stream().limit(limit)
                .map(StaffServiceRecordResponse::from).toList();
    }

    private void recordSensitiveView(RbacPrincipal actor, StaffProfile profile, UUID institutionId) {
        audit.record(new AuditEntry(AuditEventType.SENSITIVE_VIEW, "STAFF_RECORD_VIEW",
                "STAFF_PROFILE", profile.getStaffProfileId(), institutionId, null,
                Map.of("view", "sensitive_staff_fields"), 1, "P", "SUCCESS"));
    }

    private StaffProfile findScopedProfile(RbacPrincipal actor, UUID staffProfileId, String action) {
        StaffProfile profile = staffProfiles.findByIdTenantIdAndIdStaffProfileId(actor.tenantId(), staffProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff record not found."));
        List<StaffAssignment> rows = assignments.findByTenantIdAndStaffProfileIdOrderByEffectiveFromDesc(
                actor.tenantId(), staffProfileId);
        var active = permissions.activeAssignment(actor)
                .orElseThrow(() -> new com.edu360.api.exception.UnauthorizedException("An active staff role is required."));
        if (active.getScopeType() == ScopeType.TENANT) {
            permissions.authorize(actor, action, RbacScope.tenant(), RbacTarget.NONE);
            return profile;
        }
        for (StaffAssignment row : rows) {
            if (!"ACTIVE".equals(row.getStatus())) continue;
            if (active.getScopeType() == ScopeType.INSTITUTION
                    && !active.getInstitutionId().equals(row.getInstitutionId())) continue;
            if (active.getScopeType() == ScopeType.DEPARTMENT
                    && (!active.getInstitutionId().equals(row.getInstitutionId())
                    || !active.getDepartmentId().equals(row.getDepartmentId()))) continue;
            RbacScope scope = activeScope(active.getScopeType(), row.getInstitutionId(), row.getDepartmentId());
            permissions.authorize(actor, action, scope, RbacTarget.NONE);
            return profile;
        }
        throw new ResourceNotFoundException("Staff record not found in the active role scope.");
    }

    private void validateAssignmentScope(RbacPrincipal actor, UUID institutionId, UUID departmentId) {
        if (institutionId == null) {
            if (departmentId != null) throw new BusinessRuleViolationException("VALIDATION_FAILED", "departmentId requires institutionId.");
            permissions.authorize(actor, "STAFF_RECORD_MANAGE", RbacScope.tenant(), RbacTarget.NONE);
            return;
        }
        institutions.findByIdTenantIdAndIdInstitutionId(actor.tenantId(), institutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Institution not found."));
        if (departmentId != null) {
            Department department = departments.findByTenantIdAndDepartmentId(actor.tenantId(), departmentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found."));
            if (!institutionId.equals(department.getInstitutionId()) || !"ACTIVE".equals(department.getStatus())) {
                throw new ResourceNotFoundException("Department not found in the requested institution.");
            }
            permissions.authorize(actor, "STAFF_RECORD_MANAGE",
                    RbacScope.department(institutionId, departmentId), RbacTarget.NONE);
        } else {
            permissions.authorize(actor, "STAFF_RECORD_MANAGE", RbacScope.institution(institutionId), RbacTarget.NONE);
        }
    }

    private void authorizeScope(RbacPrincipal actor, UUID institutionId, UUID departmentId, String action) {
        RbacScope scope = institutionId == null ? RbacScope.tenant()
                : departmentId == null ? RbacScope.institution(institutionId)
                : RbacScope.department(institutionId, departmentId);
        permissions.authorize(actor, action, scope, RbacTarget.NONE);
    }

    private static RbacScope activeScope(ScopeType type, UUID institutionId, UUID departmentId) {
        return switch (type) {
            case TENANT -> RbacScope.tenant();
            case INSTITUTION -> RbacScope.institution(institutionId);
            case DEPARTMENT -> RbacScope.department(institutionId, departmentId);
        };
    }

    private static void validateProfile(String fullName, String email, String mobile, String nationalIdLast4,
            String statusValue, LocalDate joinedOn, LocalDate leftOn) {
        if (fullName == null || fullName.isBlank() || fullName.trim().length() > 150) {
            throw new BusinessRuleViolationException("VALIDATION_FAILED", "fullName must contain 1 to 150 characters.");
        }
        if (email != null && (email.length() < 3 || email.length() > 254 || !email.contains("@"))) {
            throw new BusinessRuleViolationException("VALIDATION_FAILED", "personalEmail is invalid.");
        }
        if (mobile != null && !mobile.matches("^\\+[1-9][0-9]{7,14}$")) {
            throw new BusinessRuleViolationException("VALIDATION_FAILED", "personalMobile must use E.164 format.");
        }
        if (nationalIdLast4 != null && !nationalIdLast4.matches("^[0-9A-Za-z]{4}$")) {
            throw new BusinessRuleViolationException("VALIDATION_FAILED", "nationalIdLast4 must contain exactly four characters.");
        }
        String status = statusValue == null ? "ACTIVE" : statusValue.trim().toUpperCase();
        if (!EMPLOYMENT_STATUSES.contains(status)) {
            throw new BusinessRuleViolationException("VALIDATION_FAILED", "employmentStatus is invalid.");
        }
        if ("LEFT".equals(status) && leftOn == null) {
            throw new BusinessRuleViolationException("VALIDATION_FAILED", "leftOn is required when employmentStatus is LEFT.");
        }
        if (joinedOn != null && leftOn != null && leftOn.isBefore(joinedOn)) {
            throw new BusinessRuleViolationException("VALIDATION_FAILED", "leftOn cannot precede joinedOn.");
        }
    }

    private static int parseIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new BusinessRuleViolationException("IF_MATCH_REQUIRED", "If-Match is required.");
        }
        String value = ifMatch.trim().replace("W/", "").replace("\"", "");
        try { return Integer.parseInt(value); }
        catch (NumberFormatException ex) {
            throw new BusinessRuleViolationException("VALIDATION_FAILED", "If-Match must contain the current numeric ETag.");
        }
    }

    private static String normalizeQuery(String query) {
        if (query == null || query.isBlank()) return null;
        String value = query.trim();
        if (value.length() > 100) throw new BusinessRuleViolationException("INVALID_QUERY", "Search query is too long.");
        return value;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void validateLimit(int limit) {
        if (limit < 1 || limit > 200) {
            throw new BusinessRuleViolationException("VALIDATION_FAILED", "limit must be between 1 and 200.");
        }
    }
}

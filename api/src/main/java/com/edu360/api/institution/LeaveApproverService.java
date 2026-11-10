package com.edu360.api.institution;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.identity.UserAccountId;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.institution.dto.GoLiveCheckResponse;
import com.edu360.api.institution.dto.GoLiveCheckResponse.GoLiveBlockerItem;
import com.edu360.api.institution.dto.LeaveApproverSetRequest;
import com.edu360.api.institution.dto.LeaveApproverSettingResponse;
import com.edu360.api.rbac.AssignmentStatus;
import com.edu360.api.rbac.RoleDefinition;
import com.edu360.api.rbac.RoleDefinitionId;
import com.edu360.api.rbac.RoleDefinitionRepository;
import com.edu360.api.rbac.UserRoleAssignment;
import com.edu360.api.rbac.UserRoleAssignmentRepository;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaveApproverService {

    private final LeaveApproverSettingRepository leaveApproverSettingRepository;
    private final InstitutionRepository institutionRepository;
    private final TenantRepository tenantRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final RoleDefinitionRepository roleDefinitionRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public LeaveApproverSettingResponse setPrincipalLeaveApprover(
            UUID tenantId,
            UUID institutionId,
            LeaveApproverSetRequest request,
            UUID actorId,
            String clientIp) {

        Institution institution = institutionRepository.findById(new InstitutionId(tenantId, institutionId))
                .orElseThrow(() -> new ResourceNotFoundException("Institution not found with id: " + institutionId));

        // Approver user must exist in the tenant
        if (!userAccountRepository.existsById(new UserAccountId(tenantId, request.approverUserId()))) {
            throw new ResourceNotFoundException("Approver user account not found: " + request.approverUserId());
        }

        // Rule D-002: The Principal is never their own leave approver
        List<UserRoleAssignment> activeAssignments = userRoleAssignmentRepository
                .findByIdTenantIdAndUserAccountIdAndStatus(tenantId, request.approverUserId(), AssignmentStatus.ACTIVE);
        for (UserRoleAssignment assignment : activeAssignments) {
            if (institutionId.equals(assignment.getInstitutionId())) {
                Optional<RoleDefinition> roleDefOpt = roleDefinitionRepository
                        .findById(new RoleDefinitionId(tenantId, assignment.getRoleDefinitionId()));
                if (roleDefOpt.isPresent()) {
                    String roleCode = roleDefOpt.get().getRoleCode();
                    if ("PRINCIPAL_COLLEGE".equalsIgnoreCase(roleCode) || "SCHOOL_PRINCIPAL".equalsIgnoreCase(roleCode)) {
                        throw new BusinessRuleViolationException(
                                "APPROVER_IS_SUBJECT",
                                "The Principal cannot be designated as their own leave approver [Rule D-002]");
                    }
                }
            }
        }

        // Rule C6: Standalone institutions cannot choose Registrar or Vice-Chancellor
        if (!institution.isUnderUniversity()) {
            if ("REGISTRAR".equalsIgnoreCase(request.approverKind()) || "VICE_CHANCELLOR".equalsIgnoreCase(request.approverKind())) {
                throw new BusinessRuleViolationException(
                        "APPROVER_KIND_MISMATCH",
                        "Standalone institutions can only choose MANAGEMENT or GOVERNING_BODY_APPROVER as leave approver [Rule C6]");
            }
        }

        // Supersede previous active settings for this institution's Principal
        List<LeaveApproverSetting> existingSettings = leaveApproverSettingRepository
                .findByIdTenantIdAndSubjectRoleAndInstitutionId(tenantId, "PRINCIPAL", institutionId);
        for (LeaveApproverSetting s : existingSettings) {
            if ("ACTIVE".equalsIgnoreCase(s.getStatus())) {
                s.setStatus("SUPERSEDED");
                s.setUpdatedAt(Instant.now());
                s.setUpdatedBy(actorId);
                leaveApproverSettingRepository.save(s);
            }
        }

        UUID settingId = UUID.randomUUID();
        LeaveApproverSetting newSetting = LeaveApproverSetting.builder()
                .id(new LeaveApproverSettingId(tenantId, settingId))
                .subjectRole("PRINCIPAL")
                .institutionId(institutionId)
                .approverKind(request.approverKind().toUpperCase())
                .approverUserId(request.approverUserId())
                .effectiveFrom(Instant.now())
                .status("ACTIVE")
                .createdAt(Instant.now())
                .createdBy(actorId)
                .updatedAt(Instant.now())
                .updatedBy(actorId)
                .rowVersion(1)
                .build();

        LeaveApproverSetting saved = leaveApproverSettingRepository.save(newSetting);

        auditLogService.record(
                AuditEventType.PRINCIPAL_LEAVE_APPROVER_SET,
                null,
                actorId != null ? actorId.toString() : "SYSTEM",
                clientIp,
                "institutionId=" + institutionId + ", approverKind=" + request.approverKind() + ", approverUserId=" + request.approverUserId()
        );

        log.info("Set Principal leave approver for institution {} in tenant {} to {} (kind={})",
                institutionId, tenantId, request.approverUserId(), request.approverKind());

        return LeaveApproverSettingResponse.fromEntity(saved);
    }

    @Transactional
    public LeaveApproverSettingResponse setVcLeaveApprover(
            UUID tenantId,
            LeaveApproverSetRequest request,
            UUID actorId,
            String clientIp) {

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with id: " + tenantId));

        if (!"UNIVERSITY_GROUP".equalsIgnoreCase(tenant.getTenantType())) {
            throw new BusinessRuleViolationException(
                    "INVALID_TENANT_TYPE",
                    "Vice-Chancellor leave approver is only applicable to university groups [Rule C6]");
        }

        // Approver user must exist in the tenant
        if (!userAccountRepository.existsById(new UserAccountId(tenantId, request.approverUserId()))) {
            throw new ResourceNotFoundException("Approver user account not found: " + request.approverUserId());
        }

        // Rule C6 & ck_leave_approver_setting_r2: Vice-Chancellor approver must be MANAGEMENT or GOVERNING_BODY_APPROVER
        String kind = request.approverKind().toUpperCase();
        if (!"MANAGEMENT".equals(kind) && !"GOVERNING_BODY_APPROVER".equals(kind)) {
            throw new BusinessRuleViolationException(
                    "APPROVER_KIND_MISMATCH",
                    "Vice-Chancellor leave approver must be MANAGEMENT or GOVERNING_BODY_APPROVER [Rule C6]");
        }

        // VC cannot be their own leave approver
        List<UserRoleAssignment> activeAssignments = userRoleAssignmentRepository
                .findByIdTenantIdAndUserAccountIdAndStatus(tenantId, request.approverUserId(), AssignmentStatus.ACTIVE);
        for (UserRoleAssignment assignment : activeAssignments) {
            Optional<RoleDefinition> roleDefOpt = roleDefinitionRepository
                    .findById(new RoleDefinitionId(tenantId, assignment.getRoleDefinitionId()));
            if (roleDefOpt.isPresent() && "VICE_CHANCELLOR".equalsIgnoreCase(roleDefOpt.get().getRoleCode())) {
                throw new BusinessRuleViolationException(
                        "APPROVER_IS_SUBJECT",
                        "The Vice-Chancellor cannot be designated as their own leave approver [Rule D-002]");
            }
        }

        // Supersede previous active VC leave approvers
        List<LeaveApproverSetting> existingSettings = leaveApproverSettingRepository
                .findByIdTenantIdAndSubjectRole(tenantId, "VICE_CHANCELLOR");
        for (LeaveApproverSetting s : existingSettings) {
            if ("ACTIVE".equalsIgnoreCase(s.getStatus())) {
                s.setStatus("SUPERSEDED");
                s.setUpdatedAt(Instant.now());
                s.setUpdatedBy(actorId);
                leaveApproverSettingRepository.save(s);
            }
        }

        UUID settingId = UUID.randomUUID();
        LeaveApproverSetting newSetting = LeaveApproverSetting.builder()
                .id(new LeaveApproverSettingId(tenantId, settingId))
                .subjectRole("VICE_CHANCELLOR")
                .institutionId(null)
                .approverKind(kind)
                .approverUserId(request.approverUserId())
                .effectiveFrom(Instant.now())
                .status("ACTIVE")
                .createdAt(Instant.now())
                .createdBy(actorId)
                .updatedAt(Instant.now())
                .updatedBy(actorId)
                .rowVersion(1)
                .build();

        LeaveApproverSetting saved = leaveApproverSettingRepository.save(newSetting);

        auditLogService.record(
                AuditEventType.VC_LEAVE_APPROVER_SET,
                null,
                actorId != null ? actorId.toString() : "SYSTEM",
                clientIp,
                "approverKind=" + kind + ", approverUserId=" + request.approverUserId()
        );

        log.info("Set Vice-Chancellor leave approver for tenant {} to {} (kind={})",
                tenantId, request.approverUserId(), kind);

        return LeaveApproverSettingResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public GoLiveCheckResponse getGoLiveCheck(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with id: " + tenantId));

        List<GoLiveBlockerItem> blockers = new ArrayList<>();

        if ("UNIVERSITY_GROUP".equalsIgnoreCase(tenant.getTenantType())) {
            boolean vcApproverExists = leaveApproverSettingRepository
                    .existsByIdTenantIdAndSubjectRoleAndStatus(tenantId, "VICE_CHANCELLOR", "ACTIVE");
            if (!vcApproverExists) {
                blockers.add(new GoLiveBlockerItem("VC_LEAVE_APPROVER_MISSING", null));
            }
        }

        List<Institution> institutions = institutionRepository.findByIdTenantIdAndStatusIn(
                tenantId, List.of("DRAFT", "ACTIVE"));
        for (Institution inst : institutions) {
            boolean principalApproverExists = leaveApproverSettingRepository
                    .existsByIdTenantIdAndSubjectRoleAndInstitutionIdAndStatus(
                            tenantId, "PRINCIPAL", inst.getId().getInstitutionId(), "ACTIVE");
            if (!principalApproverExists) {
                blockers.add(new GoLiveBlockerItem("PRINCIPAL_LEAVE_APPROVER_MISSING", inst.getId().getInstitutionId()));
            }
        }

        return new GoLiveCheckResponse(blockers.isEmpty(), blockers);
    }

    @Transactional(readOnly = true)
    public Optional<LeaveApproverSettingResponse> getPrincipalLeaveApprover(UUID tenantId, UUID institutionId) {
        return leaveApproverSettingRepository
                .findByIdTenantIdAndSubjectRoleAndInstitutionIdAndStatus(tenantId, "PRINCIPAL", institutionId, "ACTIVE")
                .map(LeaveApproverSettingResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public Optional<LeaveApproverSettingResponse> getVcLeaveApprover(UUID tenantId) {
        return leaveApproverSettingRepository
                .findByIdTenantIdAndSubjectRoleAndStatus(tenantId, "VICE_CHANCELLOR", "ACTIVE")
                .map(LeaveApproverSettingResponse::fromEntity);
    }
}

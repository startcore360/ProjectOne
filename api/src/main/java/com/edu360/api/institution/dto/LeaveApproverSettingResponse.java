package com.edu360.api.institution.dto;

import java.time.Instant;
import java.util.UUID;

import com.edu360.api.institution.LeaveApproverSetting;

public record LeaveApproverSettingResponse(
        UUID leaveApproverSettingId,
        UUID tenantId,
        String subjectRole,
        UUID institutionId,
        String approverKind,
        UUID approverUserId,
        String status,
        Instant effectiveFrom,
        Integer rowVersion
) {
    public static LeaveApproverSettingResponse fromEntity(LeaveApproverSetting setting) {
        return new LeaveApproverSettingResponse(
                setting.getId().getLeaveApproverSettingId(),
                setting.getId().getTenantId(),
                setting.getSubjectRole(),
                setting.getInstitutionId(),
                setting.getApproverKind(),
                setting.getApproverUserId(),
                setting.getStatus(),
                setting.getEffectiveFrom(),
                setting.getRowVersion()
        );
    }
}

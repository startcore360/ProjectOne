package com.edu360.api.selfservice;

import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.selfservice.dto.LeaveBalanceDto;
import com.edu360.api.selfservice.dto.LeaveRequestDto;
import com.edu360.api.selfservice.dto.NoticeDto;
import com.edu360.api.selfservice.dto.NotificationDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SelfServiceService {

    private final PermissionEvaluatorService permissionEvaluatorService;
    private final UserNotificationRepository notificationRepository;
    private final UserLeaveBalanceRepository leaveBalanceRepository;
    private final UserLeaveRequestRepository leaveRequestRepository;
    private final NoticeRepository noticeRepository;

    @Transactional(readOnly = true)
    public List<NotificationDto> getNotifications(UUID tenantId, UUID userId) {
        authorizeSelfService(tenantId, userId);
        return notificationRepository.findByIdTenantIdAndRecipientUserIdOrderByCreatedAtDesc(tenantId, userId)
                .stream()
                .map(n -> new NotificationDto(
                        n.getId().getNotificationId(),
                        n.getTriggerCode(),
                        n.getTitle(),
                        n.getBody(),
                        n.getPriorityClass(),
                        n.getCreatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceDto> getLeaveBalances(UUID tenantId, UUID userId) {
        authorizeSelfService(tenantId, userId);
        return leaveBalanceRepository.findByIdTenantIdAndStaffProfileId(tenantId, userId)
                .stream()
                .map(b -> new LeaveBalanceDto(
                        b.getId().getLeaveBalanceId(),
                        b.getLeaveTypeId(),
                        b.getLeaveYear(),
                        b.getCreditedDays(),
                        b.getUsedDays(),
                        b.getCreditedDays().subtract(b.getUsedDays() != null ? b.getUsedDays() : BigDecimal.ZERO)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestDto> getLeaveRequests(UUID tenantId, UUID userId) {
        authorizeSelfService(tenantId, userId);
        return leaveRequestRepository.findByIdTenantIdAndRequestedByUserIdOrderByCreatedAtDesc(tenantId, userId)
                .stream()
                .map(r -> new LeaveRequestDto(
                        r.getId().getLeaveRequestId(),
                        r.getLeaveTypeId(),
                        r.getUnit(),
                        r.getStartDate(),
                        r.getEndDate(),
                        r.getReason(),
                        r.getDaysCounted(),
                        r.getProcessCode(),
                        r.getStatus(),
                        r.getCreatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NoticeDto> getNotices(UUID tenantId, UUID userId) {
        authorizeSelfService(tenantId, userId);
        return noticeRepository.findByIdTenantIdAndStatusOrderByCreatedAtDesc(tenantId, "PUBLISHED")
                .stream()
                .map(n -> new NoticeDto(
                        n.getId().getNoticeId(),
                        n.getNoticeLevel(),
                        n.getNoticeKind(),
                        n.getTitle(),
                        n.getBody(),
                        n.getAuthoredActiveRole(),
                        n.getPublishedAt()
                ))
                .toList();
    }

    private void authorizeSelfService(UUID tenantId, UUID userId) {
        permissionEvaluatorService.authorize(
                new RbacPrincipal(tenantId, userId),
                "SELF_SERVICE_VIEW",
                RbacScope.tenant(),
                RbacTarget.NONE
        );
    }
}

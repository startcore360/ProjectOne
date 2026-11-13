package com.edu360.api.selfservice;

import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.selfservice.dto.LeaveBalanceDto;
import com.edu360.api.selfservice.dto.LeaveRequestDto;
import com.edu360.api.selfservice.dto.NoticeDto;
import com.edu360.api.selfservice.dto.NotificationDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import com.edu360.api.live.LiveEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
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
    private LiveEventPublisher liveEvents;

    @Autowired(required = false)
    public void setLiveEventPublisher(LiveEventPublisher liveEvents) { this.liveEvents = liveEvents; }

    @Transactional(readOnly = true)
    public List<NotificationDto> getNotifications(UUID tenantId, UUID userId) {
        return getNotifications(tenantId, userId, 50);
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> getNotifications(UUID tenantId, UUID userId, int limit) {
        authorizeSelfService(tenantId, userId);
        if (limit < 1 || limit > 200) {
            throw new com.edu360.api.exception.BusinessRuleViolationException(
                    "VALIDATION_FAILED", "Notification limit must be between 1 and 200.");
        }
        return notificationRepository.findDeliverable(tenantId, userId, Instant.now(), PageRequest.of(0, limit))
                .stream()
                .map(n -> new NotificationDto(
                        n.getId().getNotificationId(),
                        n.getTriggerCode(),
                        n.getTitle(),
                        n.getBody(),
                        n.getPriorityClass(),
                        n.getCreatedAt(),
                        n.getReadAt(),
                        n.getEntityType(),
                        n.getEntityId()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public com.edu360.api.selfservice.dto.NotificationSummaryDto getNotificationSummary(UUID tenantId, UUID userId) {
        authorizeSelfService(tenantId, userId);
        return new com.edu360.api.selfservice.dto.NotificationSummaryDto(
                notificationRepository.countUnreadDeliverable(tenantId, userId, Instant.now()));
    }

    @Transactional
    public void markNotificationRead(UUID tenantId, UUID userId, UUID notificationId) {
        authorizeSelfService(tenantId, userId);
        UserNotification notification = notificationRepository
                .findByIdTenantIdAndIdNotificationIdAndRecipientUserId(tenantId, notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (notification.getDeliverAfter() != null && notification.getDeliverAfter().isAfter(Instant.now())) {
            throw new ResourceNotFoundException("Notification not found");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notification.setUpdatedBy(userId);
            notificationRepository.save(notification);
        }
        publishUnreadCount(tenantId, userId);
    }

    @Transactional
    public void markAllNotificationsRead(UUID tenantId, UUID userId) {
        authorizeSelfService(tenantId, userId);
        notificationRepository.markAllDeliveredRead(tenantId, userId, Instant.now());
        publishUnreadCount(tenantId, userId);
    }

    private void publishUnreadCount(UUID tenantId, UUID userId) {
        if (liveEvents != null) {
            long unread = notificationRepository.countUnreadDeliverable(tenantId, userId, Instant.now());
            liveEvents.publishToUser(tenantId, userId, "UNREAD_COUNT", java.util.Map.of("unreadCount", unread));
        }
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

    /**
     * A-08: SELF_SERVICE_VIEW ("Self" in D2) is checked at the user's own active scope - a
     * Teacher at their institution, an HOD at their department - never as a tenant-wide check
     * that only tenant-level roles could pass.
     */
    private void authorizeSelfService(UUID tenantId, UUID userId) {
        RbacPrincipal self = new RbacPrincipal(tenantId, userId);
        RbacScope own = java.util.Objects.requireNonNullElse(permissionEvaluatorService.ownScope(self), RbacScope.tenant());
        permissionEvaluatorService.authorize(self, "SELF_SERVICE_VIEW", own, RbacTarget.NONE);
    }
}

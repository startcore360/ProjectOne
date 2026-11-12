package com.edu360.api.selfservice;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.selfservice.dto.NotificationDto;
import com.edu360.api.selfservice.dto.NotificationSummaryDto;

@Service
public class NotificationService {
    private static final int MAX_LIMIT = 100;
    private final UserNotificationRepository notifications;

    public NotificationService(UserNotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> list(UUID tenantId, UUID userId, int limit) {
        validateLimit(limit);
        Instant now = Instant.now();
        return notifications.findDeliverable(tenantId, userId, now, PageRequest.of(0, limit))
                .stream().map(NotificationService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public NotificationSummaryDto summary(UUID tenantId, UUID userId) {
        return new NotificationSummaryDto(
                notifications.countUnreadDeliverable(tenantId, userId, Instant.now()));
    }

    @Transactional
    public void markRead(UUID tenantId, UUID userId, UUID notificationId) {
        UserNotification notification = notifications
                .findByIdTenantIdAndIdNotificationIdAndRecipientUserId(tenantId, notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found."));
        if (notification.getDeliverAfter() != null && notification.getDeliverAfter().isAfter(Instant.now())) {
            throw new ResourceNotFoundException("Notification not found.");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notification.setUpdatedBy(userId);
            notifications.save(notification);
        }
    }

    @Transactional
    public int markAllRead(UUID tenantId, UUID userId) {
        return notifications.markAllDeliveredRead(tenantId, userId, Instant.now());
    }

    private static NotificationDto toDto(UserNotification notification) {
        return new NotificationDto(notification.getId().getNotificationId(),
                notification.getTriggerCode(), notification.getTitle(), notification.getBody(),
                notification.getPriorityClass(), notification.getCreatedAt(), notification.getReadAt(),
                notification.getEntityType(), notification.getEntityId());
    }

    private static void validateLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new BusinessRuleViolationException("VALIDATION_FAILED",
                    "limit must be between 1 and " + MAX_LIMIT + ".");
        }
    }
}

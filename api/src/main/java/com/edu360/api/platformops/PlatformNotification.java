package com.edu360.api.platformops;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Platform-user notification. Tenant notifications remain in app.notification. */
@Entity
@Table(schema = "platform", name = "platform_notification")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformNotification {
    @Id
    @Column(name = "platform_notification_id", nullable = false)
    private UUID platformNotificationId;

    @Column(name = "recipient_platform_user_id", nullable = false)
    private UUID recipientPlatformUserId;

    @Column(name = "trigger_code", nullable = false)
    private String triggerCode;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "body")
    private String body;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "priority_class", nullable = false)
    private String priorityClass;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "row_version", nullable = false)
    private Integer rowVersion;
}

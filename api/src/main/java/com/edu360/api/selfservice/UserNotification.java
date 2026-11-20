package com.edu360.api.selfservice;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notification", schema = "app")
@Getter
@Setter
@NoArgsConstructor
public class UserNotification {
    @EmbeddedId
    private UserNotificationId id;
    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;
    @Column(name = "institution_id")
    private UUID institutionId;
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
    @Column(name = "notice_id")
    private UUID noticeId;
    @Column(name = "created_by_user_id")
    private UUID createdByUserId;
    @Column(name = "priority_class", nullable = false)
    private String priorityClass;
    @Column(name = "deliver_after")
    private Instant deliverAfter;
    @Column(name = "dedupe_key")
    private String dedupeKey;
    @Column(name = "read_at")
    private Instant readAt;
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;
    @Column(name = "row_version", insertable = false, updatable = false)
    private Integer rowVersion;

    /** Backward-compatible constructor used by existing self-service notification fixtures. */
    @Deprecated
    public UserNotification(UUID tenantId, UUID notificationId, UUID recipientUserId,
            String triggerCode, String title, String body, String priorityClass) {
        this(tenantId, notificationId, recipientUserId, null, triggerCode, title, body,
                null, null, recipientUserId, priorityClass, null);
        this.createdAt = Instant.now();
    }

    public UserNotification(UUID tenantId, UUID notificationId, UUID recipientUserId,
            UUID institutionId, String triggerCode, String title, String body,
            String entityType, UUID entityId, UUID actorUserId, String priorityClass, String dedupeKey) {
        this.id = new UserNotificationId(tenantId, notificationId);
        this.recipientUserId = recipientUserId;
        this.institutionId = institutionId;
        this.triggerCode = triggerCode;
        this.title = title;
        this.body = body;
        this.entityType = entityType;
        this.entityId = entityId;
        this.createdByUserId = actorUserId;
        this.priorityClass = priorityClass == null ? "NORMAL" : priorityClass;
        this.dedupeKey = dedupeKey;
        this.createdBy = actorUserId;
        this.updatedBy = actorUserId;
    }
}

package com.edu360.api.selfservice;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
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

    @Column(name = "priority_class", nullable = false)
    private String priorityClass;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UserNotification(UUID tenantId, UUID notificationId, UUID recipientUserId,
                            String triggerCode, String title, String body, String priorityClass) {
        this.id = new UserNotificationId(tenantId, notificationId);
        this.recipientUserId = recipientUserId;
        this.triggerCode = triggerCode;
        this.title = title;
        this.body = body;
        this.priorityClass = priorityClass != null ? priorityClass : "NORMAL";
        this.createdAt = Instant.now();
    }
}

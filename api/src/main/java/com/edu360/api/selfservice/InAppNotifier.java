package com.edu360.api.selfservice;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import com.edu360.api.live.LiveEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class InAppNotifier {
    private static final Logger log = LoggerFactory.getLogger(InAppNotifier.class);
    private final UserNotificationRepository repository;
    private final TransactionTemplate isolatedTransaction;
    private LiveEventPublisher liveEvents;

    @Autowired(required = false)
    public void setLiveEventPublisher(LiveEventPublisher liveEvents) { this.liveEvents = liveEvents; }

    public InAppNotifier(UserNotificationRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.isolatedTransaction = new TransactionTemplate(transactionManager);
        this.isolatedTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Best-effort in-app delivery in an independent transaction. */
    public void notify(UUID tenantId, UUID recipientUserId, UUID institutionId,
            String triggerCode, String title, String body, String entityType,
            UUID entityId, UUID actorUserId, String priorityClass, String dedupeKey) {
        try {
            AtomicReference<UserNotification> created = new AtomicReference<>();
            isolatedTransaction.executeWithoutResult(status -> {
                if (dedupeKey != null && repository.findFirstByIdTenantIdAndDedupeKey(tenantId, dedupeKey).isPresent()) return;
                created.set(repository.save(new UserNotification(tenantId, UUID.randomUUID(), recipientUserId,
                        institutionId, triggerCode, title, body, entityType, entityId,
                        actorUserId, priorityClass, dedupeKey)));
            });
            UserNotification notification = created.get();
            if (notification != null && liveEvents != null) {
                liveEvents.publishToUser(tenantId, recipientUserId, "NOTIFICATION_CREATED", Map.of(
                        "notificationId", notification.getId().getNotificationId().toString(),
                        "triggerCode", triggerCode, "title", title == null ? "" : title,
                        "body", body == null ? "" : body,
                        "priorityClass", priorityClass == null ? "NORMAL" : priorityClass,
                        "entityType", entityType == null ? "" : entityType,
                        "entityId", entityId == null ? "" : entityId.toString(),
                        "createdAt", Instant.now().toString()));
                long unread = repository.countByIdTenantIdAndRecipientUserIdAndReadAtIsNull(tenantId, recipientUserId);
                liveEvents.publishToUser(tenantId, recipientUserId, "UNREAD_COUNT", Map.of("unreadCount", unread));
            }
        } catch (RuntimeException ex) {
            log.warn("In-app notification failed; business operation remains independent [tenant={}, trigger={}]",
                    tenantId, triggerCode, ex);
        }
    }
}

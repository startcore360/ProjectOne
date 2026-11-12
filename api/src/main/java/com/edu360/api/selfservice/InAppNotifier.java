package com.edu360.api.selfservice;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class InAppNotifier {
    private static final Logger log = LoggerFactory.getLogger(InAppNotifier.class);
    private final UserNotificationRepository repository;
    private final TransactionTemplate isolatedTransaction;

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
            isolatedTransaction.executeWithoutResult(status -> {
                if (dedupeKey != null && repository.findFirstByIdTenantIdAndDedupeKey(tenantId, dedupeKey).isPresent()) return;
                repository.save(new UserNotification(tenantId, UUID.randomUUID(), recipientUserId,
                        institutionId, triggerCode, title, body, entityType, entityId,
                        actorUserId, priorityClass, dedupeKey));
            });
        } catch (RuntimeException ex) {
            log.warn("In-app notification failed; business operation remains independent [tenant={}, trigger={}]",
                    tenantId, triggerCode, ex);
        }
    }
}

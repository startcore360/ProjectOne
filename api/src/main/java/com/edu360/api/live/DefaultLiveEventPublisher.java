package com.edu360.api.live;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class DefaultLiveEventPublisher implements LiveEventPublisher {
    private final LiveSocketHandler sockets;

    public DefaultLiveEventPublisher(LiveSocketHandler sockets) {
        this.sockets = sockets;
    }

    @Override
    public void publishToUser(UUID tenantId, UUID userId, String type, Map<String, Object> payload) {
        afterCommit(() -> sockets.publishToUser(tenantId, userId, type, payload));
    }

    @Override
    public void publishEntityChanged(UUID tenantId, List<UUID> authorizedUserIds, String entityType,
            UUID entityId, UUID institutionId, String changeType) {
        afterCommit(() -> sockets.publishEntityChanged(tenantId, authorizedUserIds,
                entityType, entityId, institutionId, changeType));
    }

    private static void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { task.run(); }
            });
        } else {
            task.run();
        }
    }
}

package com.edu360.api.selfservice;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.selfservice.dto.NotificationDto;
import com.edu360.api.selfservice.dto.NotificationSummaryDto;

@RestController
@RequestMapping({"/v1/tenants/{tenantId}/me/notifications",
        "/api/tenants/{tenantId}/me/notifications"})
public class NotificationController {
    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public ResponseEntity<List<NotificationDto>> list(@PathVariable UUID tenantId,
            @RequestParam(defaultValue = "50") int limit, Authentication authentication) {
        TenantPrincipal actor = principal(authentication, tenantId);
        return ResponseEntity.ok(notifications.list(tenantId, actor.userAccountId(), limit));
    }

    @GetMapping("/summary")
    public ResponseEntity<NotificationSummaryDto> summary(@PathVariable UUID tenantId,
            Authentication authentication) {
        TenantPrincipal actor = principal(authentication, tenantId);
        return ResponseEntity.ok(notifications.summary(tenantId, actor.userAccountId()));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID tenantId,
            @PathVariable UUID notificationId, Authentication authentication) {
        TenantPrincipal actor = principal(authentication, tenantId);
        notifications.markRead(tenantId, actor.userAccountId(), notificationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead(@PathVariable UUID tenantId,
            Authentication authentication) {
        TenantPrincipal actor = principal(authentication, tenantId);
        int updated = notifications.markAllRead(tenantId, actor.userAccountId());
        return ResponseEntity.ok(Map.of("updatedCount", updated));
    }

    private static TenantPrincipal principal(Authentication authentication, UUID tenantId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal actor)) {
            throw new UnauthorizedException("Tenant authentication is required.");
        }
        if (!tenantId.equals(actor.tenantId())) {
            throw new ResourceNotFoundException("Notification not found.");
        }
        return actor;
    }
}

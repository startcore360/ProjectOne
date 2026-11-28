package com.edu360.api.platformops;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.UnauthorizedException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/v1/platform/me/notifications", "/api/platform/me/notifications"})
@RequiredArgsConstructor
@Tag(name = "Platform Notifications", description = "Read and manage notifications for the authenticated platform user")
public class PlatformNotificationController {

    private final PlatformNotificationReadService notificationReadService;

    @GetMapping
    @Operation(summary = "List notifications for the authenticated platform user")
    public ResponseEntity<List<PlatformNotificationResponse>> list(Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        return ResponseEntity.ok(notificationReadService.list(principal.platformUserId()));
    }

    @PostMapping("/{notificationId}/read")
    @Operation(summary = "Mark one of the authenticated platform user's notifications as read")
    public ResponseEntity<Void> markRead(@PathVariable UUID notificationId, Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        notificationReadService.markRead(principal.platformUserId(), notificationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all notifications for the authenticated platform user as read")
    public ResponseEntity<Void> markAllRead(Authentication authentication) {
        PlatformPrincipal principal = requirePlatformPrincipal(authentication);
        notificationReadService.markAllRead(principal.platformUserId());
        return ResponseEntity.noContent().build();
    }

    private PlatformPrincipal requirePlatformPrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof PlatformPrincipal principal)) {
            throw new UnauthorizedException("Platform credentials required; no platform principal on this request.");
        }
        return principal;
    }
}

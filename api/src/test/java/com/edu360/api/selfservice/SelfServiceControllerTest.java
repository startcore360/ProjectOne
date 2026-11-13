package com.edu360.api.selfservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.selfservice.dto.LeaveBalanceDto;
import com.edu360.api.selfservice.dto.LeaveRequestDto;
import com.edu360.api.selfservice.dto.NoticeDto;
import com.edu360.api.selfservice.dto.NotificationDto;
import com.edu360.api.identity.UserType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class SelfServiceControllerTest {

    @Mock
    private PermissionEvaluatorService permissionEvaluatorService;
    @Mock
    private UserNotificationRepository notificationRepository;
    @Mock
    private UserLeaveBalanceRepository leaveBalanceRepository;
    @Mock
    private UserLeaveRequestRepository leaveRequestRepository;
    @Mock
    private NoticeRepository noticeRepository;

    private SelfServiceService service;
    private SelfServiceController controller;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private Authentication auth;

    @BeforeEach
    void setUp() {
        service = new SelfServiceService(
                permissionEvaluatorService,
                notificationRepository,
                leaveBalanceRepository,
                leaveRequestRepository,
                noticeRepository
        );
        controller = new SelfServiceController(service);

        TenantPrincipal principal = new TenantPrincipal(
                tenantId,
                userId,
                UserType.STAFF,
                "Prof. Alan Turing",
                false
        );
        auth = new UsernamePasswordAuthenticationToken(principal, null);
    }

    @Test
    @DisplayName("ACT-SELF_SERVICE_VIEW-ALLOW: Authorized staff member views notifications, balances, requests, and notices")
    void ALLOW_staffViewsSelfService() {

        UserNotification notif = new UserNotification(tenantId, UUID.randomUUID(), userId, "SYSTEM", "Hello", "Welcome to EDU360", "NORMAL");
        when(notificationRepository.findDeliverable(eq(tenantId), eq(userId), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(notif));

        UserLeaveBalance balance = new UserLeaveBalance(tenantId, UUID.randomUUID(), userId, UUID.randomUUID(), (short) 2026, BigDecimal.valueOf(20), BigDecimal.valueOf(5));
        when(leaveBalanceRepository.findByIdTenantIdAndStaffProfileId(tenantId, userId))
                .thenReturn(List.of(balance));

        UserLeaveRequest request = new UserLeaveRequest(tenantId, UUID.randomUUID(), userId, UUID.randomUUID(), "DAY", LocalDate.now(), LocalDate.now().plusDays(1), "Conference", BigDecimal.valueOf(2), "STAFF_CASUAL_LEAVE", "APPROVED");
        when(leaveRequestRepository.findByIdTenantIdAndRequestedByUserIdOrderByCreatedAtDesc(tenantId, userId))
                .thenReturn(List.of(request));

        Notice notice = new Notice(tenantId, UUID.randomUUID(), "UNIVERSITY", "GENERAL", "Campus Holiday", "Campus will be closed tomorrow", UUID.randomUUID(), "UNIVERSITY_ADMIN", "ALL", "PUBLISHED");
        when(noticeRepository.findByIdTenantIdAndStatusOrderByCreatedAtDesc(tenantId, "PUBLISHED"))
                .thenReturn(List.of(notice));

        ResponseEntity<List<NotificationDto>> notifRes = controller.getNotifications(tenantId, auth);
        ResponseEntity<List<LeaveBalanceDto>> balRes = controller.getLeaveBalances(tenantId, auth);
        ResponseEntity<List<LeaveRequestDto>> reqRes = controller.getLeaveRequests(tenantId, auth);
        ResponseEntity<List<NoticeDto>> notRes = controller.getNotices(tenantId, auth);

        assertThat(notifRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(notifRes.getBody()).hasSize(1);
        assertThat(balRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(balRes.getBody()).hasSize(1);
        assertThat(reqRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reqRes.getBody()).hasSize(1);
        assertThat(notRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(notRes.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("B9: recipient can mark their own notification read")
    void markOwnNotificationRead() {
        UUID notificationId = UUID.randomUUID();
        UserNotification notification = new UserNotification(tenantId, notificationId, userId,
                "SYSTEM", "Hello", "Welcome", "NORMAL");
        when(notificationRepository.findByIdTenantIdAndIdNotificationIdAndRecipientUserId(tenantId, notificationId, userId))
                .thenReturn(Optional.of(notification));

        service.markNotificationRead(tenantId, userId, notificationId);

        assertThat(notification.getReadAt()).isNotNull();
        verify(notificationRepository).save(notification);
    }

    @Test
    @DisplayName("B9: another recipient notification is not discoverable")
    void cannotMarkAnotherUsersNotificationRead() {
        UUID notificationId = UUID.randomUUID();
        when(notificationRepository.findByIdTenantIdAndIdNotificationIdAndRecipientUserId(tenantId, notificationId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markNotificationRead(tenantId, userId, notificationId))
                .isInstanceOf(com.edu360.api.exception.ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("B9: mark-all-read only persists notifications belonging to the current recipient")
    void markAllOwnNotificationsRead() {
        service.markAllNotificationsRead(tenantId, userId);

        verify(notificationRepository).markAllDeliveredRead(eq(tenantId), eq(userId), any(Instant.class));
    }


    @Test
    @DisplayName("B9: scheduled notifications cannot be read before their delivery time")
    void scheduledNotificationIsNotReadableBeforeDelivery() {
        UUID notificationId = UUID.randomUUID();
        UserNotification notification = new UserNotification(tenantId, notificationId, userId,
                "SYSTEM", "Scheduled", "Not delivered yet", "NORMAL");
        notification.setDeliverAfter(Instant.now().plusSeconds(3600));
        when(notificationRepository.findByIdTenantIdAndIdNotificationIdAndRecipientUserId(
                tenantId, notificationId, userId)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> service.markNotificationRead(tenantId, userId, notificationId))
                .isInstanceOf(com.edu360.api.exception.ResourceNotFoundException.class);
        org.mockito.Mockito.verify(notificationRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("C2-1: student, guardian and staff personas can access their own self-service notifications")
    void C2_1_allTenantPersonasCanAccessSelfService() {
        for (UserType type : List.of(UserType.STUDENT, UserType.GUARDIAN, UserType.STAFF)) {
            TenantPrincipal principal = new TenantPrincipal(tenantId, userId, type, "Self-service user", false);
            Authentication personaAuth = new UsernamePasswordAuthenticationToken(principal, null);
            assertThat(controller.getNotifications(tenantId, personaAuth).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    @DisplayName("ACT-SELF_SERVICE_VIEW-SCOPE: Cross-tenant access is refused with 403 AccessDenied")
    void SCOPE_crossTenantRefused() {
        UUID otherTenant = UUID.randomUUID();
        assertThatThrownBy(() -> controller.getNotifications(otherTenant, auth))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Cross-tenant access forbidden");
    }

    @Test
    @DisplayName("ACT-SELF_SERVICE_VIEW-GUARD: Unauthenticated request is rejected")
    void GUARD_unauthenticatedRefused() {
        assertThatThrownBy(() -> controller.getNotifications(tenantId, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required");
    }
}

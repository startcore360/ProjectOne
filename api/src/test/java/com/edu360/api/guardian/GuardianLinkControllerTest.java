package com.edu360.api.guardian;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.guardian.dto.GuardianChangeDecisionRequest;
import com.edu360.api.guardian.dto.GuardianChangeRequestDto;
import com.edu360.api.guardian.dto.GuardianLinkResponse;
import com.edu360.api.guardian.dto.LinkGuardianRequest;
import com.edu360.api.identity.UserType;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuardianLinkControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("29ba13a8-9f52-431e-a359-2eb227a866f3");
    private static final UUID INSTITUTION_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID STUDENT_ID = UUID.fromString("874cfca3-360a-4298-90f7-66a938c41ec3");
    private static final UUID USER_ID = UUID.fromString("33333333-4444-5555-6666-777777777777");
    private static final UUID LINK_ID = UUID.fromString("a03c7d0b-9141-4fe0-9613-68c65f03738a");

    private StudentGuardianLinkRepository linkRepository;
    private GuardianProfileRepository profileRepository;
    private PermissionEvaluatorService permissionEvaluatorService;
    private AuditLogService auditLogService;

    private GuardianLinkService guardianLinkService;
    private GuardianLinkController controller;

    @BeforeEach
    void setUp() {
        linkRepository = mock(StudentGuardianLinkRepository.class);
        profileRepository = mock(GuardianProfileRepository.class);
        permissionEvaluatorService = mock(PermissionEvaluatorService.class);
        auditLogService = mock(AuditLogService.class);

        guardianLinkService = new GuardianLinkService(
                linkRepository,
                profileRepository,
                permissionEvaluatorService,
                auditLogService
        );

        controller = new GuardianLinkController(guardianLinkService);
    }

    private Authentication authFor(UUID tenantId, UUID userId) {
        TenantPrincipal principal = new TenantPrincipal(tenantId, userId, UserType.STAFF, "admin@college.test", false);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    @Test
    void ALLOW_adminLinksGuardianToStudent() {
        when(linkRepository.countByIdTenantIdAndStudentProfileId(TENANT_ID, STUDENT_ID)).thenReturn(0L);
        when(profileRepository.findByIdTenantIdAndMobile(TENANT_ID, "+919876543210")).thenReturn(Optional.empty());
        when(profileRepository.save(any(GuardianProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(linkRepository.save(any(StudentGuardianLink.class))).thenAnswer(inv -> inv.getArgument(0));

        LinkGuardianRequest request = new LinkGuardianRequest(
                "Ramesh Kumar",
                "+919876543210",
                "ramesh@example.com",
                "FATHER",
                true
        );

        ResponseEntity<GuardianLinkResponse> response = controller.linkGuardian(
                TENANT_ID, STUDENT_ID, INSTITUTION_ID, request, authFor(TENANT_ID, USER_ID));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("PENDING_ACTIVATION", response.getBody().status());
        assertTrue(response.getBody().isPrimary());
        assertEquals("FATHER", response.getBody().relationship());

        verify(auditLogService).record(eq(AuditEventType.GUARDIAN_LINKED), any(), any(), any(), any());
    }

    @Test
    void GUARD_exceedingMaxThreeGuardiansIsProhibited() {
        when(linkRepository.countByIdTenantIdAndStudentProfileId(TENANT_ID, STUDENT_ID)).thenReturn(3L);

        LinkGuardianRequest request = new LinkGuardianRequest(
                "Suresh Kumar",
                "+919876543211",
                null,
                "LEGAL_GUARDIAN",
                false
        );

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class, () ->
                controller.linkGuardian(TENANT_ID, STUDENT_ID, INSTITUTION_ID, request, authFor(TENANT_ID, USER_ID)));

        assertEquals("MAX_GUARDIANS_EXCEEDED", ex.getErrorCode());
    }

    @Test
    void ALLOW_adminRequestsChangeAndPrincipalApproves() {
        StudentGuardianLink link = StudentGuardianLink.builder()
                .id(new StudentGuardianLinkId(TENANT_ID, LINK_ID))
                .studentProfileId(STUDENT_ID)
                .guardianProfileId(UUID.randomUUID())
                .relationship("FATHER")
                .isPrimary(true)
                .status("ACTIVE")
                .rowVersion(1)
                .build();

        when(linkRepository.findByIdTenantIdAndIdStudentGuardianLinkId(TENANT_ID, LINK_ID))
                .thenReturn(Optional.of(link));
        when(linkRepository.save(any(StudentGuardianLink.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1. Request Change
        GuardianChangeRequestDto changeReq = new GuardianChangeRequestDto("Court order updating custody arrangement");
        ResponseEntity<GuardianLinkResponse> reqResponse = controller.requestChange(
                TENANT_ID, LINK_ID, INSTITUTION_ID, changeReq, authFor(TENANT_ID, USER_ID));

        assertEquals(HttpStatus.OK, reqResponse.getStatusCode());
        assertNotNull(reqResponse.getBody().changeApprovalRequestId());
        assertEquals("Court order updating custody arrangement", reqResponse.getBody().blockReason());
        verify(auditLogService).record(eq(AuditEventType.GUARDIAN_CHANGE_REQUESTED), any(), any(), any(), any());

        // 2. Principal Approves: old guardian loses access at once (BLOCKED)
        GuardianChangeDecisionRequest decisionReq = new GuardianChangeDecisionRequest(true, "Custody order verified");
        ResponseEntity<GuardianLinkResponse> decisionResponse = controller.decideChange(
                TENANT_ID, LINK_ID, INSTITUTION_ID, decisionReq, authFor(TENANT_ID, USER_ID));

        assertEquals(HttpStatus.OK, decisionResponse.getStatusCode());
        assertEquals("BLOCKED", decisionResponse.getBody().status());
        assertNotNull(decisionResponse.getBody().blockedAt());
        verify(auditLogService).record(eq(AuditEventType.GUARDIAN_CHANGE_APPROVED), any(), any(), any(), any());
    }

    @Test
    void GUARD_changeRequestWithoutReasonIsProhibited() {
        GuardianChangeRequestDto changeReq = new GuardianChangeRequestDto("   ");

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class, () ->
                controller.requestChange(TENANT_ID, LINK_ID, INSTITUTION_ID, changeReq, authFor(TENANT_ID, USER_ID)));

        assertEquals("REASON_REQUIRED", ex.getErrorCode());
    }

    @Test
    void DENY_userWithoutPermissionFails() {
        doThrow(new UnauthorizedException("Action GUARDIAN_LINK not granted"))
                .when(permissionEvaluatorService).authorize(
                        any(RbacPrincipal.class), eq("GUARDIAN_LINK"), any(RbacScope.class), any(RbacTarget.class));

        LinkGuardianRequest request = new LinkGuardianRequest(
                "Ramesh Kumar",
                "+919876543210",
                null,
                "FATHER",
                true
        );

        assertThrows(UnauthorizedException.class, () ->
                controller.linkGuardian(TENANT_ID, STUDENT_ID, INSTITUTION_ID, request, authFor(TENANT_ID, USER_ID)));
    }

    @Test
    void SCOPE_tenantMismatchRejected() {
        UUID otherTenant = UUID.randomUUID();
        LinkGuardianRequest request = new LinkGuardianRequest(
                "Ramesh Kumar",
                "+919876543210",
                null,
                "FATHER",
                true
        );

        assertThrows(UnauthorizedException.class, () ->
                controller.linkGuardian(otherTenant, STUDENT_ID, INSTITUTION_ID, request, authFor(TENANT_ID, USER_ID)));
    }
}

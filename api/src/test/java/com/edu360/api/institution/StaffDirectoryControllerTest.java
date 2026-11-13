package com.edu360.api.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.UserType;
import com.edu360.api.rbac.RbacPrincipal;

class StaffDirectoryControllerTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final TenantPrincipal tenantPrincipal =
            new TenantPrincipal(tenantId, userId, UserType.STAFF, "Staff", false);
    private final Authentication authentication = mock(Authentication.class);
    private StaffDirectoryService service;
    private StaffDirectoryController controller;

    @BeforeEach
    void setUp() {
        service = mock(StaffDirectoryService.class);
        controller = new StaffDirectoryController(service);
        when(authentication.getPrincipal()).thenReturn(tenantPrincipal);
    }

    @Test
    void updateForwardsIfMatchAndReturnsUpdatedVersionEtag() {
        StaffProfileUpdateRequest request = new StaffProfileUpdateRequest(
                null, "Updated name", null, null, null, null, null, null, null, null);
        StaffProfileResponse response = new StaffProfileResponse(
                staffId, null, "SCH-001", "Updated name", null, null, null, null, null,
                "ACTIVE", null, null, null, userId, null, userId, 4);
        when(service.update(any(RbacPrincipal.class), eq(staffId), eq("\"3\""), eq(request)))
                .thenReturn(response);

        var result = controller.update(tenantId, staffId, "\"3\"", request, authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getHeaders().getETag()).isEqualTo("\"4\"");
        verify(service).update(new RbacPrincipal(tenantId, userId), staffId, "\"3\"", request);
    }

    @Test
    void endingAssignmentForwardsIfMatchAndReturnsVersionEtag() {
        EndStaffAssignmentRequest request = new EndStaffAssignmentRequest(LocalDate.of(2026, 11, 12));
        StaffAssignmentResponse response = new StaffAssignmentResponse(
                assignmentId, staffId, UUID.randomUUID(), null, "Teacher",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 12), "ENDED",
                null, userId, null, userId, 7);
        when(service.endAssignment(any(RbacPrincipal.class), eq(staffId), eq(assignmentId),
                eq("\"6\""), eq(request))).thenReturn(response);

        var result = controller.endAssignment(tenantId, staffId, assignmentId,
                "\"6\"", request, authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getHeaders().getETag()).isEqualTo("\"7\"");
        verify(service).endAssignment(new RbacPrincipal(tenantId, userId), staffId,
                assignmentId, "\"6\"", request);
    }

    @Test
    void tenantMismatchDoesNotCallStaffService() {
        UUID otherTenant = UUID.randomUUID();

        assertThatThrownBy(() -> controller.get(otherTenant, staffId, authentication))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(service, never()).get(any(), any());
    }

    @Test
    void missingTenantAuthenticationIsRejected() {
        assertThatThrownBy(() -> controller.list(tenantId, null, 50, null))
                .isInstanceOf(UnauthorizedException.class);
        verify(service, never()).list(any(), any(), anyInt());
    }
}

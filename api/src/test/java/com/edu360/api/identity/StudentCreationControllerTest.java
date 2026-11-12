package com.edu360.api.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.UserType;

class StudentCreationControllerTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID institutionId = UUID.randomUUID();
    private final UUID sectionId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final TenantPrincipal actor =
            new TenantPrincipal(tenantId, userId, UserType.STAFF, "Registrar", false);
    private final Authentication authentication = mock(Authentication.class);
    private StudentCreationService service;
    private StudentCreationController controller;

    @BeforeEach
    void setUp() {
        service = mock(StudentCreationService.class);
        controller = new StudentCreationController(service);
        when(authentication.getPrincipal()).thenReturn(actor);
    }

    @Test
    void createReturnsCreatedStudentAndVersionEtag() {
        StudentCreateRequest request = new StudentCreateRequest(institutionId, "A Student",
                null, null, sectionId, sessionId, null);
        StudentProfileResponse response = new StudentProfileResponse(UUID.randomUUID(), null,
                institutionId, "COL-2026-001", "A Student", null, null, null, null,
                "ACTIVE", null, null, null, userId, 3);
        when(service.create(actor, request)).thenReturn(response);

        var result = controller.create(tenantId, request, authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getHeaders().getETag()).isEqualTo("\"3\"");
        assertThat(result.getBody()).isEqualTo(response);
        verify(service).create(actor, request);
    }

    @Test
    void tenantMismatchDoesNotCallCreationService() {
        assertThatThrownBy(() -> controller.create(UUID.randomUUID(),
                new StudentCreateRequest(institutionId, "A Student", null, null, sectionId, sessionId, null),
                authentication)).isInstanceOf(ResourceNotFoundException.class);
        verify(service, never()).create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingTenantAuthenticationIsRejected() {
        assertThatThrownBy(() -> controller.create(tenantId,
                new StudentCreateRequest(institutionId, "A Student", null, null, sectionId, sessionId, null),
                null)).isInstanceOf(UnauthorizedException.class);
    }
}

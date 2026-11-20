package com.edu360.api.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

class StaffDocumentVerificationControllerTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();
    private final TenantPrincipal tenantPrincipal =
            new TenantPrincipal(tenantId, userId, UserType.STAFF, "Verifier", false);
    private final Authentication authentication = mock(Authentication.class);
    private StaffDocumentService service;
    private StaffDocumentVerificationController controller;

    @BeforeEach
    void setUp() {
        service = mock(StaffDocumentService.class);
        controller = new StaffDocumentVerificationController(service);
        when(authentication.getPrincipal()).thenReturn(tenantPrincipal);
    }

    @Test
    void verificationPassesDecisionAndRemarkToService() {
        StaffDocumentVerificationRequest request =
                new StaffDocumentVerificationRequest("VERIFIED", "Original checked");
        StaffDocumentResponse response = new StaffDocumentResponse(
                documentId, UUID.randomUUID(), "DEGREE", UUID.randomUUID(),
                "VERIFIED", userId, null, null, UUID.randomUUID(), null, userId, 2);
        when(service.verify(tenantPrincipal, documentId, "VERIFIED", "Original checked"))
                .thenReturn(response);

        var result = controller.verify(tenantId, documentId, request, authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(response);
        verify(service).verify(tenantPrincipal, documentId, "VERIFIED", "Original checked");
    }

    @Test
    void tenantMismatchDoesNotCallVerificationService() {
        assertThatThrownBy(() -> controller.verify(UUID.randomUUID(), documentId,
                new StaffDocumentVerificationRequest("REJECTED", "Mismatch"), authentication))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(service, never()).verify(any(), any(), any(), any());
    }

    @Test
    void missingAuthenticationIsRejected() {
        assertThatThrownBy(() -> controller.verify(tenantId, documentId,
                new StaffDocumentVerificationRequest("VERIFIED", null), null))
                .isInstanceOf(UnauthorizedException.class);
        verify(service, never()).verify(any(), eq(documentId), any(), any());
    }
}

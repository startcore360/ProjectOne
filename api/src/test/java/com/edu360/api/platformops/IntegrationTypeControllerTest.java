package com.edu360.api.platformops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.dto.CreateIntegrationTypeRequest;
import com.edu360.api.platformops.dto.IntegrationTypeResponse;
import com.edu360.api.platformops.dto.UpdateIntegrationTypeRequest;

@ExtendWith(MockitoExtension.class)
class IntegrationTypeControllerTest {

    @Mock private IntegrationTypeRepository integrationTypeRepository;

    private final PlatformPermissionService platformPermissionService = new PlatformPermissionService(com.edu360.api.platformops.PlatformAccessLookup.standardRolesOnly());

    private IntegrationTypeController controller() {
        return new IntegrationTypeController(platformPermissionService, integrationTypeRepository);
    }

    private Authentication authFor(PlatformRole role, UUID platformUserId) {
        PlatformPrincipal principal = new PlatformPrincipal(platformUserId, role, "po@edu360.test", false);
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    private IntegrationType sample() {
        return IntegrationType.builder()
                .integrationTypeId(UUID.randomUUID())
                .integrationTypeCode("SMS_DLT")
                .description("SMS via DLT-registered sender")
                .status("ALLOWED")
                .allowedProviders(List.of("Razorpay SMS", "MSG91"))
                .createdBy(UUID.randomUUID())
                .updatedBy(UUID.randomUUID())
                .build();
    }

    @Test
    void list_returnsCatalogue_forProductOwner() {
        when(integrationTypeRepository.findAllByOrderByIntegrationTypeCode()).thenReturn(List.of(sample()));

        ResponseEntity<List<IntegrationTypeResponse>> response = controller()
                .list(authFor(PlatformRole.PRODUCT_OWNER, UUID.randomUUID()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).integrationTypeCode()).isEqualTo("SMS_DLT");
    }

    @Test
    void list_deniesNonPlatformRole() {
        assertThatThrownBy(() -> controller().list(authFor(PlatformRole.DATA_PROTECTION_OFFICER, UUID.randomUUID())))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void create_savesNewTypeWithAllowedStatus_whenCodeNotTaken() {
        UUID actorId = UUID.randomUUID();
        when(integrationTypeRepository.findByIntegrationTypeCode("RAZORPAY")).thenReturn(Optional.empty());
        when(integrationTypeRepository.save(any(IntegrationType.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateIntegrationTypeRequest request = new CreateIntegrationTypeRequest(
                "RAZORPAY", "Payment gateway integration", List.of("Razorpay"));

        ResponseEntity<IntegrationTypeResponse> response = controller()
                .create(request, authFor(PlatformRole.PLATFORM_ADMIN, actorId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().integrationTypeCode()).isEqualTo("RAZORPAY");
        assertThat(response.getBody().status()).isEqualTo("ALLOWED");
    }

    @Test
    void create_rejectsDuplicateCode() {
        when(integrationTypeRepository.findByIntegrationTypeCode("SMS_DLT")).thenReturn(Optional.of(sample()));

        CreateIntegrationTypeRequest request = new CreateIntegrationTypeRequest(
                "SMS_DLT", "Duplicate", List.of());

        assertThatThrownBy(() -> controller().create(request, authFor(PlatformRole.PRODUCT_OWNER, UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_deniesNonPlatformRole() {
        CreateIntegrationTypeRequest request = new CreateIntegrationTypeRequest("X", "Y", List.of());

        assertThatThrownBy(() -> controller().create(request, authFor(PlatformRole.BILLING_MANAGER, UUID.randomUUID())))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void update_appliesPartialChanges_andKeepsUntouchedFieldsAsIs() {
        IntegrationType existing = sample();
        when(integrationTypeRepository.findById(existing.getIntegrationTypeId())).thenReturn(Optional.of(existing));
        when(integrationTypeRepository.save(any(IntegrationType.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateIntegrationTypeRequest request = new UpdateIntegrationTypeRequest(null, "DISALLOWED", null);

        ResponseEntity<IntegrationTypeResponse> response = controller()
                .update(existing.getIntegrationTypeId(), request, authFor(PlatformRole.PRODUCT_OWNER, UUID.randomUUID()));

        assertThat(response.getBody().status()).isEqualTo("DISALLOWED");
        assertThat(response.getBody().description()).isEqualTo("SMS via DLT-registered sender");
    }

    @Test
    void update_throwsNotFound_forUnknownId() {
        UUID missingId = UUID.randomUUID();
        when(integrationTypeRepository.findById(missingId)).thenReturn(Optional.empty());

        UpdateIntegrationTypeRequest request = new UpdateIntegrationTypeRequest("x", null, null);

        assertThatThrownBy(() -> controller().update(missingId, request, authFor(PlatformRole.PRODUCT_OWNER, UUID.randomUUID())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_deniesNonPlatformRole() {
        UpdateIntegrationTypeRequest request = new UpdateIntegrationTypeRequest("x", null, null);

        assertThatThrownBy(() -> controller()
                .update(UUID.randomUUID(), request, authFor(PlatformRole.SUPPORT_MANAGER, UUID.randomUUID())))
                .isInstanceOf(UnauthorizedException.class);
    }
}

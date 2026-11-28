package com.edu360.api.student;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.UserType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

class StudentDirectoryControllerTest {
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID OTHER_TENANT = UUID.randomUUID();
    private static final UUID STUDENT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private final StudentDirectoryService service = mock(StudentDirectoryService.class);
    private final StudentDirectoryController controller = new StudentDirectoryController(service);

    private Authentication auth(UUID tenant) {
        return new UsernamePasswordAuthenticationToken(
                new TenantPrincipal(tenant, USER, UserType.STAFF, "Test user", false), null, List.of());
    }

    @BeforeEach
    void resetService() { reset(service); }

    @Test
    void authorizedUpdateDelegatesValidatedProfileToService() {
        StudentProfileUpdateRequest request = new StudentProfileUpdateRequest(
                "Asha Rao", null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
        when(service.update(TENANT, (TenantPrincipal) auth(TENANT).getPrincipal(), STUDENT, request))
                .thenReturn(Map.of("full_name", "Asha Rao"));

        Map<String,Object> result = controller.update(TENANT, STUDENT, request, auth(TENANT));

        assertEquals("Asha Rao", result.get("full_name"));
        verify(service).update(eq(TENANT), any(TenantPrincipal.class), eq(STUDENT), same(request));
    }

    @Test
    void missingAuthenticationIsRejectedBeforeServiceInvocation() {
        assertThrows(UnauthorizedException.class, () -> controller.update(TENANT, STUDENT, emptyRequest(), null));
        verifyNoInteractions(service);
    }

    @Test
    void mismatchedTenantPrincipalIsRejectedBeforeServiceInvocation() {
        assertThrows(UnauthorizedException.class,
                () -> controller.update(TENANT, STUDENT, emptyRequest(), auth(OTHER_TENANT)));
        verifyNoInteractions(service);
    }

    @Test
    void missingStudentRecordEditPermissionIsRejectedByServiceAuthorization() {
        when(service.update(eq(TENANT), any(), eq(STUDENT), any()))
                .thenThrow(new UnauthorizedException("STUDENT_RECORD_EDIT denied"));

        assertThrows(UnauthorizedException.class,
                () -> controller.update(TENANT, STUDENT, emptyRequest(), auth(TENANT)));
    }

    @Test
    void statusChangeThroughProfileEndpointReturnsRequiredActionCode() {
        when(service.update(eq(TENANT), any(), eq(STUDENT), any()))
                .thenThrow(new BusinessRuleViolationException("STATUS_CHANGE_REQUIRED", "Use status-change action"));

        BusinessRuleViolationException error = assertThrows(BusinessRuleViolationException.class,
                () -> controller.update(TENANT, STUDENT, emptyRequestWithStatus(), auth(TENANT)));
        assertEquals("STATUS_CHANGE_REQUIRED", error.getErrorCode());
    }

    @Test
    void beanValidationRejectsProfileFieldsOverTheirDeclaredLimits() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        StudentProfileUpdateRequest invalid = new StudentProfileUpdateRequest(
                "x".repeat(151), null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
        assertTrue(validator.validate(invalid).stream().anyMatch(v -> v.getPropertyPath().toString().equals("fullName")));
    }

    @Test
    void profileRequestDoesNotExposeProtectedIdentityOrStatusMetadataFields() {
        List<String> componentNames = java.util.Arrays.stream(StudentProfileUpdateRequest.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList();
        assertFalse(componentNames.contains("studentProfileId"));
        assertFalse(componentNames.contains("tenantId"));
        assertFalse(componentNames.contains("studentCode"));
        assertFalse(componentNames.contains("statusChangedAt"));
        assertFalse(componentNames.contains("institutionId"));
    }

    private static StudentProfileUpdateRequest emptyRequest() {
        return new StudentProfileUpdateRequest(null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null);
    }
    private static StudentProfileUpdateRequest emptyRequestWithStatus() {
        return new StudentProfileUpdateRequest(null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,"WITHDRAWN","reason");
    }
}

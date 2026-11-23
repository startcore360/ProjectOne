package com.edu360.api.student;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.UserType;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

class StudentGuardianPreferencesControllerTest {
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID OTHER_TENANT = UUID.randomUUID();
    private static final UUID STUDENT = UUID.randomUUID();
    private static final UUID OTHER_STUDENT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID INSTITUTION = UUID.randomUUID();
    private static final UUID LINK = UUID.randomUUID();

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final PermissionEvaluatorService permissions = mock(PermissionEvaluatorService.class);
    private final StudentGuardianPreferencesController controller =
            new StudentGuardianPreferencesController(jdbc, permissions);

    private Authentication auth(UUID tenant) {
        return new UsernamePasswordAuthenticationToken(
                new TenantPrincipal(tenant, USER, UserType.STAFF, "Test user", false), null, List.of());
    }

    @BeforeEach
    void setUp() {
        reset(jdbc, permissions);
        when(jdbc.queryForMap(anyString(), anyMap())).thenReturn(Map.of("institution_id", INSTITUTION));
    }

    @Test
    void authorizedListingReturnsOnlyRowsFromNonBlockedQueryAndChecksInstitutionScope() {
        List<Map<String,Object>> rows = List.of(Map.of("studentGuardianLinkId", LINK, "primary", true));
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(rows);

        assertEquals(rows, controller.list(TENANT, STUDENT, auth(TENANT)));

        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), anyMap());
        assertTrue(sql.getValue().contains("status<>'BLOCKED'"));
        verifyGuardianLinkPermission();
    }

    @Test
    void tenantMismatchIsDeniedBeforeDatabaseOrPermissionLookup() {
        assertThrows(UnauthorizedException.class, () -> controller.list(TENANT, STUDENT, auth(OTHER_TENANT)));
        verifyNoInteractions(jdbc, permissions);
    }

    @Test
    void authorizedPatchUpdatesPreferencesAndPerformsPrimaryHandOffInOneTransaction() throws Exception {
        Map<String,Object> saved = Map.of("studentGuardianLinkId", LINK, "primary", true);
        when(jdbc.queryForMap(anyString(), anyMap())).thenReturn(Map.of("institution_id", INSTITUTION), saved);
        when(jdbc.update(anyString(), anyMap())).thenReturn(1, 1);

        assertEquals(saved, controller.update(TENANT, STUDENT, LINK,
                new StudentGuardianPreferencesController.Preferences(true, true, true), auth(TENANT)));

        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(2)).update(sql.capture(), anyMap());
        assertTrue(sql.getAllValues().get(0).contains("SET is_primary=false"));
        assertTrue(sql.getAllValues().get(1).contains("is_primary=:primary"));
        assertTrue(StudentGuardianPreferencesController.class
                .getMethod("update", UUID.class, UUID.class, UUID.class,
                        StudentGuardianPreferencesController.Preferences.class, Authentication.class)
                .isAnnotationPresent(Transactional.class));
        verifyGuardianLinkPermission();
    }

    @Test
    void wrongStudentOrMissingGuardianLinkReturnsNotFound() {
        when(jdbc.update(anyString(), anyMap())).thenReturn(0);
        ResponseStatusException missing = assertThrows(ResponseStatusException.class,
                () -> controller.update(TENANT, STUDENT, LINK,
                        new StudentGuardianPreferencesController.Preferences(true, false, false), auth(TENANT)));
        assertEquals(404, missing.getStatusCode().value());
    }

    @Test
    void failedPrimaryPromotionCannotCommitAfterClearingExistingPrimary() {
        when(jdbc.update(anyString(), anyMap())).thenReturn(1, 0);

        ResponseStatusException missing = assertThrows(ResponseStatusException.class,
                () -> controller.update(TENANT, STUDENT, LINK,
                        new StudentGuardianPreferencesController.Preferences(true, false, true), auth(TENANT)));
        assertEquals(404, missing.getStatusCode().value());
        verify(jdbc, times(2)).update(anyString(), anyMap());
        assertTrue(StudentGuardianPreferencesController.class
                .getAnnotation(Transactional.class) == null,
                "transaction boundary is deliberately on the update method, not the whole controller");
        try {
            assertTrue(StudentGuardianPreferencesController.class
                    .getMethod("update", UUID.class, UUID.class, UUID.class,
                            StudentGuardianPreferencesController.Preferences.class, Authentication.class)
                    .isAnnotationPresent(Transactional.class));
        } catch (NoSuchMethodException e) { throw new AssertionError(e); }
    }

    @Test
    void missingPrincipalIsDenied() {
        assertThrows(UnauthorizedException.class, () -> controller.list(TENANT, STUDENT, null));
        verifyNoInteractions(jdbc, permissions);
    }

    private void verifyGuardianLinkPermission() {
        verify(permissions).authorize(
                new RbacPrincipal(TENANT, USER), "GUARDIAN_LINK",
                RbacScope.institution(INSTITUTION), RbacTarget.NONE);
    }
}

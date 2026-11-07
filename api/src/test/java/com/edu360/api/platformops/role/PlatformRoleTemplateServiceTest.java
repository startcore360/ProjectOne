package com.edu360.api.platformops.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.DuplicateResourceException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.role.dto.PlatformRoleTemplateResponse;
import com.edu360.api.platformops.role.dto.SavePlatformRoleTemplateRequest;
import com.edu360.api.rbac.ActionCatalogRepository;
import com.edu360.api.rbac.StandardRoleAction;
import com.edu360.api.rbac.StandardRoleActionRepository;

class PlatformRoleTemplateServiceTest {

    private static final PlatformPrincipal OWNER =
            new PlatformPrincipal(UUID.randomUUID(), PlatformRole.PRODUCT_OWNER, "owner@edu360.test", false);

    private PlatformRoleTemplateRepository templateRepository;
    private PlatformRoleTemplateActionRepository actionRepository;
    private StandardRoleActionRepository standardRepository;
    private PlatformRoleTemplateService service;

    @BeforeEach
    void setUp() {
        templateRepository = mock(PlatformRoleTemplateRepository.class);
        actionRepository = mock(PlatformRoleTemplateActionRepository.class);
        standardRepository = mock(StandardRoleActionRepository.class);
        service = new PlatformRoleTemplateService(templateRepository, actionRepository, standardRepository,
                mock(ActionCatalogRepository.class), mock(PlatformAuditWriter.class));
        when(templateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private PlatformRoleTemplate template(UUID id, boolean standard, boolean locked, String status) {
        PlatformRoleTemplate template = PlatformRoleTemplate.builder()
                .platformRoleTemplateId(id).templateName("Billing Manager").basePlatformRole(PlatformRole.BILLING_MANAGER)
                .standard(standard).locked(locked).status(status).build();
        when(templateRepository.findById(id)).thenReturn(Optional.of(template));
        return template;
    }

    private SavePlatformRoleTemplateRequest request(String... actions) {
        return new SavePlatformRoleTemplateRequest("Billing Manager", "Notes", PlatformRole.BILLING_MANAGER, List.of(actions));
    }

    @Test
    void standardTemplateCanBeFullyEdited() {
        UUID id = UUID.randomUUID();
        template(id, true, false, "ACTIVE");

        PlatformRoleTemplateResponse res = service.updateTemplate(id, request("OVERDUE_FLAG"), OWNER);

        assertThat(res.actionCodes()).containsExactly("OVERDUE_FLAG");
        verify(actionRepository).deleteByPlatformRoleTemplateId(id);
    }

    @Test
    void lockedTemplateCanNotBeChanged() {
        UUID id = UUID.randomUUID();
        template(id, true, true, "ACTIVE");

        assertThatThrownBy(() -> service.updateTemplate(id, request("OVERDUE_FLAG"), OWNER))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("locked");
        verify(actionRepository, never()).deleteByPlatformRoleTemplateId(any());
    }

    @Test
    void archivedTemplateCanNotBeUsed() {
        UUID id = UUID.randomUUID();
        template(id, false, false, "ARCHIVED");

        assertThatThrownBy(() -> service.requireUsable(id)).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void templateCanNotSuggestAForbiddenAction() {
        assertThatThrownBy(() -> service.createTemplate(request("DISCOUNT_APPLY"), OWNER))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(templateRepository, never()).save(any());
    }

    @Test
    void duplicateTemplateNameIsRefused() {
        when(templateRepository.existsByTemplateNameIgnoreCase("Billing Manager")).thenReturn(true);

        assertThatThrownBy(() -> service.createTemplate(request("OVERDUE_FLAG"), OWNER))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void resetRestoresTheStandardActionsWithoutSignInActions() {
        UUID id = UUID.randomUUID();
        template(id, true, false, "ACTIVE");
        StandardRoleAction login = mock(StandardRoleAction.class);
        when(login.getActionCode()).thenReturn("AUTH_LOGIN");
        StandardRoleAction overdue = mock(StandardRoleAction.class);
        when(overdue.getActionCode()).thenReturn("OVERDUE_FLAG");
        when(standardRepository.findByRoleCode("BILLING_MANAGER")).thenReturn(List.of(login, overdue));

        assertThat(service.resetTemplate(id, OWNER).actionCodes()).containsExactly("OVERDUE_FLAG");
    }

    @Test
    void onlyAStandardTemplateCanBeReset() {
        UUID id = UUID.randomUUID();
        template(id, false, false, "ACTIVE");

        assertThatThrownBy(() -> service.resetTemplate(id, OWNER)).isInstanceOf(BusinessRuleViolationException.class);
    }
}

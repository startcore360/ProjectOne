package com.edu360.api.platformops.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.edu360.api.platform.PlatformUserRepository;
import com.edu360.api.platform.PlatformUserStatus;
import com.edu360.api.platformops.role.dto.CreatePlatformRoleRequest;
import com.edu360.api.platformops.role.dto.PlatformRoleResponse;
import com.edu360.api.platformops.role.dto.RoleActionItem;
import com.edu360.api.platformops.role.dto.UpdatePlatformRoleRequest;

class PlatformCustomRoleServiceTest {

    private static final PlatformPrincipal OWNER =
            new PlatformPrincipal(UUID.randomUUID(), PlatformRole.PRODUCT_OWNER, "owner@edu360.test", false);

    private PlatformCustomRoleRepository roleRepository;
    private PlatformCustomRoleActionRepository actionRepository;
    private PlatformRoleTemplateService templateService;
    private PlatformUserRepository userRepository;
    private PlatformCustomRoleService service;

    @BeforeEach
    void setUp() {
        roleRepository = mock(PlatformCustomRoleRepository.class);
        actionRepository = mock(PlatformCustomRoleActionRepository.class);
        templateService = mock(PlatformRoleTemplateService.class);
        userRepository = mock(PlatformUserRepository.class);
        service = new PlatformCustomRoleService(roleRepository, actionRepository, templateService, userRepository,
                mock(PlatformAuditWriter.class));
        when(roleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(actionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private CreatePlatformRoleRequest request(UUID templateId, boolean updateTemplate, RoleActionItem... actions) {
        return new CreatePlatformRoleRequest("Billing helper", PlatformRole.BILLING_MANAGER, templateId, List.of(actions), updateTemplate);
    }

    private PlatformCustomRole role(UUID id, PlatformCustomRoleStatus status) {
        PlatformCustomRole role = PlatformCustomRole.builder()
                .platformRoleId(id).roleName("Billing helper").basePlatformRole(PlatformRole.BILLING_MANAGER).status(status).build();
        when(roleRepository.findById(id)).thenReturn(Optional.of(role));
        return role;
    }

    private PlatformCustomRoleAction action(UUID roleId, String code, AccessMode mode) {
        return PlatformCustomRoleAction.builder().platformRoleActionId(UUID.randomUUID())
                .platformRoleId(roleId).actionCode(code).accessMode(mode).build();
    }

    @Test
    void newRoleStartsAsADraftAndKeepsEachPermissionsMode() {
        PlatformRoleResponse res = service.createRole(request(null, false,
                new RoleActionItem("OVERDUE_FLAG", AccessMode.HANDED_OVER),
                new RoleActionItem("INVOICE_MANAGE", AccessMode.SHARED)), OWNER);

        assertThat(res.status()).isEqualTo(PlatformCustomRoleStatus.DRAFT);
        assertThat(res.actions()).containsExactly(
                new RoleActionItem("INVOICE_MANAGE", AccessMode.SHARED),
                new RoleActionItem("OVERDUE_FLAG", AccessMode.HANDED_OVER));
    }

    @Test
    void templateChangesOnlyWhenAskedAndOnlyForLaterRoles() {
        UUID templateId = UUID.randomUUID();

        service.createRole(request(templateId, false, new RoleActionItem("OVERDUE_FLAG", AccessMode.SHARED)), OWNER);
        verify(templateService, never()).replaceActions(any(), any(), any());

        service = new PlatformCustomRoleService(roleRepository, actionRepository, templateService, userRepository,
                mock(PlatformAuditWriter.class));
        when(roleRepository.existsByRoleNameIgnoreCase(any())).thenReturn(false);
        service.createRole(request(templateId, true, new RoleActionItem("OVERDUE_FLAG", AccessMode.SHARED)), OWNER);
        verify(templateService).replaceActions(eq(templateId), eq(List.of("OVERDUE_FLAG")), eq(OWNER));
    }

    @Test
    void savingToATemplateNeedsATemplate() {
        assertThatThrownBy(() -> service.createRole(request(null, true, new RoleActionItem("OVERDUE_FLAG", AccessMode.SHARED)), OWNER))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void reservedActionIsRefused() {
        assertThatThrownBy(() -> service.createRole(request(null, false, new RoleActionItem("TENANT_DELETION_APPROVE", AccessMode.SHARED)), OWNER))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(roleRepository, never()).save(any());
    }

    @Test
    void customRoleCanNotBeBasedOnTheProductOwner() {
        CreatePlatformRoleRequest request = new CreatePlatformRoleRequest("Second owner", PlatformRole.PRODUCT_OWNER, null, List.of(), false);
        assertThatThrownBy(() -> service.createRole(request, OWNER)).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void duplicateNameIsRefused() {
        when(roleRepository.existsByRoleNameIgnoreCase("Billing helper")).thenReturn(true);
        assertThatThrownBy(() -> service.createRole(request(null, false), OWNER)).isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void duplicatePermissionIsRefused() {
        assertThatThrownBy(() -> service.createRole(request(null, false,
                new RoleActionItem("OVERDUE_FLAG", AccessMode.SHARED),
                new RoleActionItem("OVERDUE_FLAG", AccessMode.HANDED_OVER)), OWNER))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void roleWithNoPermissionsCanNotBeActivated() {
        UUID id = UUID.randomUUID();
        role(id, PlatformCustomRoleStatus.DRAFT);
        when(actionRepository.findByPlatformRoleId(id)).thenReturn(List.of());

        assertThatThrownBy(() -> service.activate(id, OWNER)).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void lifecycleMovesFromDraftToActiveToInactive() {
        UUID id = UUID.randomUUID();
        role(id, PlatformCustomRoleStatus.DRAFT);
        when(actionRepository.findByPlatformRoleId(id)).thenReturn(List.of(action(id, "OVERDUE_FLAG", AccessMode.SHARED)));

        assertThat(service.activate(id, OWNER).status()).isEqualTo(PlatformCustomRoleStatus.ACTIVE);
        assertThat(service.deactivate(id, OWNER).status()).isEqualTo(PlatformCustomRoleStatus.INACTIVE);
    }

    @Test
    void roleWithPeopleInItCanNotBeArchived() {
        UUID id = UUID.randomUUID();
        role(id, PlatformCustomRoleStatus.INACTIVE);
        when(userRepository.countByPlatformRoleIdAndStatus(id, PlatformUserStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> service.archive(id, OWNER)).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void archivedRoleCanNotBeEdited() {
        UUID id = UUID.randomUUID();
        role(id, PlatformCustomRoleStatus.ARCHIVED);

        assertThatThrownBy(() -> service.updateRole(id, new UpdatePlatformRoleRequest("New name", List.of()), OWNER))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void takeBackRemovesOnePermission() {
        UUID id = UUID.randomUUID();
        role(id, PlatformCustomRoleStatus.ACTIVE);
        PlatformCustomRoleAction overdue = action(id, "OVERDUE_FLAG", AccessMode.HANDED_OVER);
        when(actionRepository.findByPlatformRoleId(id))
                .thenReturn(List.of(overdue, action(id, "INVOICE_MANAGE", AccessMode.SHARED)));

        PlatformRoleResponse res = service.takeBack(id, "OVERDUE_FLAG", OWNER);

        verify(actionRepository).delete(overdue);
        assertThat(res.actions()).containsExactly(new RoleActionItem("INVOICE_MANAGE", AccessMode.SHARED));
    }

    @Test
    void lastPermissionOfAnActiveRoleCanNotBeTakenBack() {
        UUID id = UUID.randomUUID();
        role(id, PlatformCustomRoleStatus.ACTIVE);
        when(actionRepository.findByPlatformRoleId(id)).thenReturn(List.of(action(id, "OVERDUE_FLAG", AccessMode.SHARED)));

        assertThatThrownBy(() -> service.takeBack(id, "OVERDUE_FLAG", OWNER)).isInstanceOf(BusinessRuleViolationException.class);
        verify(actionRepository, never()).delete(any());
    }
}

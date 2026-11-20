package com.edu360.api.platformops.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.edu360.api.identity.*;
import com.edu360.api.rbac.*;
import com.edu360.api.selfservice.InAppNotifier;
import com.edu360.api.tenant.*;

class TenantAdminInvoiceNotifierTest {
 private TenantRepository tenantRepository; private RoleDefinitionRepository roleRepository;
 private UserRoleAssignmentRepository assignmentRepository; private UserAccountRepository userRepository;
 private InAppNotifier notifier; private TenantAdminInvoiceNotifier service;
 private UUID tenantId,recipientId,systemId,roleId,invoiceId;
 @BeforeEach void setUp() {
  tenantRepository=mock(TenantRepository.class); roleRepository=mock(RoleDefinitionRepository.class);
  assignmentRepository=mock(UserRoleAssignmentRepository.class); userRepository=mock(UserAccountRepository.class);
  notifier=mock(InAppNotifier.class); service=new TenantAdminInvoiceNotifier(tenantRepository,roleRepository,assignmentRepository,userRepository,notifier);
  tenantId=UUID.randomUUID(); recipientId=UUID.randomUUID(); systemId=UUID.randomUUID(); roleId=UUID.randomUUID(); invoiceId=UUID.randomUUID();
  Tenant tenant=Tenant.builder().tenantId(tenantId).isolationMode(IsolationMode.SHARED).dbConnectionRef(null).build();
  when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
  RoleDefinition role=RoleDefinition.builder().id(new RoleDefinitionId(tenantId,roleId)).roleCode("TENANT_ADMIN")
   .roleLevel(RoleLevel.TENANT).status(RoleDefinitionStatus.ACTIVE).build();
  when(roleRepository.findByIdTenantId(tenantId)).thenReturn(List.of(role));
  UserRoleAssignment assignment=UserRoleAssignment.builder().id(new UserRoleAssignmentId(tenantId,UUID.randomUUID()))
   .userAccountId(recipientId).roleDefinitionId(roleId).scopeType(ScopeType.TENANT).effectiveFrom(Instant.now().minusSeconds(60))
   .status(AssignmentStatus.ACTIVE).build();
  when(assignmentRepository.findByIdTenantIdAndRoleDefinitionIdAndStatus(tenantId,roleId,AssignmentStatus.ACTIVE)).thenReturn(List.of(assignment));
  UserAccount recipient=UserAccount.builder().id(new UserAccountId(tenantId,recipientId)).userType(UserType.STAFF).status(UserAccountStatus.ACTIVE).build();
  UserAccount system=UserAccount.builder().id(new UserAccountId(tenantId,systemId)).userType(UserType.SYSTEM).status(UserAccountStatus.ACTIVE).build();
  when(userRepository.findByIdTenantIdAndStatus(tenantId,UserAccountStatus.ACTIVE)).thenReturn(List.of(recipient,system));
 }
 @AfterEach void clearContext(){TenantContext.clear();}
 @Test void ACT_OVERDUE_NOTIFICATION_ALLOW_notifiesTenantAdminWithSystemActor() {
  Invoice invoice=Invoice.builder().invoiceId(invoiceId).tenantId(tenantId).invoiceNumber("INV-OVERDUE-1").dueAt(LocalDate.now().minusDays(1)).status("SENT").build();
  int count=service.notifyOverdueInvoice(invoice);
  assertThat(count).isEqualTo(1); assertThat(TenantContext.isSet()).isFalse();
  verify(notifier).notify(eq(tenantId),eq(recipientId),isNull(),eq("INVOICE_OVERDUE"),eq("Invoice overdue"),
   contains("INV-OVERDUE-1"),eq("INVOICE"),eq(invoiceId),eq(systemId),eq("NORMAL"),
   eq("invoice-overdue-"+invoiceId+"-"+recipientId));
 }
 @Test void ACT_OVERDUE_NOTIFICATION_SCOPE_excludesInstitutionScopedAssignments() {
  when(assignmentRepository.findByIdTenantIdAndRoleDefinitionIdAndStatus(tenantId,roleId,AssignmentStatus.ACTIVE))
   .thenReturn(List.of(UserRoleAssignment.builder().userAccountId(recipientId).roleDefinitionId(roleId)
    .scopeType(ScopeType.INSTITUTION).effectiveFrom(Instant.now().minusSeconds(60)).status(AssignmentStatus.ACTIVE).build()));
  Invoice invoice=Invoice.builder().invoiceId(invoiceId).tenantId(tenantId).invoiceNumber("INV-OVERDUE-2").build();
  assertThat(service.notifyOverdueInvoice(invoice)).isZero(); verifyNoInteractions(notifier);
 }
}

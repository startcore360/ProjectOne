package com.edu360.api.platformops.billing;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.edu360.api.identity.UserAccount;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.identity.UserAccountStatus;
import com.edu360.api.identity.UserType;
import com.edu360.api.rbac.AssignmentStatus;
import com.edu360.api.rbac.RoleDefinition;
import com.edu360.api.rbac.RoleDefinitionRepository;
import com.edu360.api.rbac.RoleDefinitionStatus;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.rbac.UserRoleAssignment;
import com.edu360.api.rbac.UserRoleAssignmentRepository;
import com.edu360.api.selfservice.InAppNotifier;
import com.edu360.api.tenant.Tenant;
import com.edu360.api.tenant.TenantContext;
import com.edu360.api.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;

/** Routes overdue invoice alerts into the tenant's own notification store. */
@Service
@RequiredArgsConstructor
public class TenantAdminInvoiceNotifier {
 private static final Logger log=LoggerFactory.getLogger(TenantAdminInvoiceNotifier.class);
 private final TenantRepository tenantRepository;
 private final RoleDefinitionRepository roleRepository;
 private final UserRoleAssignmentRepository assignmentRepository;
 private final UserAccountRepository userRepository;
 private final InAppNotifier inAppNotifier;

 public int notifyOverdueInvoice(Invoice invoice) {
  Tenant tenant=tenantRepository.findById(invoice.getTenantId()).orElse(null);
  if(tenant==null) { log.warn("Overdue invoice {} references missing tenant {}",invoice.getInvoiceId(),invoice.getTenantId()); return 0; }
  TenantContext.set(tenant.getTenantId(),tenant.getIsolationMode(),tenant.getDbConnectionRef());
  try {
   List<UserAccount> activeUsers=userRepository.findByIdTenantIdAndStatus(tenant.getTenantId(),UserAccountStatus.ACTIVE);
   UserAccount system=activeUsers.stream().filter(u->u.getUserType()==UserType.SYSTEM).findFirst().orElse(null);
   if(system==null) { log.warn("Tenant {} has no active SYSTEM user; overdue alert not sent",tenant.getTenantId()); return 0; }
   Instant now=Instant.now();
   Set<UUID> adminIds=new LinkedHashSet<>();
   for(RoleDefinition role:roleRepository.findByIdTenantId(tenant.getTenantId())) {
    if(role.getStatus()!=RoleDefinitionStatus.ACTIVE) continue;
    String code=role.getRoleCode();
    boolean admin=role.getRoleLevel()==com.edu360.api.rbac.RoleLevel.TENANT
      || code.endsWith("_ADMIN") || "IT_ADMINISTRATOR".equals(code);
    if(!admin) continue;
    List<UserRoleAssignment> assignments=assignmentRepository.findByIdTenantIdAndRoleDefinitionIdAndStatus(
      tenant.getTenantId(),role.getId().getRoleDefinitionId(),AssignmentStatus.ACTIVE);
    for(UserRoleAssignment assignment:assignments) {
     if(assignment.getScopeType()!=ScopeType.TENANT) continue;
     if(assignment.getEffectiveFrom()!=null&&assignment.getEffectiveFrom().isAfter(now)) continue;
     if(assignment.getEffectiveTo()!=null&&!assignment.getEffectiveTo().isAfter(now)) continue;
     adminIds.add(assignment.getUserAccountId());
    }
   }
   int sent=0;
   for(UserAccount user:activeUsers) {
    UUID recipient=user.getId().getUserAccountId();
    if(user.getUserType()==UserType.SYSTEM||!adminIds.contains(recipient)) continue;
    try {
     inAppNotifier.notify(tenant.getTenantId(),recipient,null,"INVOICE_OVERDUE","Invoice overdue",
       "Invoice "+invoice.getInvoiceNumber()+" is overdue. Please contact your billing administrator.",
       "INVOICE",invoice.getInvoiceId(),system.getId().getUserAccountId(),"NORMAL",
       "invoice-overdue-"+invoice.getInvoiceId()+"-"+recipient);
     sent++;
    } catch(RuntimeException ex) {
     log.warn("Could not notify tenant admin {} for overdue invoice {}",recipient,invoice.getInvoiceId(),ex);
    }
   }
   return sent;
  } finally {
   TenantContext.clear();
  }
 }
}

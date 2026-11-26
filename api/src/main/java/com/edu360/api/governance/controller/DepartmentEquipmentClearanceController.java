package com.edu360.api.governance.controller;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.governance.service.DepartmentEquipmentClearanceService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/v1/tenants/{tenantId}/governance/department-equipment-clearances","/api/tenants/{tenantId}/governance/department-equipment-clearances"})
@RequiredArgsConstructor
public class DepartmentEquipmentClearanceController {
 private final DepartmentEquipmentClearanceService clearances;
 public record RecordRequest(@NotNull UUID institutionId,@NotNull UUID departmentId,@NotNull UUID studentId,@PositiveOrZero int outstandingItems,@Size(max=2000) String notes){}
 @GetMapping
 public List<DepartmentEquipmentClearanceService.Clearance> list(@PathVariable UUID tenantId,@RequestParam UUID institutionId,@RequestParam(required=false) UUID departmentId,Authentication auth){
  return clearances.list(principal(auth,tenantId),institutionId,departmentId);
 }
 @PutMapping
 @ResponseStatus(HttpStatus.OK)
 public DepartmentEquipmentClearanceService.Clearance record(@PathVariable UUID tenantId,@Valid @RequestBody RecordRequest body,Authentication auth){
  return clearances.record(principal(auth,tenantId),body.institutionId(),body.departmentId(),body.studentId(),body.outstandingItems(),body.notes());
 }
 private static TenantPrincipal principal(Authentication auth,UUID tenantId){
  if(auth==null||!(auth.getPrincipal() instanceof TenantPrincipal p))throw new UnauthorizedException("Tenant authentication is required.");
  if(!tenantId.equals(p.tenantId()))throw new ResourceNotFoundException("Clearance record not found.");
  return p;
 }
}

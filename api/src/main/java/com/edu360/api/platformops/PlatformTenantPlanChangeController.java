package com.edu360.api.platformops;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.idempotency.PlatformIdempotencyService;
import com.edu360.api.platformops.dto.ChangeTenantPlanRequest;
import com.edu360.api.platformops.dto.TenantPlanChangeResponse;
import tools.jackson.databind.json.JsonMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
@RestController
@RequestMapping({"/v1/platform/tenants","/api/platform/tenants"})
@RequiredArgsConstructor
public class PlatformTenantPlanChangeController {
 private final PlatformTenantPlanChangeService service; private final PlatformPermissionService permissionService;
 private final PlatformIdempotencyService idempotencyService; private final JsonMapper objectMapper;
 @PostMapping("/{tenantId}/plan-change") @Transactional(transactionManager="platformTransactionManager")
 public ResponseEntity<TenantPlanChangeResponse> changePlan(@PathVariable UUID tenantId,@Valid @RequestBody ChangeTenantPlanRequest request,
  @RequestHeader("Idempotency-Key") String key,Authentication auth) {
  PlatformPrincipal principal=(PlatformPrincipal)auth.getPrincipal(); permissionService.authorize(principal,"TENANT_SETTINGS");
  PlatformIdempotencyService.Claim claim=idempotencyService.claim(principal.platformUserId(),"TENANT_PLAN_CHANGE",key,java.util.Map.of("tenantId",tenantId,"request",request));
  if(claim.replay()) {
   TenantPlanChangeResponse replay=objectMapper.convertValue(claim.responseBody(),TenantPlanChangeResponse.class);
   return ResponseEntity.status(claim.responseStatus()==null?HttpStatus.OK:HttpStatus.valueOf(claim.responseStatus())).body(replay);
  }
  try {
   TenantPlanChangeResponse result=service.changePlan(tenantId,request,principal);
   idempotencyService.complete(principal.platformUserId(),claim.id(),HttpStatus.OK.value(),result);
   return ResponseEntity.ok(result);
  } catch(RuntimeException ex) { idempotencyService.release(principal.platformUserId(),claim.id()); throw ex; }
 }
}

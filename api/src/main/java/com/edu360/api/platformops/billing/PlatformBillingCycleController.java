package com.edu360.api.platformops.billing;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.idempotency.PlatformIdempotencyService;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.billing.dto.BillingCycleResponse;
import com.edu360.api.platformops.billing.dto.CreateBillingCycleRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/v1/platform/billing-cycles","/api/platform/billing-cycles"})
@RequiredArgsConstructor
public class PlatformBillingCycleController {
 private final PlatformBillingCycleService billingCycleService;
 private final PlatformPermissionService permissionService;
 private final PlatformIdempotencyService idempotencyService;
 private final ObjectMapper objectMapper;

 @PostMapping
 @Transactional(transactionManager="platformTransactionManager")
 public ResponseEntity<BillingCycleResponse> createBillingCycle(@Valid @RequestBody CreateBillingCycleRequest request,
  @RequestHeader("Idempotency-Key") String idempotencyKey,Authentication auth) {
  PlatformPrincipal principal=(PlatformPrincipal)auth.getPrincipal();
  permissionService.authorize(principal,"INVOICE_MANAGE");
  PlatformIdempotencyService.Claim claim=idempotencyService.claim(principal.platformUserId(),"BILLING_CYCLE",idempotencyKey,request);
  if(claim.replay()) {
   BillingCycleResponse replay=objectMapper.convertValue(claim.responseBody(),BillingCycleResponse.class);
   return ResponseEntity.status(claim.responseStatus()==null?HttpStatus.OK:HttpStatus.valueOf(claim.responseStatus())).body(replay);
  }
  try {
   BillingCycleResponse result=billingCycleService.createBillingCycle(request,principal);
   idempotencyService.complete(principal.platformUserId(),claim.id(),HttpStatus.OK.value(),result);
   return ResponseEntity.ok(result);
  } catch(RuntimeException ex) {
   idempotencyService.release(principal.platformUserId(),claim.id());
   throw ex;
  }
 }
}

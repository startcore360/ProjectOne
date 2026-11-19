package com.edu360.api.platformops.delegation;
import java.util.List; import org.springframework.http.*; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
import com.edu360.api.config.PlatformPrincipal; import com.edu360.api.idempotency.PlatformIdempotentExecutor; import com.edu360.api.platformops.PlatformPermissionService; import jakarta.validation.Valid; import lombok.RequiredArgsConstructor;
@RestController @RequestMapping({"/v1/platform/delegations","/api/platform/delegations"}) @RequiredArgsConstructor
public class PlatformDelegationController {
 private final PlatformDelegationService delegationService; private final PlatformPermissionService permissionService; private final PlatformIdempotentExecutor idempotentExecutor;
 @GetMapping("/candidates") public ResponseEntity<List<PlatformDelegationCandidate>> candidates(Authentication auth){PlatformPrincipal a=(PlatformPrincipal)auth.getPrincipal();permissionService.authorize(a,"APPROVAL_DELEGATE");return ResponseEntity.ok(delegationService.candidates(a));}
 @PostMapping public ResponseEntity<PlatformDelegationResponse> create(@Valid @RequestBody PlatformDelegationRequest req,@RequestHeader("Idempotency-Key") String key,Authentication auth){PlatformPrincipal a=(PlatformPrincipal)auth.getPrincipal();permissionService.authorize(a,"APPROVAL_DELEGATE");return idempotentExecutor.execute(a.platformUserId(),"createPlatformDelegation",key,req,HttpStatus.CREATED,PlatformDelegationResponse.class,()->delegationService.create(req,a));}
}

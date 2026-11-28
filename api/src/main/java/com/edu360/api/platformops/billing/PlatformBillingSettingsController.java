package com.edu360.api.platformops.billing;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.billing.dto.BillingSettingsResponse;
import com.edu360.api.platformops.billing.dto.UpdateBillingSettingsRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/v1/platform/billing/settings","/api/platform/billing/settings"})
@RequiredArgsConstructor
public class PlatformBillingSettingsController {
 private final PlatformBillingSettingsService service;
 private final PlatformPermissionService permissionService;
 @GetMapping public ResponseEntity<BillingSettingsResponse> getSettings(Authentication auth) {
  PlatformPrincipal principal=(PlatformPrincipal)auth.getPrincipal();
  permissionService.authorize(principal,"INVOICE_MANAGE");
  return ResponseEntity.ok(service.getSettings());
 }
 @PutMapping public ResponseEntity<BillingSettingsResponse> updateSettings(@Valid @RequestBody UpdateBillingSettingsRequest request,Authentication auth) {
  PlatformPrincipal principal=(PlatformPrincipal)auth.getPrincipal();
  permissionService.authorize(principal,"INVOICE_MANAGE");
  return ResponseEntity.ok(service.updateSettings(request,principal));
 }
}

package com.edu360.api.platformops.billing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.billing.dto.BillingSettingsResponse;
import com.edu360.api.platformops.billing.dto.UpdateBillingSettingsRequest;
class PlatformBillingSettingsControllerTest {
 private PlatformBillingSettingsService service; private PlatformPermissionService permissions; private PlatformBillingSettingsController controller;
 @BeforeEach void setUp() {
  service=mock(PlatformBillingSettingsService.class);
  permissions=com.edu360.api.platformops.PlatformTestSupport.permissions(com.edu360.api.platformops.PlatformAccessLookup.standardRolesOnly());
  controller=new PlatformBillingSettingsController(service,permissions);
 }
 private Authentication auth(PlatformRole role) {
  return new UsernamePasswordAuthenticationToken(new PlatformPrincipal(java.util.UUID.randomUUID(),role,"staff@edu360.local",false),null,List.of());
 }
 @Test void ACT_BILLING_SETTINGS_ALLOW_billingManagerCanReadAndUpdate() {
  Authentication billingAuth=auth(PlatformRole.BILLING_MANAGER);
  when(service.getSettings()).thenReturn(new BillingSettingsResponse(15));
  assertThat(controller.getSettings(billingAuth).getBody().invoiceDueDays()).isEqualTo(15);
  when(service.updateSettings(new UpdateBillingSettingsRequest(30),(PlatformPrincipal)billingAuth.getPrincipal())).thenReturn(new BillingSettingsResponse(30));
  var response=controller.updateSettings(new UpdateBillingSettingsRequest(30),billingAuth);
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(response.getBody().invoiceDueDays()).isEqualTo(30);
 }
 @Test void ACT_BILLING_SETTINGS_DENY_supportManagerCannotConfigureBilling() {
  assertThatThrownBy(()->controller.getSettings(auth(PlatformRole.SUPPORT_MANAGER))).isInstanceOf(UnauthorizedException.class);
 }
}

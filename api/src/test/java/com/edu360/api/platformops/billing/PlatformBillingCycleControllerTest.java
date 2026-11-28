package com.edu360.api.platformops.billing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.idempotency.PlatformIdempotencyService;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.billing.dto.BillingCycleResponse;
import com.edu360.api.platformops.billing.dto.CreateBillingCycleRequest;
import tools.jackson.databind.json.JsonMapper;
class PlatformBillingCycleControllerTest {
 private PlatformBillingCycleService service; private PlatformPermissionService permissions; private PlatformIdempotencyService idempotency;
 private PlatformBillingCycleController controller; private final UUID userId=UUID.randomUUID();
 @BeforeEach void setUp() {
  service=mock(PlatformBillingCycleService.class); permissions=com.edu360.api.platformops.PlatformTestSupport.permissions(com.edu360.api.platformops.PlatformAccessLookup.standardRolesOnly());
  idempotency=mock(PlatformIdempotencyService.class); controller=new PlatformBillingCycleController(service,permissions,idempotency,JsonMapper.builder().findAndAddModules().build());
 }
 private Authentication auth(PlatformRole role){return new UsernamePasswordAuthenticationToken(new PlatformPrincipal(userId,role,"billing@edu360.local",false),null,List.of());}
 @Test void ACT_BILLING_CYCLE_ALLOW_billingManager() {
  var request=new CreateBillingCycleRequest(LocalDate.of(2026,11,1),LocalDate.of(2026,11,30));
  when(idempotency.claim(any(),any(),any(),any())).thenReturn(new PlatformIdempotencyService.Claim(UUID.randomUUID(),false,null,null));
  when(service.createBillingCycle(request,(PlatformPrincipal)auth(PlatformRole.BILLING_MANAGER).getPrincipal())).thenReturn(new BillingCycleResponse(List.of(),List.of()));
  ResponseEntity<BillingCycleResponse> response=controller.createBillingCycle(request,"cycle-key-001",auth(PlatformRole.BILLING_MANAGER));
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK); verify(idempotency).complete(any(),any(),org.mockito.ArgumentMatchers.eq(200),any());
 }
 @Test void ACT_BILLING_CYCLE_DENY_supportManager() {
  var request=new CreateBillingCycleRequest(LocalDate.of(2026,11,1),LocalDate.of(2026,11,30));
  assertThatThrownBy(()->controller.createBillingCycle(request,"cycle-key-002",auth(PlatformRole.SUPPORT_MANAGER))).isInstanceOf(UnauthorizedException.class);
  verifyNoInteractions(idempotency);
 }
 @Test void ACT_BILLING_CYCLE_GUARD_replaysCompletedIdempotencyKey() {
  var request=new CreateBillingCycleRequest(LocalDate.of(2026,11,1),LocalDate.of(2026,11,30));
  var body=JsonMapper.builder().findAndAddModules().build().valueToTree(new BillingCycleResponse(List.of(),List.of()));
  when(idempotency.claim(any(),any(),any(),any())).thenReturn(new PlatformIdempotencyService.Claim(UUID.randomUUID(),true,200,body));
  var response=controller.createBillingCycle(request,"cycle-key-003",auth(PlatformRole.BILLING_MANAGER));
  assertThat(response.getBody().created()).isEmpty(); verifyNoInteractions(service);
 }
}

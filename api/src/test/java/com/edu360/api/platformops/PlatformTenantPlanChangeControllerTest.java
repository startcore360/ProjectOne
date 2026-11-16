package com.edu360.api.platformops;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.idempotency.PlatformIdempotencyService;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.dto.*;
import tools.jackson.databind.json.JsonMapper;
class PlatformTenantPlanChangeControllerTest {
 private PlatformTenantPlanChangeService service; private PlatformPermissionService permissions; private PlatformIdempotencyService idempotency;
 private PlatformTenantPlanChangeController controller; private UUID userId=UUID.randomUUID(),tenantId=UUID.randomUUID(),planId=UUID.randomUUID();
 @BeforeEach void setUp() {
  service=mock(PlatformTenantPlanChangeService.class); permissions=com.edu360.api.platformops.PlatformTestSupport.permissions(com.edu360.api.platformops.PlatformAccessLookup.standardRolesOnly());
  idempotency=mock(PlatformIdempotencyService.class); controller=new PlatformTenantPlanChangeController(service,permissions,idempotency,JsonMapper.builder().findAndAddModules().build());
 }
 private Authentication auth(PlatformRole role){return new UsernamePasswordAuthenticationToken(new PlatformPrincipal(userId,role,"owner@edu360.local",false),null,List.of());}
 @Test void ACT_PLAN_CHANGE_ALLOW_productOwnerUsesIdempotencyKey() {
  var request=new ChangeTenantPlanRequest(planId,PlanChangeEffective.RENEWAL);
  when(idempotency.claim(any(),any(),any(),any())).thenReturn(new PlatformIdempotencyService.Claim(UUID.randomUUID(),false,null,null));
  when(service.changePlan(tenantId,request,(PlatformPrincipal)auth(PlatformRole.PRODUCT_OWNER).getPrincipal()))
   .thenReturn(new TenantPlanChangeResponse(tenantId,UUID.randomUUID(),UUID.randomUUID(),planId,PlanChangeEffective.RENEWAL,java.time.LocalDate.now().plusMonths(1),null));
  var response=controller.changePlan(tenantId,request,"plan-change-key-001",auth(PlatformRole.PRODUCT_OWNER));
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK); verify(idempotency).complete(any(),any(),eq(200),any());
 }
 @Test void ACT_PLAN_CHANGE_DENY_billingManager() {
  var request=new ChangeTenantPlanRequest(planId,PlanChangeEffective.RENEWAL);
  assertThatThrownBy(()->controller.changePlan(tenantId,request,"plan-change-key-002",auth(PlatformRole.BILLING_MANAGER))).isInstanceOf(UnauthorizedException.class);
  verifyNoInteractions(idempotency);
 }
}

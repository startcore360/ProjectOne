package com.edu360.api.platformops.dataprotection;
import static org.assertj.core.api.Assertions.assertThatThrownBy; import static org.mockito.Mockito.*;
import java.util.Optional; import java.util.UUID; import org.junit.jupiter.api.Test;
import com.edu360.api.config.*; import com.edu360.api.exception.UnauthorizedException; import com.edu360.api.platform.PlatformRole; import com.edu360.api.platformops.*; import com.edu360.api.platformops.role.PlatformAuditWriter;
class PlatformDelegatedApprovalGuardTest {
 @Test void delegateCannotDecideExportRaisedByDelegator(){UUID delegate=UUID.randomUUID(),delegator=UUID.randomUUID(),id=UUID.randomUUID();ExportRequestRepository exports=mock(ExportRequestRepository.class);PlatformAccessLookup access=mock(PlatformAccessLookup.class);
  ExportRequest r=ExportRequest.builder().exportRequestId(id).tenantId(UUID.randomUUID()).requestedByUserId(delegator).requestedByKind("DATA_PROTECTION_OFFICER").status("REQUESTED").rowVersion(1).build();when(exports.findById(id)).thenReturn(Optional.of(r));when(access.activeDelegationDelegator(delegate)).thenReturn(Optional.of(delegator));
  var service=new PlatformDataProtectionService(exports,mock(DeletionRequestRepository.class),mock(TenantRetentionPolicyRepository.class),mock(RetentionChangeRequestRepository.class),mock(com.edu360.api.tenant.TenantRepository.class),mock(PlatformAuditWriter.class),mock(PlatformNotificationService.class),new PlatformConcurrency(),mock(TenantIssuerAuthenticationManagerResolver.class),access);
  assertThatThrownBy(()->service.decideExportRequest(id,"APPROVED",null,"\"1\"",new PlatformPrincipal(delegate,PlatformRole.DATA_PROTECTION_OFFICER,"delegate@example.test",false))).isInstanceOf(UnauthorizedException.class).hasMessageContaining("delegating DPO");}
}

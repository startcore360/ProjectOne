package com.edu360.api.platformops.delegation;
import static org.assertj.core.api.Assertions.assertThatThrownBy; import static org.mockito.Mockito.*;
import java.time.LocalDate; import java.util.Optional; import java.util.UUID; import org.junit.jupiter.api.Test;
import com.edu360.api.config.PlatformPrincipal; import com.edu360.api.exception.BusinessRuleViolationException; import com.edu360.api.platform.*; import com.edu360.api.platformops.role.PlatformAuditWriter;
class PlatformDelegationServiceTest {
 private final PlatformDelegationRepository repo=mock(PlatformDelegationRepository.class); private final PlatformUserRepository users=mock(PlatformUserRepository.class);
 private final PlatformDelegationService service=new PlatformDelegationService(repo,users,mock(PlatformAuditWriter.class));
 private PlatformUser user(UUID id,PlatformRole role){return PlatformUser.builder().platformUserId(id).platformRole(role).status(PlatformUserStatus.ACTIVE).build();}
 @Test void nonDpoDelegateIsRejected(){UUID actor=UUID.randomUUID(),target=UUID.randomUUID();when(users.findById(target)).thenReturn(Optional.of(user(target,PlatformRole.PLATFORM_ADMIN)));
  assertThatThrownBy(()->service.create(new PlatformDelegationRequest(target,LocalDate.of(2026,12,1),LocalDate.of(2026,12,10)),new PlatformPrincipal(actor,PlatformRole.DATA_PROTECTION_OFFICER,"dpo@example.test",false))).isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("active Data Protection Officer");}
 @Test void activeDelegateCannotDelegateAgain(){UUID actor=UUID.randomUUID();when(repo.existsActiveDelegationForDelegate(eq(actor),any(LocalDate.class))).thenReturn(true);
  assertThatThrownBy(()->service.create(new PlatformDelegationRequest(UUID.randomUUID(),LocalDate.of(2026,12,1),LocalDate.of(2026,12,10)),new PlatformPrincipal(actor,PlatformRole.DATA_PROTECTION_OFFICER,"dpo@example.test",false))).isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("cannot delegate approvals onward");}
 @Test void selfAndInvalidDatesAreRejected(){UUID id=UUID.randomUUID();var a=new PlatformPrincipal(id,PlatformRole.DATA_PROTECTION_OFFICER,"dpo@example.test",false);
  assertThatThrownBy(()->service.create(new PlatformDelegationRequest(id,LocalDate.of(2026,12,1),LocalDate.of(2026,12,10)),a)).isInstanceOf(BusinessRuleViolationException.class);
  assertThatThrownBy(()->service.create(new PlatformDelegationRequest(UUID.randomUUID(),LocalDate.of(2026,12,10),LocalDate.of(2026,12,1)),a)).isInstanceOf(BusinessRuleViolationException.class);}
}

package com.edu360.api.platformops.delegation;
import java.time.*; import java.util.*; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import com.edu360.api.config.PlatformPrincipal; import com.edu360.api.exception.*; import com.edu360.api.platform.*; import com.edu360.api.platformops.role.PlatformAuditWriter; import lombok.RequiredArgsConstructor;
@Service @RequiredArgsConstructor
public class PlatformDelegationService {
 private final PlatformDelegationRepository delegationRepository; private final PlatformUserRepository userRepository; private final PlatformAuditWriter auditWriter;
 @Transactional(readOnly=true) public List<PlatformDelegationCandidate> candidates(PlatformPrincipal actor){return userRepository.findAllByPlatformRoleAndStatus(PlatformRole.DATA_PROTECTION_OFFICER,PlatformUserStatus.ACTIVE).stream().filter(u->!u.getPlatformUserId().equals(actor.platformUserId())).map(u->new PlatformDelegationCandidate(u.getPlatformUserId(),u.getFullName(),u.getEmail())).toList();}
 @Transactional public PlatformDelegationResponse create(PlatformDelegationRequest request,PlatformPrincipal actor){
  if(actor.platformRole()!=PlatformRole.DATA_PROTECTION_OFFICER)throw new UnauthorizedException("Only a Data Protection Officer can delegate DPO approvals.");
  if(actor.platformUserId().equals(request.delegateUserId()))throw new BusinessRuleViolationException("SELF_DELEGATION_PROHIBITED","A DPO cannot delegate to themselves.");
  if(!request.from().isBefore(request.to()))throw new BusinessRuleViolationException("INVALID_DATE_RANGE","The delegation start date must be before its end date.");
  LocalDate today=LocalDate.now(ZoneOffset.UTC);
  if(delegationRepository.existsActiveDelegationForDelegate(actor.platformUserId(),today))throw new BusinessRuleViolationException("REDELEGATION_PROHIBITED","A DPO acting as a delegate cannot delegate approvals onward.");
  var target=userRepository.findById(request.delegateUserId()).orElseThrow(()->new BusinessRuleViolationException("DELEGATE_NOT_DPO","The selected delegate must be an active Data Protection Officer."));
  if(target.getPlatformRole()!=PlatformRole.DATA_PROTECTION_OFFICER||target.getStatus()!=PlatformUserStatus.ACTIVE)throw new BusinessRuleViolationException("DELEGATE_NOT_DPO","The selected delegate must be an active Data Protection Officer.");
  Instant now=Instant.now(); PlatformDelegation saved=delegationRepository.save(PlatformDelegation.builder().delegationId(UUID.randomUUID()).delegatorUserId(actor.platformUserId()).delegateUserId(request.delegateUserId()).fromDate(request.from()).toDate(request.to()).status("ACTIVE").createdAt(now).createdBy(actor.platformUserId()).updatedAt(now).updatedBy(actor.platformUserId()).rowVersion(1).build());
  auditWriter.record(actor,"PLATFORM_APPROVAL_DELEGATED","APPROVAL_DELEGATE","PLATFORM_APPROVAL_DELEGATION",saved.getDelegationId()); return PlatformDelegationResponse.from(saved);
 }
}

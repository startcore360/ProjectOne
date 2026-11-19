package com.edu360.api.platformops.delegation;
import java.time.LocalDate; import java.util.UUID;
public record PlatformDelegationResponse(UUID delegationId,UUID delegatorUserId,UUID delegateUserId,LocalDate from,LocalDate to,String status) {
 static PlatformDelegationResponse from(PlatformDelegation d){return new PlatformDelegationResponse(d.getDelegationId(),d.getDelegatorUserId(),d.getDelegateUserId(),d.getFromDate(),d.getToDate(),d.getStatus());}
}

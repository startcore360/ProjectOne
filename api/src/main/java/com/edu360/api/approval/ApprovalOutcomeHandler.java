package com.edu360.api.approval;
import java.util.UUID;
/** Pluggable final side effect for an approved/rejected business process. */
public interface ApprovalOutcomeHandler {
 boolean supports(ApprovalRequest request);
 void apply(UUID tenantId,ApprovalRequest request,boolean approved,UUID actorUserId);
}

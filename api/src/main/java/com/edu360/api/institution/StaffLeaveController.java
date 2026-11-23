package com.edu360.api.institution;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.identity.UserType;
import com.edu360.api.selfservice.StaffLeaveService;
import com.edu360.api.selfservice.dto.LeaveBalanceCreditRequest;
import com.edu360.api.selfservice.dto.StaffLeaveBalanceResponse;
import com.edu360.api.selfservice.dto.StaffLeaveTypeResponse;
import com.edu360.api.selfservice.dto.StaffLeaveTypeSetRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class StaffLeaveController {
    private final StaffLeaveService staffLeaveService;

    @GetMapping({"/v1/tenants/{tenantId}/institutions/{institutionId}/leave-types",
                 "/api/tenants/{tenantId}/institutions/{institutionId}/leave-types"})
    public ResponseEntity<List<StaffLeaveTypeResponse>> getInstitutionLeaveTypes(
            @PathVariable UUID tenantId, @PathVariable UUID institutionId, Authentication authentication) {
        TenantPrincipal principal = principal(authentication, tenantId);
        return ResponseEntity.ok(staffLeaveService.institutionLeaveTypes(
                tenantId, principal.userAccountId(), institutionId));
    }

    @PostMapping({"/v1/tenants/{tenantId}/institutions/{institutionId}/leave-types",
                  "/api/tenants/{tenantId}/institutions/{institutionId}/leave-types"})
    public ResponseEntity<StaffLeaveTypeResponse> createInstitutionLeaveType(
            @PathVariable UUID tenantId, @PathVariable UUID institutionId,
            @RequestBody StaffLeaveTypeSetRequest request, Authentication authentication) {
        TenantPrincipal principal = principal(authentication, tenantId);
        return ResponseEntity.status(201).body(staffLeaveService.createLeaveType(
                tenantId, principal.userAccountId(), institutionId, request));
    }

    @GetMapping({"/v1/tenants/{tenantId}/staff/{staffProfileId}/leave-balances",
                 "/api/tenants/{tenantId}/staff/{staffProfileId}/leave-balances"})
    public ResponseEntity<List<StaffLeaveBalanceResponse>> getBalances(
            @PathVariable UUID tenantId, @PathVariable UUID staffProfileId, Authentication authentication) {
        TenantPrincipal principal = principal(authentication, tenantId);
        return ResponseEntity.ok(staffLeaveService.balancesForActor(tenantId, principal.userAccountId(), staffProfileId));
    }

    @PostMapping({"/v1/tenants/{tenantId}/staff/{staffProfileId}/leave-balances",
                  "/api/tenants/{tenantId}/staff/{staffProfileId}/leave-balances"})
    public ResponseEntity<StaffLeaveBalanceResponse> creditBalance(
            @PathVariable UUID tenantId, @PathVariable UUID staffProfileId,
            @RequestBody LeaveBalanceCreditRequest request, Authentication authentication) {
        TenantPrincipal principal = principal(authentication, tenantId);
        return ResponseEntity.status(201).body(
                staffLeaveService.creditBalance(tenantId, principal.userAccountId(), staffProfileId, request));
    }

    @PostMapping({"/v1/tenants/{tenantId}/staff-leave/requests/{leaveRequestId}/confirm-loss-of-pay",
                  "/api/tenants/{tenantId}/staff-leave/requests/{leaveRequestId}/confirm-loss-of-pay"})
    public ResponseEntity<Void> confirmLossOfPay(
            @PathVariable UUID tenantId, @PathVariable UUID leaveRequestId, Authentication authentication) {
        TenantPrincipal principal = principal(authentication, tenantId);
        staffLeaveService.confirmLossOfPay(tenantId, principal.userAccountId(), leaveRequestId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping({"/v1/tenants/{tenantId}/staff-leave/requests/{leaveRequestId}/cancel-final",
                  "/api/tenants/{tenantId}/staff-leave/requests/{leaveRequestId}/cancel-final"})
    public ResponseEntity<Void> cancelByFinalApprover(
            @PathVariable UUID tenantId, @PathVariable UUID leaveRequestId, Authentication authentication) {
        TenantPrincipal principal = principal(authentication, tenantId);
        staffLeaveService.cancelByFinalApprover(tenantId, principal.userAccountId(), leaveRequestId);
        return ResponseEntity.noContent().build();
    }

    private static TenantPrincipal principal(Authentication authentication, UUID tenantId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal p)) {
            throw new AccessDeniedException("Authentication required");
        }
        if (!tenantId.equals(p.tenantId())) throw new AccessDeniedException("Cross-tenant access forbidden");
        if (p.userType() != UserType.STAFF) throw new AccessDeniedException("Staff access required");
        return p;
    }
}

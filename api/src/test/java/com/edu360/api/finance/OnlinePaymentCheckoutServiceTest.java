package com.edu360.api.finance;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.identity.UserType;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class OnlinePaymentCheckoutServiceTest {
    @Mock private NamedParameterJdbcTemplate jdbc;
    @Mock private PermissionEvaluatorService permissions;
    @Mock private PaymentGatewayCredentialsResolver credentialsResolver;
    @Mock private AuditLogService audit;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID profileId = UUID.randomUUID();
    private final UUID institutionId = UUID.randomUUID();
    private final UUID installmentId = UUID.randomUUID();
    private final TenantPrincipal actor = new TenantPrincipal(
            tenantId, actorId, UserType.STUDENT, "Student", false);

    @BeforeEach
    void setUp() {
        when(permissions.ownScope(any(RbacPrincipal.class))).thenReturn(RbacScope.institution(institutionId));
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Boolean.class))).thenReturn(true);
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FROM app.student_fee_account")) {
                return Map.of("status", "ACTIVE", "studentProfileId", profileId);
            }
            if (sql.contains("FROM app.payment WHERE")) return null;
            throw new AssertionError("Unexpected result-set query: " + sql);
        }).when(jdbc).query(anyString(), any(MapSqlParameterSource.class), any(ResultSetExtractor.class));
    }

    @Test
    void refusesSecondCheckoutWhenInstallmentHasAnUnexpiredPendingPayment() {
        Instant expiresAt = Instant.now().plusSeconds(300);
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FROM app.student_installment")) {
                return List.of(new OnlinePaymentCheckoutService.Installment(
                        installmentId, institutionId, new BigDecimal("1200.00"), "UNPAID"));
            }
            if (sql.contains("FROM app.payment p")) {
                return List.of(new OnlinePaymentCheckoutService.PendingPayment(UUID.randomUUID(), expiresAt));
            }
            throw new AssertionError("Unexpected row-mapper query: " + sql);
        }).when(jdbc).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));

        OnlinePaymentCheckoutService service = new OnlinePaymentCheckoutService(
                jdbc, permissions, credentialsResolver, audit);
        OnlinePaymentCheckoutService.CheckoutRequest request = new OnlinePaymentCheckoutService.CheckoutRequest(
                accountId, List.of(installmentId), "RAZORPAY", "request-key-123");

        assertThatThrownBy(() -> service.start(tenantId, actor, request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("pending checkout");

        verify(credentialsResolver, never()).resolve(tenantId, institutionId);
        verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void doesNotTreatLocalExpiryAsProofThatProviderCheckoutCannotBeCaptured() {
        Instant locallyExpiredAt = Instant.now().minusSeconds(30);
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FROM app.student_installment")) {
                return List.of(new OnlinePaymentCheckoutService.Installment(
                        installmentId, institutionId, new BigDecimal("1200.00"), "UNPAID"));
            }
            if (sql.contains("FROM app.payment p")) {
                return List.of(new OnlinePaymentCheckoutService.PendingPayment(UUID.randomUUID(), locallyExpiredAt));
            }
            throw new AssertionError("Unexpected row-mapper query: " + sql);
        }).when(jdbc).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));

        OnlinePaymentCheckoutService service = new OnlinePaymentCheckoutService(
                jdbc, permissions, credentialsResolver, audit);
        OnlinePaymentCheckoutService.CheckoutRequest request = new OnlinePaymentCheckoutService.CheckoutRequest(
                accountId, List.of(installmentId), "RAZORPAY", "request-key-456");

        assertThatThrownBy(() -> service.start(tenantId, actor, request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("provider's final notification");

        verify(credentialsResolver, never()).resolve(tenantId, institutionId);
        verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class));
    }
}

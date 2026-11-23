package com.edu360.api.admissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.edu360.api.admissions.service.AdmissionService;
import com.edu360.api.admissions.service.MeritCalculationService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.identity.StudentCreationService;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.fasterxml.jackson.databind.ObjectMapper;

class AdmissionPaymentEventHandlerTest {
    private final JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final PermissionEvaluatorService permissions = org.mockito.Mockito.mock(PermissionEvaluatorService.class);
    private final StudentCreationService students = org.mockito.Mockito.mock(StudentCreationService.class);
    private AdmissionService service;

    private static final UUID TENANT = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID APPLICATION = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID CYCLE = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        service = new AdmissionService(jdbc, new ObjectMapper(), permissions, students, new MeritCalculationService());
    }

    @Test
    void verifiedGatewayEventConfirmsOnlyMatchingPendingFee() {
        Map<String,Object> app = new HashMap<>();
        app.put("cycle_id", CYCLE);
        app.put("fee_status", "PENDING");
        app.put("application_fee_event_id", null);
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(app), List.of(Map.of("application_fee", new BigDecimal("500.00"))));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        service.handleVerifiedApplicationFeeEvent(TENANT, APPLICATION, "evt-123", "pay-456", new BigDecimal("500.00"));

        verify(jdbc).update(anyString(), any(Object[].class));
    }

    @Test
    void rejectsAmountMismatchWithoutChangingFeeState() {
        Map<String,Object> app = new HashMap<>();
        app.put("cycle_id", CYCLE);
        app.put("fee_status", "PENDING");
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(app), List.of(Map.of("application_fee", new BigDecimal("500.00"))));

        assertThatThrownBy(() -> service.handleVerifiedApplicationFeeEvent(
                TENANT, APPLICATION, "evt-123", "pay-456", new BigDecimal("499.00")))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("does not match");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void sameEventIdWithDifferentPaymentReferenceIsRejected() {
        Map<String,Object> app = new HashMap<>();
        app.put("cycle_id", CYCLE);
        app.put("fee_status", "CONFIRMED");
        app.put("application_fee_event_id", "evt-123");
        app.put("application_fee_reference", "pay-original");
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(app), List.of(Map.of("application_fee", new BigDecimal("500.00"))));

        assertThatThrownBy(() -> service.handleVerifiedApplicationFeeEvent(
                TENANT, APPLICATION, "evt-123", "pay-substituted", new BigDecimal("500.00")))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("different payment event");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void duplicateDeliveryOfTheSameVerifiedEventIsIdempotent() {
        Map<String,Object> app = new HashMap<>();
        app.put("cycle_id", CYCLE);
        app.put("fee_status", "CONFIRMED");
        app.put("application_fee_event_id", "evt-123");
        app.put("application_fee_reference", "pay-456");
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(app), List.of(Map.of("application_fee", new BigDecimal("500.00"))));

        service.handleVerifiedApplicationFeeEvent(TENANT, APPLICATION, "evt-123", "pay-456", new BigDecimal("500.00"));

        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}

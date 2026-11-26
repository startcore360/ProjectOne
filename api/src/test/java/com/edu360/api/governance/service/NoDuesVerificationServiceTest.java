package com.edu360.api.governance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.edu360.api.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class NoDuesVerificationServiceTest {
    @Test
    void libraryClearanceUsesExactT10FiveColumnViewContract() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        UUID tenant=UUID.randomUUID(), institution=UUID.randomUUID(), student=UUID.randomUUID(), user=UUID.randomUUID();
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<ResultSetExtractor<Map<String,Object>>>any(),
                eq(tenant),eq(student))).thenReturn(Map.of("studentId",student,"institutionId",institution,"userAccountId",user.toString()));
        when(jdbc.queryForObject(eq("SELECT to_regclass(?) IS NOT NULL"),eq(Boolean.class),eq("app.v_fee_invoice"))).thenReturn(true);
        when(jdbc.queryForList(contains("FROM app.v_fee_invoice"),eq(tenant),eq(student))).thenReturn(List.of());
        when(jdbc.queryForList(contains("FROM app.v_student_library_dues_flag"),eq(tenant),eq(institution),eq(user)))
                .thenReturn(List.of(Map.of("tenant_id",tenant,"institution_id",institution,"student_id",user,
                        "has_library_dues",true,"outstanding_fine_balance",new BigDecimal("12.50"))));

        try(MockedStatic<TenantContext> context=mockStatic(TenantContext.class)) {
            context.when(TenantContext::currentTenantId).thenReturn(tenant);
            NoDuesVerificationService service=new NoDuesVerificationService(jdbc);
            NoDuesVerificationService.ClearanceResult result=service.verifyClearance(student);
            assertThat(result.clear()).isFalse();
            assertThat(result.items()).anySatisfy(item -> {
                assertThat(item.source()).isEqualTo("LIBRARY");
                assertThat(item.outstandingAmount()).isEqualByComparingTo("12.50");
            });
        }
    }

    @Test
    void missingLibraryActivityRowDoesNotInventLibraryDues() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        UUID tenant=UUID.randomUUID(), institution=UUID.randomUUID(), student=UUID.randomUUID(), user=UUID.randomUUID();
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<ResultSetExtractor<Map<String,Object>>>any(),
                eq(tenant),eq(student))).thenReturn(Map.of("studentId",student,"institutionId",institution,"userAccountId",user.toString()));
        when(jdbc.queryForObject(eq("SELECT to_regclass(?) IS NOT NULL"),eq(Boolean.class),eq("app.v_fee_invoice"))).thenReturn(true);
        when(jdbc.queryForList(contains("FROM app.v_fee_invoice"),eq(tenant),eq(student))).thenReturn(List.of());
        when(jdbc.queryForList(contains("FROM app.v_student_library_dues_flag"),eq(tenant),eq(institution),eq(user))).thenReturn(List.of());

        try(MockedStatic<TenantContext> context=mockStatic(TenantContext.class)) {
            context.when(TenantContext::currentTenantId).thenReturn(tenant);
            NoDuesVerificationService service=new NoDuesVerificationService(jdbc);
            NoDuesVerificationService.ClearanceResult result=service.verifyClearance(student);
            assertThat(result.clear()).isTrue();
            assertThat(result.items()).noneMatch(item -> item.source().equals("LIBRARY"));
        }
    }
}

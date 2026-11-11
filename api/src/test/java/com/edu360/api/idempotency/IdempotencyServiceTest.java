package com.edu360.api.idempotency;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.edu360.api.exception.ApiException;
import com.edu360.api.rbac.ScopeType;
import com.edu360.api.tenant.IsolationMode;
import com.edu360.api.tenant.TenantContext;
class IdempotencyServiceTest {
 @AfterEach void clear(){TenantContext.clear();}
 private void tenant(UUID id){TenantContext.set(id,IsolationMode.SHARED,null,"ALL",Set.of(),"PROFESSOR",ScopeType.TENANT);}
 @Test void requestHashIsDeterministicForMapKeyOrder(){assertThat(IdempotencyService.requestHash(Map.of("a",1,"b",2))).isEqualTo(IdempotencyService.requestHash(Map.of("b",2,"a",1)));}
 @Test void requestHashChangesWhenBodyChanges(){assertThat(IdempotencyService.requestHash(Map.of("status","PRESENT"))).isNotEqualTo(IdempotencyService.requestHash(Map.of("status","ABSENT")));}
 @Test void firstClaimCreatesAnInProgressRecord(){
  JdbcTemplate jdbc=mock(JdbcTemplate.class);UUID tenant=UUID.randomUUID(),caller=UUID.randomUUID();tenant(tenant);
  AtomicReference<UUID> insertedId=new AtomicReference<>();
  when(jdbc.update(anyString(),any(),any(),any(),any(),any(),any(),any(),any())).thenAnswer(inv->{insertedId.set(inv.getArgument(2));return 1;});
  when(jdbc.queryForList(anyString(),any(),any(),any(),any())).thenAnswer(inv->{
   Map<String,Object> row=new HashMap<>();row.put("idempotency_key_id",insertedId.get());row.put("request_hash",IdempotencyService.requestHash(Map.of("v",1)));
   row.put("status","IN_PROGRESS");row.put("response_status",null);row.put("response_body",null);return List.of(row);
  });
  var claim=new IdempotencyService(jdbc).claim(tenant,caller,"ATTENDANCE_UPLOAD","upload-key-0001",Map.of("v",1));
  assertThat(claim.replay()).isFalse();assertThat(claim.responseBody()).isNull();assertThat(claim.id()).isEqualTo(insertedId.get());
 }
 @Test void completedClaimReplaysStoredResponse(){
  JdbcTemplate jdbc=mock(JdbcTemplate.class);UUID tenant=UUID.randomUUID(),caller=UUID.randomUUID(),id=UUID.randomUUID();tenant(tenant);
  when(jdbc.update(anyString(),any(),any(),any(),any(),any(),any(),any(),any())).thenReturn(0);
  when(jdbc.queryForList(anyString(),any(),any(),any(),any())).thenReturn(List.of(Map.of(
   "idempotency_key_id",id,"request_hash",IdempotencyService.requestHash(Map.of("v",1)),
   "status","COMPLETED","response_status",201,"response_body","{\"result\":\"created\"}")));
  var claim=new IdempotencyService(jdbc).claim(tenant,caller,"ATTENDANCE_UPLOAD","upload-key-0002",Map.of("v",1));
  assertThat(claim.replay()).isTrue();assertThat(claim.responseStatus()).isEqualTo(201);assertThat(claim.responseBody().get("result").asText()).isEqualTo("created");
 }
 @Test void sameKeyWithDifferentBodyReturnsConflict(){
  JdbcTemplate jdbc=mock(JdbcTemplate.class);UUID tenant=UUID.randomUUID(),caller=UUID.randomUUID(),id=UUID.randomUUID();tenant(tenant);
  when(jdbc.update(anyString(),any(Object[].class))).thenReturn(0);
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of(
   "idempotency_key_id",id,"request_hash",IdempotencyService.requestHash(Map.of("v",2)),"status","COMPLETED")));
  assertThatThrownBy(()->new IdempotencyService(jdbc).claim(tenant,caller,"ATTENDANCE_UPLOAD","upload-key-0003",Map.of("v",1)))
   .isInstanceOf(ApiException.class).satisfies(ex->assertThat(((ApiException)ex).getErrorCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
 }
 @Test void concurrentInProgressDuplicateReturnsConflict(){
  JdbcTemplate jdbc=mock(JdbcTemplate.class);UUID tenant=UUID.randomUUID(),caller=UUID.randomUUID();tenant(tenant);
  when(jdbc.update(anyString(),any(Object[].class))).thenReturn(0);
  when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of(
   "idempotency_key_id",UUID.randomUUID(),"request_hash",IdempotencyService.requestHash(Map.of("v",1)),"status","IN_PROGRESS")));
  assertThatThrownBy(()->new IdempotencyService(jdbc).claim(tenant,caller,"ATTENDANCE_UPLOAD","upload-key-0004",Map.of("v",1)))
   .isInstanceOf(ApiException.class).satisfies(ex->assertThat(((ApiException)ex).getErrorCode()).isEqualTo("IDEMPOTENCY_REQUEST_IN_PROGRESS"));
 }
}

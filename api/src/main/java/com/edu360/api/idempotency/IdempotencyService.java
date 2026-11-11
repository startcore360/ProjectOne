package com.edu360.api.idempotency;
import java.security.MessageDigest;import java.security.NoSuchAlgorithmException;import java.util.HexFormat;import java.util.List;import java.util.Map;import java.util.UUID;
import javax.sql.DataSource;
import com.fasterxml.jackson.core.JsonProcessingException;import com.fasterxml.jackson.databind.JsonNode;import com.fasterxml.jackson.databind.ObjectMapper;import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.beans.factory.annotation.Autowired;import org.springframework.dao.DataAccessException;import org.springframework.http.HttpStatus;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import org.springframework.transaction.annotation.Propagation;
import com.edu360.api.exception.ApiException;import com.edu360.api.tenant.TenantContext;
/** Claims and replays D5 idempotency keys in app.idempotency_key. */
@Service
public class IdempotencyService {
 private final JdbcTemplate jdbc;private final ObjectMapper mapper=new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true);
 @Autowired public IdempotencyService(DataSource ds){this(new JdbcTemplate(ds));}
 public IdempotencyService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record Claim(UUID id,boolean replay,Integer responseStatus,JsonNode responseBody){}
 @Transactional(propagation=Propagation.REQUIRES_NEW) public Claim claim(UUID tenant,UUID caller,String scope,String key,Object body){
  if(tenant==null||caller==null||scope==null||scope.isBlank())throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_QUERY","Idempotency scope and caller are required.");
  if(key==null||key.length()<8||key.length()>100)throw new ApiException(HttpStatus.BAD_REQUEST,"IDEMPOTENCY_KEY_REQUIRED","Idempotency-Key must contain 8-100 characters.");
  if(!tenant.equals(TenantContext.currentTenantId()))throw new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","The requested resource was not found.");
  String hash=requestHash(body);UUID proposed=UUID.randomUUID();
  try{
   jdbc.update("""
    INSERT INTO app.idempotency_key(tenant_id,idempotency_key_id,scope,request_key,caller_user_id,request_hash,status,created_by,updated_by)
    VALUES(?,?,?,?,?,?,'IN_PROGRESS',?,?) ON CONFLICT(tenant_id,scope,caller_user_id,request_key) DO NOTHING
    """,tenant,proposed,scope,key,caller,hash,caller,caller);
   List<Map<String,Object>> rows=jdbc.queryForList("""
    SELECT idempotency_key_id,request_hash,status,response_status,response_body FROM app.idempotency_key
    WHERE tenant_id=? AND scope=? AND caller_user_id=? AND request_key=? FOR UPDATE
    """,tenant,scope,caller,key);
   if(rows.isEmpty())throw inProgress();
   Map<String,Object> row=rows.get(0);UUID id=(UUID)row.get("idempotency_key_id");
   if(!hash.equals(String.valueOf(row.get("request_hash")).trim()))throw new ApiException(HttpStatus.CONFLICT,"IDEMPOTENCY_KEY_REUSED","This key was used with a different request body.");
   if("IN_PROGRESS".equals(row.get("status"))){if(proposed.equals(id))return new Claim(id,false,null,null);throw inProgress();}
   Object stored=row.get("response_body");JsonNode response=stored==null?null:mapper.readTree(stored.toString());Number status=(Number)row.get("response_status");
   return new Claim(id,true,status==null?null:status.intValue(),response);
  }catch(ApiException ex){throw ex;}catch(DataAccessException|JsonProcessingException ex){throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"IDEMPOTENCY_STORE_UNAVAILABLE","The idempotency store is temporarily unavailable.");}
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW) public void complete(UUID tenant,UUID caller,UUID keyId,int status,Object body){
  if(!tenant.equals(TenantContext.currentTenantId()))throw new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","The requested resource was not found.");
  try{String json=mapper.writeValueAsString(body);int n=jdbc.update("""
   UPDATE app.idempotency_key SET status='COMPLETED',response_status=?,response_body=CAST(? AS jsonb),completed_at=now(),updated_by=?
   WHERE tenant_id=? AND idempotency_key_id=? AND caller_user_id=? AND status='IN_PROGRESS'
   """,status,json,caller,tenant,keyId,caller);
   if(n!=1)throw inProgress();
  }catch(JsonProcessingException ex){throw new IllegalArgumentException("Response cannot be serialized for idempotent replay.",ex);}
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW) public void release(UUID tenant,UUID caller,UUID keyId){
  jdbc.update("DELETE FROM app.idempotency_key WHERE tenant_id=? AND idempotency_key_id=? AND caller_user_id=? AND status='IN_PROGRESS'",tenant,keyId,caller);
 }
 static String requestHash(Object body){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true).writeValueAsBytes(body)));}
  catch(JsonProcessingException|NoSuchAlgorithmException ex){throw new IllegalArgumentException("Request body cannot be hashed.",ex);}
 }
 private static ApiException inProgress(){return new ApiException(HttpStatus.CONFLICT,"IDEMPOTENCY_REQUEST_IN_PROGRESS","An identical request is already being processed.");}
}

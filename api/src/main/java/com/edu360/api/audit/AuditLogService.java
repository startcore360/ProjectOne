package com.edu360.api.audit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import com.edu360.api.config.CorrelationContext;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.tenant.TenantContext;
/** Required audit writer. Tenant events commit or roll back with the business transaction. */
@Service
public class AuditLogService {
 private static final Logger log=LoggerFactory.getLogger(AuditLogService.class);
 private static final Set<String> SENSITIVITY=Set.of("N","P","F","H","K");
 private static final Set<String> OUTCOMES=Set.of("SUCCESS","DENIED","FAILED");
 private final AuditLogRepository legacy; private final TenantAuditStore tenant;
 private final ObjectMapper mapper=new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true);
 @Autowired public AuditLogService(AuditLogRepository legacy,TenantAuditStore tenant){this.legacy=legacy;this.tenant=tenant;}
 public AuditLogService(AuditLogRepository legacy){this(legacy,null);}
 public void record(AuditEventType type,Long userId,String email,String ip,String details){
  String role=TenantContext.currentActiveRoleCode();
  String scope=TenantContext.currentActiveScopeType()==null?null:TenantContext.currentActiveScopeType().name();
  Set<UUID> ids=TenantContext.currentInstitutionIds();
  record(type,userId,email,ip,details,role,scope,ids.isEmpty()?null:ids.iterator().next());
 }
 public void record(AuditEventType type,Long userId,String email,String ip,String details,String role,String scope,UUID institution){
  try{
   String correlation=CorrelationContext.get(); TenantPrincipal actor=tenantActor(role);
   if(actor!=null){tenant.append(legacyEvent(actor,type,details,role,scope,institution,correlation));return;}
   legacy.save(AuditLog.builder().eventType(type).userId(userId).email(email).ipAddress(ip).details(details)
    .actorRoleCode(role).scopeType(scope).institutionId(institution).correlationId(correlation).build());
  }catch(Exception ex){log.error("Audit write failed for {}",type,ex);throw new AuditFailureException("Required audit event could not be recorded; the request was not completed.",ex);}
 }
 public void record(AuditEntry entry){
  try{
   if(entry==null||entry.eventType()==null||blank(entry.actionCode())||blank(entry.entityType())||entry.entityId()==null)
    throw new IllegalArgumentException("Structured audit requires event type, action code, entity type and entity id.");
   if(entry.sensitivityClass()!=null&&!SENSITIVITY.contains(entry.sensitivityClass()))throw new IllegalArgumentException("Invalid sensitivity class.");
   if(entry.outcome()!=null&&!OUTCOMES.contains(entry.outcome()))throw new IllegalArgumentException("Invalid audit outcome.");
   if(entry.recordCount()!=null&&entry.recordCount()<0)throw new IllegalArgumentException("Record count cannot be negative.");
   String role=TenantContext.currentActiveRoleCode();
   UUID institution=entry.institutionId();
   if(institution==null){Set<UUID> ids=TenantContext.currentInstitutionIds();institution=ids.isEmpty()?null:ids.iterator().next();}
   if(tenant==null){
    Map<String,Object> fallback=new LinkedHashMap<>();
    fallback.put("action_code",entry.actionCode());fallback.put("entity_type",entry.entityType());
    fallback.put("entity_id",entry.entityId().toString());fallback.put("old_values",entry.oldValues());
    fallback.put("new_values",entry.newValues());fallback.put("record_count",entry.recordCount());
    fallback.put("sensitivity_class",entry.sensitivityClass());fallback.put("outcome",entry.outcome());
    legacy.save(AuditLog.builder().eventType(entry.eventType()).actorRoleCode(role)
      .scopeType(TenantContext.currentActiveScopeType()==null?null:TenantContext.currentActiveScopeType().name())
      .institutionId(institution).correlationId(bound(CorrelationContext.get()))
      .details(json(fallback)).build());
    return;
   }
   TenantPrincipal actor=tenantActor(role);
   if(actor==null)throw new IllegalStateException("Structured audit requires an authenticated tenant actor with active role.");
   UUID event=UUID.randomUUID(); Instant now=Instant.now(); String correlation=bound(CorrelationContext.get());
   String oldJson=json(entry.oldValues()),newJson=json(entry.newValues());
   String operation=operationOf(entry.eventType()),outcome=entry.outcome()==null?"SUCCESS":entry.outcome();
   String hash=sha(String.join("|",actor.tenantId().toString(),event.toString(),now.toString(),actor.userAccountId().toString(),
    role,operation,entry.actionCode(),entry.entityType(),entry.entityId().toString(),String.valueOf(entry.recordCount()),
    String.valueOf(entry.sensitivityClass()),outcome,String.valueOf(oldJson),String.valueOf(newJson),correlation));
   tenant.append(new TenantAuditEvent(actor.tenantId(),event,institution,now,actor.userAccountId(),role,operation,
    entry.actionCode(),entry.entityType(),entry.entityId(),entry.recordCount(),entry.sensitivityClass(),outcome,
    oldJson,newJson,correlation,hash));
  }catch(Exception ex){log.error("Structured audit write failed",ex);throw new AuditFailureException("Required audit event could not be recorded; the request was not completed.",ex);}
 }
 private TenantPrincipal tenantActor(String role){
  UUID tenantId=TenantContext.currentTenantId();
  if(tenant==null||tenantId==null||blank(role))return null;
  Authentication auth=SecurityContextHolder.getContext().getAuthentication();
  return auth!=null&&auth.getPrincipal() instanceof TenantPrincipal p&&tenantId.equals(p.tenantId())&&p.userAccountId()!=null?p:null;
 }
 private TenantAuditEvent legacyEvent(TenantPrincipal actor,AuditEventType type,String details,String role,String scope,UUID institution,String correlation){
  UUID id=UUID.randomUUID();Instant now=Instant.now();String operation=operationOf(type),outcome=outcomeOf(type),corr=bound(correlation);
  Map<String,Object> values=new LinkedHashMap<>();values.put("event_type",type.name());values.put("scope_type",scope);String safeDetails=safeLegacyDetails(details);if(safeDetails!=null)values.put("details",safeDetails);
  String newJson=json(values);String hash=sha(String.join("|",actor.tenantId().toString(),id.toString(),now.toString(),actor.userAccountId().toString(),role,operation,type.name(),outcome,corr,String.valueOf(newJson)));
  return new TenantAuditEvent(actor.tenantId(),id,institution,now,actor.userAccountId(),role,operation,null,type.name(),null,null,null,outcome,null,newJson,corr,hash);
 }
 static String operationOf(AuditEventType t){return switch(t){
  case LOGIN_SUCCESS,LOGIN_FAILURE,LOGOUT->"LOGIN";case DENIED_ATTEMPT->"DENIED";case EXPORT->"EXPORT";
  case SENSITIVE_VIEW->"VIEW_SENSITIVE";case DELETE->"DELETE";case APPROVAL,ATTENDANCE_CORRECTION_SELF_APPROVED->"APPROVE";
  case TERM_LOCKED,TERM_UNLOCKED,TENANT_SETTINGS_UPDATED,PRINCIPAL_LEAVE_APPROVER_SET,VC_LEAVE_APPROVER_SET->"CONFIG_CHANGE";
  case ATTENDANCE_MARKED,ATTENDANCE_CORRECTION_APPLIED->"UPDATE";
  default->t.name().endsWith("_REJECTED")?"REJECT":t.name().endsWith("_APPROVED")||t.name().endsWith("_PUBLISHED")?"APPROVE":
   t.name().endsWith("_CREATED")||t.name().endsWith("_CREATED_DRAFT")||t.name().endsWith("_LINKED")||
   t.name().endsWith("_ASSIGNED")||t.name().endsWith("_UPLOADED")||t.name().endsWith("_REQUESTED")||t.name().equals("CREATE")?"CREATE":"UPDATE";
 };}
 static String outcomeOf(AuditEventType t){return t==AuditEventType.LOGIN_FAILURE?"FAILED":t==AuditEventType.DENIED_ATTEMPT?"DENIED":"SUCCESS";}
 private String json(Object v){if(v==null)return null;validateSafeValues(v);try{return mapper.writeValueAsString(v);}catch(JsonProcessingException e){throw new IllegalArgumentException("Invalid audit JSON.",e);}}
 private static void validateSafeValues(Object value){
  if(value instanceof Map<?,?> map){for(Map.Entry<?,?> e:map.entrySet()){
   String key=String.valueOf(e.getKey()).toLowerCase(java.util.Locale.ROOT).replace("_","").replace("-","");
   if(key.equals("name")||key.endsWith("name")||key.contains("email")||key.contains("phone")||key.contains("mobile")
    ||key.contains("contact")||key.contains("address")||key.contains("joinlink")||key.contains("meetingurl")
    ||key.contains("file")&&key.contains("content")||key.contains("bytes")||key.contains("binary")||key.contains("link")||key.contains("url")||key.contains("password")||key.contains("token")||key.contains("secret"))
    throw new IllegalArgumentException("Sensitive personal or secret fields are not permitted in audit values.");
   validateSafeValues(e.getValue());
  }}else if(value instanceof Iterable<?> values){for(Object item:values)validateSafeValues(item);}
  else if(value instanceof String text){String lower=text.toLowerCase(java.util.Locale.ROOT);
   if(lower.contains("http://")||lower.contains("https://")||lower.matches(".*[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}.*")||lower.matches(".*[0-9][0-9 ()+.-]{7,}[0-9].*"))
    throw new IllegalArgumentException("Links and contact details are not permitted in audit values.");
  }
 }
 private static String safeLegacyDetails(String details){
  if(details==null||details.isBlank())return null;
  String lower=details.toLowerCase(java.util.Locale.ROOT);
  if(lower.contains("student")||lower.contains("guardian")||lower.contains("parent")||lower.contains("child")
   ||lower.contains("http://")||lower.contains("https://")||lower.contains("join link")||lower.contains("meeting link")||lower.contains("file content")||lower.contains("file bytes")||lower.contains("@")
   ||lower.matches(".*[0-9][0-9 ()+.-]{7,}[0-9].*"))return null;
  return details;
 }
 private static boolean blank(String s){return s==null||s.isBlank();}
 private static String bound(String s){if(blank(s))return UUID.randomUUID().toString();return s.length()>100?s.substring(0,100):s;}
 private static String sha(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}

package com.edu360.api.governance.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.edu360.api.audit.AuditEntry;
import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;

@Service
public class DepartmentEquipmentClearanceService {
 private final JdbcTemplate jdbc; private final PermissionEvaluatorService permissions; private final AuditLogService audit;
 public DepartmentEquipmentClearanceService(JdbcTemplate jdbc,PermissionEvaluatorService permissions,AuditLogService audit){this.jdbc=jdbc;this.permissions=permissions;this.audit=audit;}
 public record Clearance(UUID clearanceId,UUID institutionId,UUID departmentId,UUID studentId,String status,int outstandingItems,String notes,OffsetDateTime clearedAt,OffsetDateTime updatedAt){}
 @Transactional(readOnly=true)
 public List<Clearance> list(TenantPrincipal p,UUID institutionId,UUID departmentId){
  authorize(p,institutionId,departmentId);
  return jdbc.query("""
   SELECT department_equipment_clearance_id,institution_id,department_id,student_profile_id,clearance_status,outstanding_items,notes,cleared_at,updated_at
   FROM app.department_equipment_clearance WHERE tenant_id=? AND institution_id=? AND (? IS NULL OR department_id=?)
   ORDER BY updated_at DESC
   """,(rs,n)->new Clearance(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),rs.getObject(4,UUID.class),rs.getString(5),rs.getInt(6),rs.getString(7),rs.getObject(8,OffsetDateTime.class),rs.getObject(9,OffsetDateTime.class)),p.tenantId(),institutionId,departmentId,departmentId);
 }
 @Transactional
 public Clearance record(TenantPrincipal p,UUID institutionId,UUID departmentId,UUID studentId,int outstandingItems,String notes){
  authorize(p,institutionId,departmentId);
  if(outstandingItems<0||outstandingItems>10000||(notes!=null&&notes.length()>2000))throw new BusinessRuleViolationException("DEPARTMENT_CLEARANCE_INVALID","Outstanding item count or notes are invalid.");
  Integer valid=jdbc.query("SELECT count(*) FROM app.department d JOIN app.student_profile sp ON sp.tenant_id=d.tenant_id AND sp.institution_id=d.institution_id WHERE d.tenant_id=? AND d.institution_id=? AND d.department_id=? AND d.status='ACTIVE' AND sp.student_profile_id=?",rs->rs.next()?rs.getInt(1):0,p.tenantId(),institutionId,departmentId,studentId);
  if(valid==0)throw new ResourceNotFoundException("Department or student not found in this institution.");
  String status=outstandingItems==0?"CLEARED":"OUTSTANDING";
  jdbc.update("""
   INSERT INTO app.department_equipment_clearance
    (tenant_id,department_equipment_clearance_id,institution_id,department_id,student_profile_id,clearance_status,outstanding_items,notes,cleared_at,cleared_by_user_id,created_by,updated_by)
   VALUES (?,?,?,?,?,?,?,?,CASE WHEN ?='CLEARED' THEN now() ELSE NULL END,CASE WHEN ?='CLEARED' THEN ? ELSE NULL END,?,?)
   ON CONFLICT (tenant_id,institution_id,department_id,student_profile_id)
   DO UPDATE SET clearance_status=EXCLUDED.clearance_status,outstanding_items=EXCLUDED.outstanding_items,
    notes=EXCLUDED.notes,cleared_at=EXCLUDED.cleared_at,cleared_by_user_id=EXCLUDED.cleared_by_user_id,
    updated_by=EXCLUDED.updated_by,updated_at=now()
   """,p.tenantId(),UUID.randomUUID(),institutionId,departmentId,studentId,status,outstandingItems,notes,status,status,p.userAccountId(),p.userAccountId(),p.userAccountId());
  Clearance result=jdbc.query("""
   SELECT department_equipment_clearance_id,institution_id,department_id,student_profile_id,clearance_status,outstanding_items,notes,cleared_at,updated_at
   FROM app.department_equipment_clearance WHERE tenant_id=? AND institution_id=? AND department_id=? AND student_profile_id=?
   """,rs->rs.next()?new Clearance(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),rs.getObject(4,UUID.class),rs.getString(5),rs.getInt(6),rs.getString(7),rs.getObject(8,OffsetDateTime.class),rs.getObject(9,OffsetDateTime.class)):null,p.tenantId(),institutionId,departmentId,studentId);
  audit.record(new AuditEntry(AuditEventType.EDIT,"DEPARTMENT_EQUIPMENT_CLEARANCE_MANAGE","DEPARTMENT_EQUIPMENT_CLEARANCE",result.clearanceId(),institutionId,null,Map.of("departmentId",departmentId.toString(),"studentId",studentId.toString(),"status",status,"outstandingItems",outstandingItems),1,"P","SUCCESS"));
  return result;
 }
 private void authorize(TenantPrincipal p,UUID institutionId,UUID departmentId){
  RbacPrincipal caller=new RbacPrincipal(p.tenantId(),p.userAccountId());
  RbacScope scope=permissions.targetScope(caller,institutionId,departmentId);
  permissions.authorize(caller,"DEPARTMENT_EQUIPMENT_CLEARANCE_MANAGE",scope,RbacTarget.NONE);
 }
}

package com.edu360.api.academic.service;
import java.math.BigDecimal;import java.time.LocalDate;import java.util.List;import java.util.Map;import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import com.edu360.api.academic.calculator.AttendancePercentageCalculator;import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.rbac.PermissionEvaluatorService;import com.edu360.api.rbac.RbacPrincipal;import com.edu360.api.rbac.RbacScope;import com.edu360.api.rbac.RbacTarget;import com.edu360.api.rbac.UserRoleAssignment;
@Service public class AttendanceService{
 private final JdbcTemplate jdbc;private final PermissionEvaluatorService permissions;private final AttendancePercentageCalculator calculator;
 public AttendanceService(JdbcTemplate jdbc,PermissionEvaluatorService permissions,AttendancePercentageCalculator calculator){this.jdbc=jdbc;this.permissions=permissions;this.calculator=calculator;}
 @Transactional public Map<String,Object> createSession(UUID tenant,TenantPrincipal actor,UUID institution,UUID section,UUID group,UUID slot,UUID allocation,LocalDate date,String kind,boolean notHeld,List<AttendanceItem> entries){
  UUID session=academicSessionForScope(tenant,institution,section,group);if(session==null)throw new ResourceNotFoundException("Active section or subject group not found.");
  UUID dept=section!=null?departmentForSection(tenant,institution,section):departmentForGroup(tenant,institution,group);
  authorize(tenant,actor,notHeld?"ATTENDANCE_NOT_HELD":"ATTENDANCE_MARK",institution,dept);
  Short year=jdbc.queryForObject("SELECT academic_year FROM app.academic_session WHERE tenant_id=? AND academic_session_id=?",Short.class,tenant,session);
  UUID id=UUID.randomUUID();
  jdbc.update("""
   INSERT INTO app.attendance_session(tenant_id,attendance_session_id,institution_id,section_id,subject_group_id,timetable_slot_id,
    subject_allocation_id,session_kind,attendance_date,academic_year,held_status,marked_by_user_id,marked_as,sync_source,created_by,updated_by)
   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'ONLINE',?,?)
   """,tenant,id,institution,section,group,slot,allocation,kind,date,year,notHeld?"NOT_HELD":"HELD",actor.userAccountId(),"ASSIGNED",actor.userAccountId(),actor.userAccountId());
  if(entries!=null)for(AttendanceItem item:entries)jdbc.update("""
   INSERT INTO app.attendance_entry(tenant_id,attendance_entry_id,institution_id,attendance_session_id,enrollment_id,academic_year,
    status,recorded_via,recorded_by_user_id,created_by,updated_by) VALUES (?,?,?,?,?,?,?,'MANUAL',?,?,?)
   """,tenant,UUID.randomUUID(),institution,id,item.enrollmentId(),year,notHeld?"NOT_HELD":item.status(),actor.userAccountId(),actor.userAccountId(),actor.userAccountId());
  return jdbc.queryForMap("SELECT attendance_session_id,institution_id,section_id,subject_group_id,session_kind,attendance_date,academic_year,held_status,submitted_at,row_version FROM app.attendance_session WHERE tenant_id=? AND attendance_session_id=?",tenant,id);
 }
 @Transactional
 public int recordEntries(UUID tenant,TenantPrincipal actor,UUID institution,UUID sessionId,List<AttendanceItem> entries){
  Map<String,Object> session;
  try{session=jdbc.queryForMap("SELECT section_id,subject_group_id,academic_year,held_status,submitted_at FROM app.attendance_session WHERE tenant_id=? AND institution_id=? AND attendance_session_id=?",tenant,institution,sessionId);}
  catch(org.springframework.dao.EmptyResultDataAccessException ex){throw new ResourceNotFoundException("Attendance session not found.");}
  UUID section=(UUID)session.get("section_id"),group=(UUID)session.get("subject_group_id");
  UUID dept=section!=null?departmentForSection(tenant,institution,section):departmentForGroup(tenant,institution,group);
  authorize(tenant,actor,"ATTENDANCE_MARK",institution,dept);
  if(session.get("submitted_at")!=null)throw new com.edu360.api.exception.BusinessRuleViolationException("ATTENDANCE_ALREADY_SUBMITTED","Submitted attendance cannot be changed.");
  if(entries==null||entries.isEmpty())throw new IllegalArgumentException("At least one attendance entry is required.");
  if(entries.stream().map(AttendanceItem::enrollmentId).distinct().count()!=entries.size())throw new IllegalArgumentException("An enrollment may appear only once in a bulk attendance request.");
  String held=String.valueOf(session.get("held_status"));short year=((Number)session.get("academic_year")).shortValue();
  for(AttendanceItem item:entries){
   if(item.enrollmentId()==null)throw new IllegalArgumentException("Enrollment ID is required.");
   if(!List.of("PRESENT","ABSENT","ON_APPROVED_LEAVE","NOT_HELD").contains(item.status()))throw new IllegalArgumentException("Unsupported attendance status.");
   if("NOT_HELD".equals(held)&&!"NOT_HELD".equals(item.status()))throw new IllegalArgumentException("A session marked not held only accepts NOT_HELD entries.");
   if("HELD".equals(held)&&"NOT_HELD".equals(item.status()))throw new IllegalArgumentException("NOT_HELD entries require a session marked not held.");
   jdbc.update("""
    INSERT INTO app.attendance_entry(tenant_id,attendance_entry_id,institution_id,attendance_session_id,enrollment_id,academic_year,status,recorded_via,recorded_by_user_id,created_by,updated_by)
    VALUES (?,?,?,?,?,?,?,'MANUAL',?,?,?)
    ON CONFLICT (tenant_id,institution_id,attendance_session_id,enrollment_id,academic_year)
    DO UPDATE SET status=EXCLUDED.status,recorded_via='MANUAL',recorded_by_user_id=EXCLUDED.recorded_by_user_id,updated_by=EXCLUDED.updated_by
    """,tenant,UUID.randomUUID(),institution,sessionId,item.enrollmentId(),year,item.status(),actor.userAccountId(),actor.userAccountId(),actor.userAccountId());
  }
  return entries.size();
 }

 @Transactional(readOnly=true) public List<Map<String,Object>> roster(UUID tenant,TenantPrincipal actor,UUID institution,UUID section){
  return roster(tenant,actor,institution,section,LocalDate.now());
 }
 @Transactional(readOnly=true) public List<Map<String,Object>> roster(UUID tenant,TenantPrincipal actor,UUID institution,UUID section,LocalDate attendanceDate){
  authorize(tenant,actor,"ATTENDANCE_VIEW",institution,departmentForSection(tenant,institution,section));
  return jdbc.queryForList("""
   SELECT e.enrollment_id,p.student_profile_id,p.student_code,p.full_name,
    EXISTS(SELECT 1 FROM app.attendance_medical_exemption mx WHERE mx.tenant_id=e.tenant_id AND mx.institution_id=e.institution_id
     AND mx.enrollment_id=e.enrollment_id AND mx.status='APPROVED' AND ? BETWEEN mx.start_date AND mx.end_date) AS has_medical_exemption
   FROM app.student_enrollment e
   JOIN app.student_profile p ON p.tenant_id=e.tenant_id AND p.institution_id=e.institution_id AND p.student_profile_id=e.student_profile_id
   WHERE e.tenant_id=? AND e.institution_id=? AND e.section_id=? AND e.outcome='IN_PROGRESS' AND e.ended_on IS NULL AND p.status='ACTIVE'
   ORDER BY p.full_name,p.student_code
   """,attendanceDate,tenant,institution,section);
 }
 @Transactional(readOnly=true) public List<Map<String,Object>> rosterForGroup(UUID tenant,TenantPrincipal actor,UUID institution,UUID group){
  authorize(tenant,actor,"ATTENDANCE_VIEW",institution,departmentForGroup(tenant,institution,group));
  return jdbc.queryForList("""
   SELECT e.enrollment_id,p.student_profile_id,p.student_code,p.full_name FROM app.subject_group_member gm
   JOIN app.student_enrollment e ON e.tenant_id=gm.tenant_id AND e.institution_id=gm.institution_id AND e.enrollment_id=gm.enrollment_id
   JOIN app.student_profile p ON p.tenant_id=e.tenant_id AND p.institution_id=e.institution_id AND p.student_profile_id=e.student_profile_id
   WHERE gm.tenant_id=? AND gm.institution_id=? AND gm.subject_group_id=? AND e.outcome='IN_PROGRESS' AND e.ended_on IS NULL AND p.status='ACTIVE'
   ORDER BY p.full_name,p.student_code
   """,tenant,institution,group);
 }
 @Transactional(readOnly=true) public List<Map<String,Object>> register(UUID tenant,TenantPrincipal actor,UUID institution,UUID section,LocalDate date){
  authorize(tenant,actor,"ATTENDANCE_VIEW",institution,departmentForSection(tenant,institution,section));
  return jdbc.queryForList("""
   SELECT s.attendance_session_id,s.session_kind,s.attendance_date,s.held_status,s.submitted_at,e.enrollment_id,p.student_profile_id,
    p.student_code,p.full_name,a.status AS attendance_status
   FROM app.attendance_session s
   LEFT JOIN app.attendance_entry a ON a.tenant_id=s.tenant_id AND a.institution_id=s.institution_id AND a.attendance_session_id=s.attendance_session_id AND a.academic_year=s.academic_year
   LEFT JOIN app.student_enrollment e ON e.tenant_id=a.tenant_id AND e.institution_id=a.institution_id AND e.enrollment_id=a.enrollment_id
   LEFT JOIN app.student_profile p ON p.tenant_id=e.tenant_id AND p.institution_id=e.institution_id AND p.student_profile_id=e.student_profile_id
   WHERE s.tenant_id=? AND s.institution_id=? AND s.section_id=? AND s.attendance_date=? ORDER BY s.session_kind,p.full_name
   """,tenant,institution,section,date);
 }
 @Transactional(readOnly=true) public Map<String,Object> percentage(UUID tenant,TenantPrincipal actor,UUID institution,UUID enrollment){
  authorize(tenant,actor,"ATTENDANCE_VIEW",institution,departmentForEnrollment(tenant,institution,enrollment));
  try{return calculator.calculate(tenant,institution,enrollment);}catch(org.springframework.dao.EmptyResultDataAccessException ex){throw new ResourceNotFoundException("Attendance percentage not found.");}
 }
 @Transactional(readOnly=true) public Map<String,Object> eligibility(UUID tenant,TenantPrincipal actor,UUID institution,UUID enrollment){
  authorize(tenant,actor,"ATTENDANCE_VIEW",institution,departmentForEnrollment(tenant,institution,enrollment));
  try{return calculator.eligibility(tenant,institution,enrollment);}catch(org.springframework.dao.EmptyResultDataAccessException ex){throw new ResourceNotFoundException("Exam eligibility could not be calculated; verify an active threshold policy.");}
 }
 @Transactional(readOnly=true) public Map<String,Object> threshold(UUID tenant,TenantPrincipal actor,UUID institution,UUID department){
  authorize(tenant,actor,"ATTENDANCE_VIEW",institution,department);
  try{return jdbc.queryForMap("""
   SELECT * FROM app.attendance_threshold_policy WHERE tenant_id=? AND institution_id=? AND is_active
    AND (department_id IS NOT DISTINCT FROM ? OR department_id IS NULL) AND effective_from<=CURRENT_DATE AND (effective_to IS NULL OR effective_to>=CURRENT_DATE)
   ORDER BY (department_id IS NOT NULL) DESC,effective_from DESC LIMIT 1
   """,tenant,institution,department);}catch(org.springframework.dao.EmptyResultDataAccessException ex){throw new ResourceNotFoundException("Attendance threshold settings not found.");}
 }
 @Transactional public Map<String,Object> updateThreshold(UUID tenant,TenantPrincipal actor,UUID institution,UUID department,BigDecimal value,LocalDate effectiveFrom){
  authorize(tenant,actor,"ATTENDANCE_RULES_SET",institution,department);
  if(value==null||value.compareTo(BigDecimal.ZERO)<0||value.compareTo(new BigDecimal("100"))>0)throw new IllegalArgumentException("Attendance threshold must be between 0 and 100.");
  LocalDate from=effectiveFrom==null?LocalDate.now():effectiveFrom;
  jdbc.update("UPDATE app.attendance_threshold_policy SET is_active=false,updated_by=? WHERE tenant_id=? AND institution_id=? AND department_id IS NOT DISTINCT FROM ? AND is_active",actor.userAccountId(),tenant,institution,department);
  UUID id=UUID.randomUUID();
  jdbc.update("INSERT INTO app.attendance_threshold_policy(tenant_id,attendance_threshold_policy_id,institution_id,department_id,minimum_attendance_percentage,effective_from,is_active,created_by,updated_by) VALUES (?,?,?,?,?,?,true,?,?)",
   tenant,id,institution,department,value,from,actor.userAccountId(),actor.userAccountId());
  return jdbc.queryForMap("SELECT * FROM app.attendance_threshold_policy WHERE tenant_id=? AND attendance_threshold_policy_id=?",tenant,id);
 }
 private UUID academicSessionForScope(UUID tenant,UUID institution,UUID section,UUID group){
  if(section!=null)return jdbc.query("SELECT academic_session_id FROM app.section WHERE tenant_id=? AND institution_id=? AND section_id=?",rs->rs.next()?rs.getObject(1,UUID.class):null,tenant,institution,section);
  return jdbc.query("SELECT co.academic_session_id FROM app.subject_group g JOIN app.course_offering co ON co.tenant_id=g.tenant_id AND co.institution_id=g.institution_id AND co.course_offering_id=g.course_offering_id WHERE g.tenant_id=? AND g.institution_id=? AND g.subject_group_id=?",rs->rs.next()?rs.getObject(1,UUID.class):null,tenant,institution,group);
 }
 private UUID departmentForSection(UUID tenant,UUID institution,UUID section){
  return jdbc.query("SELECT pr.department_id FROM app.section s LEFT JOIN app.semester sm ON sm.tenant_id=s.tenant_id AND sm.semester_id=s.semester_id LEFT JOIN app.program_year py ON py.tenant_id=sm.tenant_id AND py.program_year_id=sm.program_year_id LEFT JOIN app.program pr ON pr.tenant_id=py.tenant_id AND pr.program_id=py.program_id WHERE s.tenant_id=? AND s.institution_id=? AND s.section_id=?",rs->rs.next()?rs.getObject(1,UUID.class):null,tenant,institution,section);
 }
 private UUID departmentForGroup(UUID tenant,UUID institution,UUID group){
  return jdbc.query("SELECT pr.department_id FROM app.subject_group g JOIN app.course_offering co ON co.tenant_id=g.tenant_id AND co.institution_id=g.institution_id AND co.course_offering_id=g.course_offering_id LEFT JOIN app.semester sm ON sm.tenant_id=co.tenant_id AND sm.semester_id=co.semester_id LEFT JOIN app.program_year py ON py.tenant_id=sm.tenant_id AND py.program_year_id=sm.program_year_id LEFT JOIN app.program pr ON pr.tenant_id=py.tenant_id AND pr.program_id=py.program_id WHERE g.tenant_id=? AND g.institution_id=? AND g.subject_group_id=?",rs->rs.next()?rs.getObject(1,UUID.class):null,tenant,institution,group);
 }
 private UUID departmentForEnrollment(UUID tenant,UUID institution,UUID enrollment){
  UUID section=jdbc.query("SELECT section_id FROM app.student_enrollment WHERE tenant_id=? AND institution_id=? AND enrollment_id=?",rs->rs.next()?rs.getObject(1,UUID.class):null,tenant,institution,enrollment);
  return section==null?null:departmentForSection(tenant,institution,section);
 }
 private void authorize(UUID tenant,TenantPrincipal actor,String action,UUID institution,UUID department){
  RbacPrincipal principal=new RbacPrincipal(tenant,actor.userAccountId());
  UserRoleAssignment active=permissions.activeAssignment(principal).orElseThrow(()->new UnauthorizedException("An active role is required for attendance access."));
  RbacScope scope=switch(active.getScopeType()){case TENANT->RbacScope.tenant();case INSTITUTION->RbacScope.institution(institution);case DEPARTMENT->{if(department==null)throw new UnauthorizedException("This attendance operation requires a department-scoped target.");yield RbacScope.department(institution,department);}};
  permissions.authorize(principal,action,scope,RbacTarget.NONE);
 }
 public record AttendanceItem(UUID enrollmentId,String status){}
}
package com.edu360.api.student;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

@RestController
public class StudentGuardianPreferencesController {
    private final NamedParameterJdbcTemplate jdbc;
    private final PermissionEvaluatorService permissions;
    public StudentGuardianPreferencesController(NamedParameterJdbcTemplate jdbc,PermissionEvaluatorService permissions){this.jdbc=jdbc;this.permissions=permissions;}
    public record Preferences(boolean contactAuthorized,boolean emergencyContact,boolean primary){}

    @GetMapping({"/v1/tenants/{tenantId}/students/{studentId}/guardian-preferences","/api/tenants/{tenantId}/students/{studentId}/guardian-preferences"})
    public java.util.List<Map<String,Object>> list(@PathVariable UUID tenantId,@PathVariable UUID studentId,Authentication auth){
        if(auth==null||!(auth.getPrincipal() instanceof TenantPrincipal p)||!tenantId.equals(p.tenantId()))throw new UnauthorizedException("Authenticated tenant principal required.");
        Map<String,Object> student=jdbc.queryForMap("SELECT institution_id FROM app.student_profile WHERE tenant_id=:t AND student_profile_id=:s",Map.of("t",tenantId,"s",studentId));
        permissions.authorize(new RbacPrincipal(tenantId,p.userAccountId()),"GUARDIAN_LINK",scope((UUID)student.get("institution_id")),RbacTarget.NONE);
        return jdbc.queryForList("SELECT student_guardian_link_id AS \"studentGuardianLinkId\",contact_authorized AS \"contactAuthorized\",emergency_contact AS \"emergencyContact\",is_primary AS \"primary\" FROM app.student_guardian_link WHERE tenant_id=:t AND student_profile_id=:s AND status<>'BLOCKED' ORDER BY created_at",Map.of("t",tenantId,"s",studentId));
    }

    @Transactional
    @PatchMapping({"/v1/tenants/{tenantId}/students/{studentId}/guardian-links/{linkId}/preferences","/api/tenants/{tenantId}/students/{studentId}/guardian-links/{linkId}/preferences"})
    public Map<String,Object> update(@PathVariable UUID tenantId,@PathVariable UUID studentId,@PathVariable UUID linkId,@RequestBody Preferences body,Authentication auth){
        if(auth==null||!(auth.getPrincipal() instanceof TenantPrincipal p)||!tenantId.equals(p.tenantId()))throw new UnauthorizedException("Authenticated tenant principal required.");
        Map<String,Object> student=jdbc.queryForMap("SELECT institution_id FROM app.student_profile WHERE tenant_id=:t AND student_profile_id=:s",Map.of("t",tenantId,"s",studentId));
        permissions.authorize(new RbacPrincipal(tenantId,p.userAccountId()),"GUARDIAN_LINK",scope((UUID)student.get("institution_id")),RbacTarget.NONE);
        if(body.primary()) jdbc.update("UPDATE app.student_guardian_link SET is_primary=false,updated_by=:actor WHERE tenant_id=:t AND student_profile_id=:s AND status<>'BLOCKED'",Map.of("actor",p.userAccountId(),"t",tenantId,"s",studentId));
        int changed = jdbc.update("UPDATE app.student_guardian_link SET contact_authorized=:authorized, emergency_contact=:emergency, is_primary=:primary, updated_by=:actor WHERE tenant_id=:t AND student_profile_id=:s AND student_guardian_link_id=:l AND status<>'BLOCKED'",
          Map.of("authorized",body.contactAuthorized(),"emergency",body.emergencyContact(),"primary",body.primary(),"actor",p.userAccountId(),"t",tenantId,"s",studentId,"l",linkId));
        if (changed != 1) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Guardian link not found");
        return jdbc.queryForMap("SELECT student_guardian_link_id AS \"studentGuardianLinkId\",contact_authorized AS \"contactAuthorized\",emergency_contact AS \"emergencyContact\",is_primary AS \"primary\" FROM app.student_guardian_link WHERE tenant_id=:t AND student_profile_id=:s AND student_guardian_link_id=:l",Map.of("t",tenantId,"s",studentId,"l",linkId));
    }
    private static RbacScope scope(UUID institutionId) { return institutionId == null ? RbacScope.tenant() : RbacScope.institution(institutionId); }
}
package com.edu360.api.document.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.edu360.api.tenant.TenantContext;
import com.edu360.api.governance.service.NoDuesVerificationService;

@Service
public class CertificateService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final PermissionEvaluatorService permissions;
    private final NoDuesVerificationService noDues;
    private final AuditLogService audit;
    private final ObjectMapper mapper;

    public CertificateService(JdbcTemplate jdbc, PermissionEvaluatorService permissions,
            NoDuesVerificationService noDues, AuditLogService audit, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.noDues = noDues;
        this.audit = audit;
        this.mapper = mapper;
    }

    public record Template(UUID certificateTemplateId, UUID institutionId, String type,
            String name, String templateHtml, String status, Integer rowVersion) { }
    public record Request(UUID certificateRequestId, UUID institutionId, UUID studentId,
            UUID templateId, String reason, String status, String duesJson, OffsetDateTime createdAt) { }
    public record Issuance(UUID certificateIssuanceId, String certificateNumber,
            String verificationCode, String verificationUrl, OffsetDateTime issuedAt) { }
    public record PublicVerification(String status, String studentName, String programName,
            String certificateType, OffsetDateTime issuedAt, String institutionName) { }

    @Transactional(readOnly = true)
    public List<Template> templates(TenantPrincipal principal, UUID institutionId) {
        authorize(principal, institutionId, "CERTIFICATE_REQUEST", RbacTarget.NONE);
        return jdbc.query("""
                SELECT certificate_template_id,institution_id,type,name,template_html,status,row_version
                  FROM app.certificate_template
                 WHERE tenant_id=? AND institution_id=? AND status='ACTIVE'
                 ORDER BY type,name
                """, (rs, n) -> new Template(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getInt(7)),
                principal.tenantId(), institutionId);
    }

    @Transactional
    public Template createTemplate(TenantPrincipal principal, UUID institutionId,
            String type, String name, String templateHtml) {
        authorize(principal, institutionId, "CERTIFICATE_TEMPLATE_MANAGE", RbacTarget.NONE);
        if (!List.of("TRANSFER_CERTIFICATE", "BONAFIDE", "CONDUCT", "DEGREE").contains(type)) {
            throw new BusinessRuleViolationException("CERTIFICATE_TYPE_INVALID", "Unsupported certificate type.");
        }
        if (name == null || name.isBlank() || name.length() > 100
                || templateHtml == null || templateHtml.isBlank() || templateHtml.length() > 50000
                || templateHtml.toLowerCase().contains("<script")
                || templateHtml.toLowerCase().contains("javascript:")
                || templateHtml.matches("(?is).*\\son[a-z]+\\s*=.*")) {
            throw new BusinessRuleViolationException("CERTIFICATE_TEMPLATE_INVALID",
                    "Template name or HTML is invalid. Script, event-handler and javascript content is not permitted.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app.certificate_template
                  (tenant_id,certificate_template_id,institution_id,type,name,template_html,
                   status,created_by,updated_by)
                VALUES (?,?,?,?,?,?,'ACTIVE',?,?)
                """, principal.tenantId(), id, institutionId, type, name.trim(),
                templateHtml, principal.userAccountId(), principal.userAccountId());
        audit.record(new AuditEntry(AuditEventType.CREATE, "CERTIFICATE_TEMPLATE_MANAGE",
                "CERTIFICATE_TEMPLATE", id, institutionId, null, Map.of("type", type), 1, "P", "SUCCESS"));
        return new Template(id, institutionId, type, name.trim(), templateHtml, "ACTIVE", 1);
    }

    @Transactional
    public Request request(TenantPrincipal principal, UUID studentId, UUID templateId, String reason) {
        UUID institutionId = jdbc.query("""
                SELECT institution_id FROM app.student_profile
                 WHERE tenant_id=? AND student_profile_id=?
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, principal.tenantId(), studentId);
        if (institutionId == null) throw new ResourceNotFoundException("Student not found.");
        authorize(principal, institutionId, "CERTIFICATE_REQUEST", RbacTarget.NONE);
        if (reason == null || reason.isBlank() || reason.length() > 1000) {
            throw new BusinessRuleViolationException("CERTIFICATE_REASON_REQUIRED", "A reason of up to 1,000 characters is required.");
        }
        Integer templateExists = jdbc.query("""
                SELECT count(*) FROM app.certificate_template
                 WHERE tenant_id=? AND institution_id=? AND certificate_template_id=? AND status='ACTIVE'
                """, rs -> rs.next() ? rs.getInt(1) : 0, principal.tenantId(), institutionId, templateId);
        if (templateExists == 0) throw new ResourceNotFoundException("Certificate template not found.");
        NoDuesVerificationService.ClearanceResult clearance = noDues.verifyClearance(studentId);
        String status = clearance.clear() ? "REQUESTED" : "NO_DUES_PENDING";
        String dues;
        try { dues = mapper.writeValueAsString(clearance.items()); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Could not serialize No-Dues result.", ex); }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app.certificate_request
                  (tenant_id,certificate_request_id,institution_id,student_id,template_id,reason,status,
                   dues_json,created_by,updated_by)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """, principal.tenantId(), id, institutionId, studentId, templateId, reason.trim(),
                status, dues, principal.userAccountId(), principal.userAccountId());
        audit.record(new AuditEntry(AuditEventType.CREATE, "CERTIFICATE_REQUEST",
                "CERTIFICATE_REQUEST", id, institutionId, null, Map.of("status", status), 1, "P", "SUCCESS"));
        return new Request(id, institutionId, studentId, templateId, reason.trim(), status, dues, null);
    }

    @Transactional(readOnly = true)
    public List<Request> requests(TenantPrincipal principal, UUID institutionId) {
        authorize(principal, institutionId, "CERTIFICATE_PREPARE", RbacTarget.NONE);
        return jdbc.query("""
                SELECT certificate_request_id,institution_id,student_id,template_id,reason,status,
                       dues_json::text,created_at
                  FROM app.certificate_request
                 WHERE tenant_id=? AND institution_id=?
                 ORDER BY created_at DESC
                """, (rs, n) -> new Request(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getString(5),
                rs.getString(6), rs.getString(7), rs.getObject(8, OffsetDateTime.class)),
                principal.tenantId(), institutionId);
    }

    @Transactional
    public Request approve(TenantPrincipal principal, UUID requestId) {
        RequestRow row = requestRow(principal.tenantId(), requestId);
        UUID targetUser = studentUser(row.studentId());
        authorize(principal, row.institutionId(), "CERTIFICATE_APPROVE",
                targetUser == null ? RbacTarget.NONE : RbacTarget.of(targetUser));
        NoDuesVerificationService.ClearanceResult clearance = noDues.verifyClearance(row.studentId());
        if (!clearance.clear()) {
            updateDues(principal, row, clearance, "NO_DUES_PENDING");
            throw new BusinessRuleViolationException("NO_DUES_PENDING",
                    "Certificate approval is blocked until all fee, library and departmental clearances are confirmed.");
        }
        jdbc.update("""
                UPDATE app.certificate_request SET status='APPROVED',dues_json='[]'::jsonb,
                       updated_by=?,updated_at=now()
                 WHERE tenant_id=? AND certificate_request_id=?
                """, principal.userAccountId(), principal.tenantId(), requestId);
        audit.record(new AuditEntry(AuditEventType.APPROVAL, "CERTIFICATE_APPROVE",
                "CERTIFICATE_REQUEST", requestId, row.institutionId(), Map.of("status", row.status()),
                Map.of("status", "APPROVED"), 1, "P", "SUCCESS"));
        return new Request(requestId, row.institutionId(), row.studentId(), row.templateId(),
                row.reason(), "APPROVED", "[]", null);
    }

    @Transactional
    public Issuance prepare(TenantPrincipal principal, UUID requestId) {
        RequestRow row = requestRow(principal.tenantId(), requestId);
        authorize(principal, row.institutionId(), "CERTIFICATE_PREPARE", RbacTarget.NONE);
        if (!"APPROVED".equals(row.status())) {
            throw new BusinessRuleViolationException("CERTIFICATE_NOT_APPROVED", "Only an approved request can be prepared.");
        }
        NoDuesVerificationService.ClearanceResult clearance = noDues.verifyClearance(row.studentId());
        if (!clearance.clear()) {
            updateDues(principal, row, clearance, "NO_DUES_PENDING");
            throw new BusinessRuleViolationException("NO_DUES_PENDING", "No-Dues clearance must be revalidated before issuance.");
        }
        String code = randomCode();
        String hash = sha256(code);
        String number = "CERT-" + LocalDate.now() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UUID issuanceId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app.certificate_issuance
                  (tenant_id,certificate_issuance_id,institution_id,certificate_request_id,
                   certificate_number,verification_code_hash,issued_by_user_id,created_by)
                VALUES (?,?,?,?,?,?,?,?)
                """, principal.tenantId(), issuanceId, row.institutionId(), requestId,
                number, hash, principal.userAccountId(), principal.userAccountId());
        jdbc.update("""
                UPDATE app.certificate_request SET status='ISSUED',updated_by=?,updated_at=now()
                 WHERE tenant_id=? AND certificate_request_id=?
                """, principal.userAccountId(), principal.tenantId(), requestId);
        audit.record(new AuditEntry(AuditEventType.CREATE, "CERTIFICATE_PREPARE",
                "CERTIFICATE_ISSUANCE", issuanceId, row.institutionId(), null,
                Map.of("certificateNumber", number, "requestId", requestId.toString()), 1, "P", "SUCCESS"));
        return new Issuance(issuanceId, number, code,
                "/api/public/certificates/verify/" + code, OffsetDateTime.now());
    }

    @Transactional
    public void overrideNoDues(TenantPrincipal principal, UUID requestId, String reason) {
        RequestRow row = requestRow(principal.tenantId(), requestId);
        authorize(principal, row.institutionId(), "NO_DUES_OVERRIDE", RbacTarget.NONE);
        if (reason == null || reason.isBlank() || reason.length() > 1000) {
            throw new BusinessRuleViolationException("OVERRIDE_REASON_REQUIRED", "A reason is required for a No-Dues override.");
        }
        jdbc.update("""
                UPDATE app.certificate_request SET status='APPROVED',updated_by=?,updated_at=now()
                 WHERE tenant_id=? AND certificate_request_id=?
                """, principal.userAccountId(), principal.tenantId(), requestId);
        audit.record(new AuditEntry(AuditEventType.APPROVAL, "NO_DUES_OVERRIDE",
                "CERTIFICATE_REQUEST", requestId, row.institutionId(),
                Map.of("status", row.status()), Map.of("status", "APPROVED", "overrideReason", reason.trim()),
                1, "H", "SUCCESS"));
    }

    @Transactional
    public void revoke(TenantPrincipal principal, UUID issuanceId, String reason) {
        Map<String, Object> row = jdbc.query("""
                SELECT ci.institution_id,cr.student_id,sp.user_account_id,ci.revoked_at
                  FROM app.certificate_issuance ci
                  JOIN app.certificate_request cr ON cr.tenant_id=ci.tenant_id
                   AND cr.certificate_request_id=ci.certificate_request_id
                  JOIN app.student_profile sp ON sp.tenant_id=cr.tenant_id
                   AND sp.student_profile_id=cr.student_id
                 WHERE ci.tenant_id=? AND ci.certificate_issuance_id=?
                """, rs -> rs.next() ? Map.of(
                "institutionId", rs.getObject(1, UUID.class),
                "studentId", rs.getObject(2, UUID.class),
                "userId", rs.getObject(3, UUID.class) == null ? "" : rs.getObject(3, UUID.class).toString(),
                "revoked", rs.getObject(4) != null) : null, principal.tenantId(), issuanceId);
        if (row == null) throw new ResourceNotFoundException("Certificate issuance not found.");
        if (Boolean.TRUE.equals(row.get("revoked"))) throw new BusinessRuleViolationException("CERTIFICATE_ALREADY_REVOKED", "Certificate is already revoked.");
        String userId = (String) row.get("userId");
        authorize(principal, (UUID) row.get("institutionId"), "CERTIFICATE_REVOKE",
                userId.isBlank() ? RbacTarget.NONE : RbacTarget.of(UUID.fromString(userId)));
        if (reason == null || reason.isBlank() || reason.length() > 1000) {
            throw new BusinessRuleViolationException("REVOCATION_REASON_REQUIRED", "A revocation reason is required.");
        }
        jdbc.update("""
                UPDATE app.certificate_issuance SET revoked_at=now(),revoked_by_user_id=?,revocation_reason=?
                 WHERE tenant_id=? AND certificate_issuance_id=? AND revoked_at IS NULL
                """, principal.userAccountId(), reason.trim(), principal.tenantId(), issuanceId);
        audit.record(new AuditEntry(AuditEventType.APPROVAL, "CERTIFICATE_REVOKE",
                "CERTIFICATE_ISSUANCE", issuanceId, (UUID) row.get("institutionId"), null,
                Map.of("revocationReason", reason.trim()), 1, "H", "SUCCESS"));
    }

    @Transactional(readOnly = true)
    public PublicVerification verifyPublic(String code) {
        if (code == null || code.length() < 32 || code.length() > 128
                || !code.matches("[A-Za-z0-9_-]+")) {
            throw new ResourceNotFoundException("Certificate not found.");
        }
        String hash = sha256(code);
        return jdbc.query("""
                SELECT verification_status,student_name,program_name,certificate_type,issued_at,institution_name
                  FROM app.fn_public_certificate_verify(?)
                """, rs -> rs.next() ? new PublicVerification(rs.getString(1), rs.getString(2),
                rs.getString(3), rs.getString(4), rs.getObject(5, OffsetDateTime.class), rs.getString(6)) : null,
                hash);
    }

    private record RequestRow(UUID institutionId, UUID studentId, UUID templateId,
            String reason, String status) { }

    private RequestRow requestRow(UUID tenantId, UUID requestId) {
        return jdbc.query("""
                SELECT institution_id,student_id,template_id,reason,status
                  FROM app.certificate_request
                 WHERE tenant_id=? AND certificate_request_id=?
                """, rs -> rs.next() ? new RequestRow(rs.getObject(1, UUID.class),
                rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                rs.getString(4), rs.getString(5)) : null, tenantId, requestId);
    }

    private UUID studentUser(UUID studentId) {
        return jdbc.query("""
                SELECT user_account_id FROM app.student_profile
                 WHERE tenant_id=? AND student_profile_id=?
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                TenantContext.currentTenantId(), studentId);
    }

    private void updateDues(TenantPrincipal principal, RequestRow row,
            NoDuesVerificationService.ClearanceResult clearance, String status) {
        try {
            jdbc.update("""
                    UPDATE app.certificate_request SET status=?,dues_json=?::jsonb,
                           updated_by=?,updated_at=now()
                     WHERE tenant_id=? AND certificate_request_id=?
                    """, status, mapper.writeValueAsString(clearance.items()), principal.userAccountId(),
                    principal.tenantId(), row == null ? null : findRequestId(row));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize No-Dues result.", ex);
        }
    }

    private UUID findRequestId(RequestRow row) {
        return jdbc.query("""
                SELECT certificate_request_id FROM app.certificate_request
                 WHERE tenant_id=? AND institution_id=? AND student_id=? AND template_id=? AND status=?
                 ORDER BY created_at DESC LIMIT 1
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                TenantContext.currentTenantId(), row.institutionId(), row.studentId(), row.templateId(), row.status());
    }

    private RbacScope authorize(TenantPrincipal principal, UUID institutionId, String action, RbacTarget target) {
        RbacPrincipal caller = new RbacPrincipal(principal.tenantId(), principal.userAccountId());
        RbacScope scope = permissions.targetScope(caller, institutionId, null);
        permissions.authorize(caller, action, scope, target);
        return scope;
    }

    private static String randomCode() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }
}

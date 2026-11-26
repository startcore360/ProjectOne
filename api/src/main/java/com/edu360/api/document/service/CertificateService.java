package com.edu360.api.document.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
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
    private final String signingSecret;

    public CertificateService(JdbcTemplate jdbc, PermissionEvaluatorService permissions,
            NoDuesVerificationService noDues, AuditLogService audit, ObjectMapper mapper,
            @Value("${edu360.documents.signing-secret:}") String signingSecret) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.noDues = noDues;
        this.audit = audit;
        this.mapper = mapper;
        this.signingSecret = signingSecret;
    }

    public record Template(UUID certificateTemplateId, UUID institutionId, String type,
            String name, String templateHtml, String status, Integer rowVersion) { }
    public record Request(UUID certificateRequestId, UUID institutionId, UUID studentId,
            UUID templateId, String reason, String status, String duesJson, OffsetDateTime createdAt) { }
    public record Issuance(UUID certificateIssuanceId, String certificateNumber,
            String verificationCode, String verificationUrl, OffsetDateTime issuedAt) { }
    public record PublicVerification(String status, String studentName, String programName,
            String certificateType, OffsetDateTime issuedAt, String institutionName) { }
    public record IssuanceSummary(UUID issuanceId, UUID requestId, String certificateNumber,
            OffsetDateTime issuedAt, OffsetDateTime revokedAt) { }

    @Transactional(readOnly = true)
    public List<Template> templates(TenantPrincipal principal, UUID institutionId) {
        RbacPrincipal caller = new RbacPrincipal(principal.tenantId(), principal.userAccountId());
        RbacScope scope = permissions.targetScope(caller, institutionId, null);
        if (!permissions.can(caller, "CERTIFICATE_REQUEST", scope, RbacTarget.NONE).allowed()
                && !permissions.can(caller, "CERTIFICATE_PREPARE", scope, RbacTarget.NONE).allowed()
                && !permissions.can(caller, "CERTIFICATE_TEMPLATE_MANAGE", scope, RbacTarget.NONE).allowed()) {
            throw new com.edu360.api.exception.UnauthorizedException("Certificate template access is not granted.");
        }
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
        RbacPrincipal caller = new RbacPrincipal(principal.tenantId(), principal.userAccountId());
        RbacScope scope = permissions.targetScope(caller, institutionId, null);
        boolean canPrepare = permissions.can(caller, "CERTIFICATE_PREPARE", scope, RbacTarget.NONE).allowed();
        boolean canApprove = permissions.can(caller, "CERTIFICATE_APPROVE", scope, RbacTarget.NONE).allowed();
        boolean canRequest = permissions.can(caller, "CERTIFICATE_REQUEST", scope, RbacTarget.NONE).allowed();
        if (!canPrepare && !canApprove && !canRequest) {
            throw new com.edu360.api.exception.UnauthorizedException("Certificate request access is not granted.");
        }
        String sql = """
                SELECT cr.certificate_request_id,cr.institution_id,cr.student_id,cr.template_id,cr.reason,cr.status,
                       cr.dues_json::text,cr.created_at
                  FROM app.certificate_request cr
                 WHERE cr.tenant_id=? AND cr.institution_id=?
                """ + ((canPrepare || canApprove) ? "" : """
                  AND EXISTS (SELECT 1 FROM app.student_profile sp
                               WHERE sp.tenant_id=cr.tenant_id AND sp.student_profile_id=cr.student_id
                                 AND sp.user_account_id=?)
                """) + " ORDER BY cr.created_at DESC";
        return (canPrepare || canApprove)
                ? jdbc.query(sql, (rs, n) -> new Request(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                    rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getString(5),
                    rs.getString(6), rs.getString(7), rs.getObject(8, OffsetDateTime.class)),
                    principal.tenantId(), institutionId)
                : jdbc.query(sql, (rs, n) -> new Request(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                    rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getString(5),
                    rs.getString(6), rs.getString(7), rs.getObject(8, OffsetDateTime.class)),
                    principal.tenantId(), institutionId, principal.userAccountId());
    }

    @Transactional
    public Request approve(TenantPrincipal principal, UUID requestId) {
        RequestRow row = requestRow(principal.tenantId(), requestId);
        UUID targetUser = studentUser(row.studentId());
        authorize(principal, row.institutionId(), "CERTIFICATE_APPROVE",
                targetUser == null ? RbacTarget.NONE : RbacTarget.of(targetUser));
        NoDuesVerificationService.ClearanceResult clearance = noDues.verifyClearance(row.studentId());
        if (!clearance.clear()) {
            updateDues(principal, requestId, clearance, "NO_DUES_PENDING");
            return new Request(requestId, row.institutionId(), row.studentId(), row.templateId(),
                    row.reason(), "NO_DUES_PENDING", serializeDues(clearance), null);
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

    @Transactional(noRollbackFor = BusinessRuleViolationException.class)
    public Issuance prepare(TenantPrincipal principal, UUID requestId) {
        RequestRow row = requestRow(principal.tenantId(), requestId);
        authorize(principal, row.institutionId(), "CERTIFICATE_PREPARE", RbacTarget.NONE);
        if (!"APPROVED".equals(row.status())) {
            throw new BusinessRuleViolationException("CERTIFICATE_NOT_APPROVED", "Only an approved request can be prepared.");
        }
        NoDuesVerificationService.ClearanceResult clearance = noDues.verifyClearance(row.studentId());
        if (!clearance.clear() && row.noDuesOverrideAt() == null) {
            updateDues(principal, requestId, clearance, "NO_DUES_PENDING");
            throw new BusinessRuleViolationException("NO_DUES_PENDING", "No-Dues clearance must be revalidated or explicitly overridden before issuance.");
        }
        String number = "CERT-" + LocalDate.now() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UUID issuanceId = UUID.randomUUID();
        String code = certificateCode(principal.tenantId(), issuanceId, number);
        String hash = sha256(code);
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

    @Transactional(readOnly = true)
    public List<IssuanceSummary> issuances(TenantPrincipal principal, UUID institutionId) {
        RbacPrincipal caller = new RbacPrincipal(principal.tenantId(), principal.userAccountId());
        RbacScope scope = permissions.targetScope(caller, institutionId, null);
        if (!permissions.can(caller, "CERTIFICATE_PREPARE", scope, RbacTarget.NONE).allowed()
                && !permissions.can(caller, "CERTIFICATE_REVOKE", scope, RbacTarget.NONE).allowed()) {
            throw new com.edu360.api.exception.UnauthorizedException("Certificate issuance access is not granted.");
        }
        return jdbc.query("""
                SELECT certificate_issuance_id,certificate_request_id,certificate_number,issued_at,revoked_at
                  FROM app.certificate_issuance
                 WHERE tenant_id=? AND institution_id=?
                 ORDER BY issued_at DESC
                """, (rs, n) -> new IssuanceSummary(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getString(3), rs.getObject(4, OffsetDateTime.class), rs.getObject(5, OffsetDateTime.class)),
                principal.tenantId(), institutionId);
    }

    @Transactional(readOnly = true)
    public String renderIssuance(TenantPrincipal principal, UUID issuanceId) {
        Map<String, Object> row = jdbc.query("""
                SELECT ci.institution_id,ci.certificate_number,ci.issued_at,ci.revoked_at,
                       cr.student_id,ct.template_html,ct.type,sp.full_name,sp.program_id,
                       COALESCE(p.program_name,'Not recorded'),inst.institution_name
                  FROM app.certificate_issuance ci
                  JOIN app.certificate_request cr ON cr.tenant_id=ci.tenant_id
                   AND cr.certificate_request_id=ci.certificate_request_id
                  JOIN app.certificate_template ct ON ct.tenant_id=cr.tenant_id
                   AND ct.certificate_template_id=cr.template_id
                  JOIN app.student_profile sp ON sp.tenant_id=cr.tenant_id
                   AND sp.student_profile_id=cr.student_id
                  LEFT JOIN app.program p ON p.tenant_id=sp.tenant_id AND p.program_id=sp.program_id
                  JOIN app.institution inst ON inst.tenant_id=ci.tenant_id AND inst.institution_id=ci.institution_id
                 WHERE ci.tenant_id=? AND ci.certificate_issuance_id=?
                """, rs -> rs.next() ? Map.of(
                "institutionId", rs.getObject(1, UUID.class),
                "certificateNumber", rs.getString(2),
                "issuedAt", rs.getObject(3, OffsetDateTime.class),
                "revoked", rs.getObject(4) != null,
                "studentId", rs.getObject(5, UUID.class),
                "templateHtml", rs.getString(6),
                "type", rs.getString(7),
                "studentName", rs.getString(8),
                "programName", rs.getString(10),
                "institutionName", rs.getString(11)) : null,
                principal.tenantId(), issuanceId);
        if (row == null) throw new ResourceNotFoundException("Certificate issuance not found.");
        authorize(principal, (UUID) row.get("institutionId"), "CERTIFICATE_PREPARE", RbacTarget.NONE);
        String code = certificateCode(principal.tenantId(), issuanceId, (String) row.get("certificateNumber"));
        String html = (String) row.get("templateHtml");
        html = html.replace("{{studentName}}", html((String) row.get("studentName")))
                .replace("{{programName}}", html((String) row.get("programName")))
                .replace("{{certificateNumber}}", html((String) row.get("certificateNumber")))
                .replace("{{certificateType}}", html((String) row.get("type")))
                .replace("{{institutionName}}", html((String) row.get("institutionName")))
                .replace("{{issueDate}}", html(row.get("issuedAt").toString()))
                .replace("{{verificationUrl}}", html("/api/public/certificates/verify/" + code))
                .replace("{{verificationCode}}", html(code));
        if (Boolean.TRUE.equals(row.get("revoked"))) {
            html = "<div role='alert'>REVOKED CERTIFICATE</div>" + html;
        }
        audit.record(new AuditEntry(AuditEventType.SENSITIVE_VIEW, "CERTIFICATE_PREPARE",
                "CERTIFICATE_ISSUANCE", issuanceId, (UUID) row.get("institutionId"), null,
                Map.of("print", true, "revoked", row.get("revoked")), 1, "P", "SUCCESS"));
        return html;
    }

    @Transactional
    public void overrideNoDues(TenantPrincipal principal, UUID requestId, String reason) {
        RequestRow row = requestRow(principal.tenantId(), requestId);
        authorize(principal, row.institutionId(), "NO_DUES_OVERRIDE", RbacTarget.NONE);
        if (reason == null || reason.isBlank() || reason.length() > 1000) {
            throw new BusinessRuleViolationException("OVERRIDE_REASON_REQUIRED", "A reason is required for a No-Dues override.");
        }
        if (!"NO_DUES_PENDING".equals(row.status())) {
            throw new BusinessRuleViolationException("NO_DUES_OVERRIDE_NOT_APPLICABLE",
                    "No-Dues override is available only for a request blocked by clearance.");
        }
        jdbc.update("""
                UPDATE app.certificate_request SET status='APPROVED',
                       no_dues_override_by_user_id=?,no_dues_override_at=now(),
                       no_dues_override_reason=?,updated_by=?,updated_at=now()
                 WHERE tenant_id=? AND certificate_request_id=?
                """, principal.userAccountId(), reason.trim(), principal.userAccountId(),
                principal.tenantId(), requestId);
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
        if (code == null || code.length() < 32 || code.length() > 256
                || !code.matches("[A-Za-z0-9_.-]+")) {
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
            String reason, String status, OffsetDateTime noDuesOverrideAt) { }

    private RequestRow requestRow(UUID tenantId, UUID requestId) {
        return jdbc.query("""
                SELECT institution_id,student_id,template_id,reason,status,no_dues_override_at
                  FROM app.certificate_request
                 WHERE tenant_id=? AND certificate_request_id=?
                """, rs -> rs.next() ? new RequestRow(rs.getObject(1, UUID.class),
                rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                rs.getString(4), rs.getString(5), rs.getObject(6, OffsetDateTime.class)) : null, tenantId, requestId);
    }

    private UUID studentUser(UUID studentId) {
        return jdbc.query("""
                SELECT user_account_id FROM app.student_profile
                 WHERE tenant_id=? AND student_profile_id=?
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                TenantContext.currentTenantId(), studentId);
    }

    private String serializeDues(NoDuesVerificationService.ClearanceResult clearance) {
        try { return mapper.writeValueAsString(clearance.items()); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Could not serialize No-Dues result.", ex); }
    }

    private void updateDues(TenantPrincipal principal, UUID requestId,
            NoDuesVerificationService.ClearanceResult clearance, String status) {
        try {
            jdbc.update("""
                    UPDATE app.certificate_request SET status=?,dues_json=?::jsonb,
                           updated_by=?,updated_at=now()
                     WHERE tenant_id=? AND certificate_request_id=?
                    """, status, mapper.writeValueAsString(clearance.items()), principal.userAccountId(),
                    principal.tenantId(), requestId);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize No-Dues result.", ex);
        }
    }

    @Transactional(readOnly = true)
    public NoDuesVerificationService.ClearanceResult verifyNoDues(TenantPrincipal principal, UUID studentId) {
        UUID institutionId = jdbc.query("""
                SELECT institution_id FROM app.student_profile
                 WHERE tenant_id=? AND student_profile_id=?
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, principal.tenantId(), studentId);
        if (institutionId == null) throw new ResourceNotFoundException("Student not found.");
        authorize(principal, institutionId, "CERTIFICATE_REQUEST", RbacTarget.NONE);
        return noDues.verifyClearance(studentId);
    }

    private RbacScope authorize(TenantPrincipal principal, UUID institutionId, String action, RbacTarget target) {
        RbacPrincipal caller = new RbacPrincipal(principal.tenantId(), principal.userAccountId());
        RbacScope scope = permissions.targetScope(caller, institutionId, null);
        permissions.authorize(caller, action, scope, target);
        return scope;
    }

    private String certificateCode(UUID tenantId, UUID issuanceId, String certificateNumber) {
        if (signingSecret == null || signingSecret.length() < 32) {
            throw new BusinessRuleViolationException("DOCUMENT_SIGNING_NOT_CONFIGURED",
                    "The certificate signing key is not configured with at least 32 characters.");
        }
        String payload = tenantId + "|" + issuanceId + "|" + certificateNumber;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String encoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return encoded + "." + signature;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not sign certificate verification code.", ex);
        }
    }

    private static String html(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
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

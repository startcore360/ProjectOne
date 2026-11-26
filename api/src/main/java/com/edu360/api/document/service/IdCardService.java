package com.edu360.api.document.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
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
public class IdCardService {
    private final JdbcTemplate jdbc;
    private final PermissionEvaluatorService permissions;
    private final AuditLogService audit;
    private final IdCardRenderer renderer;
    private final ObjectMapper mapper;
    private final String signingSecret;

    public IdCardService(JdbcTemplate jdbc, PermissionEvaluatorService permissions,
            AuditLogService audit, IdCardRenderer renderer, ObjectMapper mapper,
            @Value("${edu360.documents.signing-secret:}") String signingSecret) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.audit = audit;
        this.renderer = renderer;
        this.mapper = mapper;
        this.signingSecret = signingSecret;
    }

    public record Template(UUID templateId, UUID institutionId, String name, String layout,
            String backgroundImageRef, String fieldsConfigJson, String status, Integer rowVersion) { }
    public record Card(UUID issuanceId, UUID institutionId, UUID userId, String cardNumber,
            LocalDate issueDate, LocalDate expiryDate, String status, String qrToken,
            String fullName, String profileCode, String institutionName, String layout) { }
    public record Replacement(UUID replacementId, UUID originalIssuanceId, String reason,
            String status, UUID replacementIssuanceId) { }
    private record Profile(UUID userId, UUID institutionId, UUID studentProfileId,
            UUID staffProfileId, String fullName, String profileCode) { }

    @Transactional(readOnly = true)
    public List<Template> templates(TenantPrincipal principal, UUID institutionId) {
        authorize(principal, institutionId, "ID_CARD_ISSUE", RbacTarget.NONE);
        return jdbc.query("""
                SELECT id_card_template_id,institution_id,name,layout,background_image_ref,
                       fields_config_json::text,status,row_version
                  FROM app.id_card_template
                 WHERE tenant_id=? AND institution_id=?
                 ORDER BY name
                """, (rs, n) -> new Template(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getString(7), rs.getInt(8)), principal.tenantId(), institutionId);
    }

    @Transactional
    public Template createTemplate(TenantPrincipal principal, UUID institutionId, String name,
            String layout, String backgroundImageRef, String fieldsConfigJson) {
        authorize(principal, institutionId, "ID_CARD_TEMPLATE_MANAGE", RbacTarget.NONE);
        if (name == null || name.isBlank() || name.length() > 100
                || !List.of("PORTRAIT", "LANDSCAPE").contains(layout)) {
            throw new BusinessRuleViolationException("ID_CARD_TEMPLATE_INVALID", "Provide a template name and PORTRAIT or LANDSCAPE layout.");
        }
        JsonNode fields;
        try { fields = mapper.readTree(fieldsConfigJson == null ? "{}" : fieldsConfigJson); }
        catch (Exception ex) { throw new BusinessRuleViolationException("ID_CARD_TEMPLATE_INVALID", "Field configuration must be valid JSON."); }
        if (fields == null || !fields.isObject()) {
            throw new BusinessRuleViolationException("ID_CARD_TEMPLATE_INVALID", "Field configuration must be a JSON object.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app.id_card_template
                  (tenant_id,id_card_template_id,institution_id,name,layout,background_image_ref,
                   fields_config_json,status,created_by,updated_by)
                VALUES (?,?,?,?,?,?,?::jsonb,'ACTIVE',?,?)
                """, principal.tenantId(), id, institutionId, name.trim(), layout,
                backgroundImageRef, fields.toString(), principal.userAccountId(), principal.userAccountId());
        audit.record(new AuditEntry(AuditEventType.CREATE, "ID_CARD_TEMPLATE_MANAGE",
                "ID_CARD_TEMPLATE", id, institutionId, null, Map.of("layout", layout), 1, "P", "SUCCESS"));
        return new Template(id, institutionId, name.trim(), layout, backgroundImageRef,
                fields.toString(), "ACTIVE", 1);
    }

    @Transactional
    public Template updateTemplate(TenantPrincipal principal, UUID templateId, String name,
            String layout, String backgroundImageRef, String fieldsConfigJson, int expectedVersion) {
        Template existing = template(principal.tenantId(), templateId);
        authorize(principal, existing.institutionId(), "ID_CARD_TEMPLATE_MANAGE", RbacTarget.NONE);
        if (name == null || name.isBlank() || name.length() > 100
                || !List.of("PORTRAIT", "LANDSCAPE").contains(layout)) {
            throw new BusinessRuleViolationException("ID_CARD_TEMPLATE_INVALID", "Template name or layout is invalid.");
        }
        JsonNode fields;
        try { fields = mapper.readTree(fieldsConfigJson == null ? "{}" : fieldsConfigJson); }
        catch (Exception ex) { throw new BusinessRuleViolationException("ID_CARD_TEMPLATE_INVALID", "Field configuration must be valid JSON."); }
        if (fields == null || !fields.isObject()) throw new BusinessRuleViolationException("ID_CARD_TEMPLATE_INVALID", "Field configuration must be a JSON object.");
        int changed = jdbc.update("""
                UPDATE app.id_card_template SET name=?,layout=?,background_image_ref=?,fields_config_json=?::jsonb,
                       updated_by=?,updated_at=now()
                 WHERE tenant_id=? AND id_card_template_id=? AND row_version=?
                """, name.trim(), layout, backgroundImageRef, fields.toString(), principal.userAccountId(),
                principal.tenantId(), templateId, expectedVersion);
        if (changed == 0) throw new BusinessRuleViolationException("VERSION_CONFLICT", "The template changed; reload before saving.");
        audit.record(new AuditEntry(AuditEventType.EDIT, "ID_CARD_TEMPLATE_MANAGE",
                "ID_CARD_TEMPLATE", templateId, existing.institutionId(), Map.of("rowVersion", expectedVersion),
                Map.of("layout", layout), 1, "P", "SUCCESS"));
        return template(principal.tenantId(), templateId);
    }

    @Transactional
    public Card issue(TenantPrincipal principal, UUID institutionId, String subjectType,
            UUID profileId, UUID templateId, LocalDate expiryDate) {
        authorize(principal, institutionId, "ID_CARD_ISSUE", RbacTarget.NONE);
        Profile profile = profile(principal.tenantId(), institutionId, subjectType, profileId);
        return createIssuance(principal, institutionId, profile, templateId, expiryDate, "ID_CARD_ISSUE");
    }

    @Transactional(readOnly = true)
    public List<Card> cards(TenantPrincipal principal, UUID institutionId) {
        authorize(principal, institutionId, "ID_CARD_ISSUE", RbacTarget.NONE);
        List<UUID> ids = jdbc.query("""
                SELECT id_card_issuance_id FROM app.id_card_issuance
                 WHERE tenant_id=? AND institution_id=? ORDER BY issue_date DESC
                """, (rs, n) -> rs.getObject(1, UUID.class), principal.tenantId(), institutionId);
        return ids.stream().map(id -> card(principal.tenantId(), id)).toList();
    }

    @Transactional
    public Replacement requestReplacement(TenantPrincipal principal, UUID issuanceId,
            String reason, UUID feeReceiptFileId) {
        Card original = card(principal.tenantId(), issuanceId);
        RbacTarget target = RbacTarget.of(original.userId());
        boolean ownRequest = principal.userAccountId().equals(original.userId());
        authorize(principal, original.institutionId(),
                ownRequest ? "ID_CARD_REPLACEMENT_REQUEST" : "ID_CARD_REPLACE", target);
        if (reason == null || reason.isBlank() || reason.length() > 1000) {
            throw new BusinessRuleViolationException("REPLACEMENT_REASON_REQUIRED", "A replacement reason is required.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app.id_card_replacement
                  (tenant_id,id_card_replacement_id,institution_id,original_issuance_id,reason,
                   fee_receipt_file_id,status,created_by,updated_by)
                VALUES (?,?,?,?,?,?,'REQUESTED',?,?)
                """, principal.tenantId(), id, original.institutionId(), issuanceId, reason.trim(),
                feeReceiptFileId, principal.userAccountId(), principal.userAccountId());
        audit.record(new AuditEntry(AuditEventType.CREATE,
                ownRequest ? "ID_CARD_REPLACEMENT_REQUEST" : "ID_CARD_REPLACE",
                "ID_CARD_REPLACEMENT", id, original.institutionId(), null,
                Map.of("originalIssuanceId", issuanceId.toString()), 1, "P", "SUCCESS"));
        return new Replacement(id, issuanceId, reason.trim(), "REQUESTED", null);
    }

    @Transactional
    public Replacement decideReplacement(TenantPrincipal principal, UUID replacementId,
            boolean approve, String decisionNote) {
        Map<String, Object> row = jdbc.query("""
                SELECT r.original_issuance_id,r.reason,r.status,i.institution_id,i.user_id,
                       i.student_profile_id,i.staff_profile_id,i.template_id,i.expiry_date
                  FROM app.id_card_replacement r
                  JOIN app.id_card_issuance i ON i.tenant_id=r.tenant_id
                   AND i.id_card_issuance_id=r.original_issuance_id
                 WHERE r.tenant_id=? AND r.id_card_replacement_id=?
                """, rs -> rs.next() ? Map.of(
                "originalId", rs.getObject(1, UUID.class), "reason", rs.getString(2),
                "status", rs.getString(3), "institutionId", rs.getObject(4, UUID.class),
                "userId", rs.getObject(5, UUID.class), "studentId", rs.getObject(6, UUID.class) == null ? "" : rs.getObject(6, UUID.class).toString(),
                "staffId", rs.getObject(7, UUID.class) == null ? "" : rs.getObject(7, UUID.class).toString(),
                "templateId", rs.getObject(8, UUID.class),
                "expiryDate", rs.getObject(9, LocalDate.class) == null ? "" : rs.getObject(9, LocalDate.class).toString()) : null,
                principal.tenantId(), replacementId);
        if (row == null) throw new ResourceNotFoundException("Replacement request not found.");
        if (!"REQUESTED".equals(row.get("status"))) throw new BusinessRuleViolationException("REPLACEMENT_ALREADY_DECIDED", "This replacement request has already been decided.");
        UUID institutionId = (UUID) row.get("institutionId");
        UUID userId = (UUID) row.get("userId");
        authorize(principal, institutionId, "ID_CARD_REPLACE", RbacTarget.of(userId));
        if (decisionNote == null || decisionNote.isBlank() || decisionNote.length() > 1000) {
            throw new BusinessRuleViolationException("REPLACEMENT_DECISION_REQUIRED", "A decision note is required.");
        }
        UUID newIssuanceId = null;
        String status = "REJECTED";
        if (approve) {
            UUID originalId = (UUID) row.get("originalId");
            Map<String, Object> subject = jdbc.query("""
                    SELECT student_profile_id,staff_profile_id FROM app.id_card_issuance
                     WHERE tenant_id=? AND id_card_issuance_id=?
                    """, rs -> rs.next() ? Map.of(
                    "student", rs.getObject(1, UUID.class) == null ? "" : rs.getObject(1, UUID.class).toString(),
                    "staff", rs.getObject(2, UUID.class) == null ? "" : rs.getObject(2, UUID.class).toString()) : null,
                    principal.tenantId(), originalId);
            Profile profile = profile(principal.tenantId(), institutionId,
                    ((String) subject.get("student")).isBlank() ? "STAFF" : "STUDENT",
                    UUID.fromString(((String) subject.get("student")).isBlank()
                            ? (String) subject.get("staff") : (String) subject.get("student")));
            jdbc.update("UPDATE app.id_card_issuance SET status='REPLACED',updated_by=?,updated_at=now() WHERE tenant_id=? AND id_card_issuance_id=?",
                    principal.userAccountId(), principal.tenantId(), originalId);
            LocalDate expiry = ((String) row.get("expiryDate")).isBlank() ? null : LocalDate.parse((String) row.get("expiryDate"));
            Card newCard = createIssuance(principal, institutionId, profile, (UUID) row.get("templateId"), expiry, "ID_CARD_REPLACE");
            newIssuanceId = newCard.issuanceId();
            status = "COMPLETED";
        }
        jdbc.update("""
                UPDATE app.id_card_replacement SET status=?,reviewed_by_user_id=?,reviewed_at=now(),
                       replacement_issuance_id=?,updated_by=?,updated_at=now()
                 WHERE tenant_id=? AND id_card_replacement_id=?
                """, status, principal.userAccountId(), newIssuanceId, principal.userAccountId(),
                principal.tenantId(), replacementId);
        audit.record(new AuditEntry(AuditEventType.APPROVAL, "ID_CARD_REPLACE",
                "ID_CARD_REPLACEMENT", replacementId, institutionId, Map.of("status", "REQUESTED"),
                Map.of("status", status, "decision", decisionNote.trim()), 1, "P", "SUCCESS"));
        return new Replacement(replacementId, (UUID) row.get("originalId"),
                (String) row.get("reason"), status, newIssuanceId);
    }

    @Transactional
    public void revoke(TenantPrincipal principal, UUID issuanceId, String reason) {
        Card card = card(principal.tenantId(), issuanceId);
        authorize(principal, card.institutionId(), "ID_CARD_REVOKE", RbacTarget.of(card.userId()));
        if (reason == null || reason.isBlank() || reason.length() > 1000) {
            throw new BusinessRuleViolationException("REVOCATION_REASON_REQUIRED", "A revocation reason is required.");
        }
        jdbc.update("""
                UPDATE app.id_card_issuance SET status='REVOKED',updated_by=?,updated_at=now()
                 WHERE tenant_id=? AND id_card_issuance_id=? AND status='ACTIVE'
                """, principal.userAccountId(), principal.tenantId(), issuanceId);
        audit.record(new AuditEntry(AuditEventType.APPROVAL, "ID_CARD_REVOKE",
                "ID_CARD_ISSUANCE", issuanceId, card.institutionId(), Map.of("status", card.status()),
                Map.of("status", "REVOKED", "reason", reason.trim()), 1, "P", "SUCCESS"));
    }

    @Transactional(readOnly = true)
    public String svg(TenantPrincipal principal, UUID issuanceId) {
        Card card = card(principal.tenantId(), issuanceId);
        authorize(principal, card.institutionId(), "ID_CARD_EXPORT", RbacTarget.NONE);
        return renderer.toSvg(cardData(card), card.qrToken());
    }

    @Transactional(readOnly = true)
    public byte[] batchPdf(TenantPrincipal principal, UUID institutionId, List<UUID> issuanceIds) {
        if (issuanceIds == null || issuanceIds.isEmpty() || issuanceIds.size() > 50) {
            throw new BusinessRuleViolationException("ID_CARD_BATCH_LIMIT", "Select between 1 and 50 cards for a PDF batch.");
        }
        authorize(principal, institutionId, "ID_CARD_EXPORT", RbacTarget.NONE);
        List<Card> cards = issuanceIds.stream().map(id -> card(principal.tenantId(), id)).toList();
        if (cards.stream().anyMatch(card -> !institutionId.equals(card.institutionId()))) {
            throw new ResourceNotFoundException("One or more identity cards were not found.");
        }
        audit.record(new AuditEntry(AuditEventType.EXPORT, "ID_CARD_EXPORT",
                "ID_CARD_ISSUANCE", cards.getFirst().issuanceId(), institutionId, null,
                Map.of("recordCount", cards.size()), cards.size(), "P", "SUCCESS"));
        return renderer.toPdf(cards.stream().map(this::cardData).toList(),
                cards.stream().map(Card::qrToken).toList());
    }

    private Card createIssuance(TenantPrincipal principal, UUID institutionId, Profile profile,
            UUID templateId, LocalDate expiryDate, String actionCode) {
        Template template = template(principal.tenantId(), templateId);
        if (!institutionId.equals(template.institutionId()) || !"ACTIVE".equals(template.status())) {
            throw new ResourceNotFoundException("Active identity-card template not found.");
        }
        if (expiryDate != null && expiryDate.isBefore(LocalDate.now())) {
            throw new BusinessRuleViolationException("ID_CARD_EXPIRY_INVALID", "Expiry date must be today or later.");
        }
        UUID id = UUID.randomUUID();
        LocalDate issueDate = LocalDate.now();
        String cardNumber = "EDU-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String token = qrToken(principal.tenantId(), institutionId, profile.userId(), cardNumber, issueDate, expiryDate);
        String hash = sha256(token);
        jdbc.update("""
                INSERT INTO app.id_card_issuance
                  (tenant_id,id_card_issuance_id,institution_id,user_id,student_profile_id,staff_profile_id,
                   template_id,card_number,issue_date,expiry_date,qr_hash,status,created_by,updated_by)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,'ACTIVE',?,?)
                """, principal.tenantId(), id, institutionId, profile.userId(), profile.studentProfileId(),
                profile.staffProfileId(), templateId, cardNumber, issueDate, expiryDate, hash,
                principal.userAccountId(), principal.userAccountId());
        audit.record(new AuditEntry(AuditEventType.CREATE, actionCode, "ID_CARD_ISSUANCE",
                id, institutionId, null, Map.of("cardNumber", cardNumber), 1, "P", "SUCCESS"));
        return new Card(id, institutionId, profile.userId(), cardNumber, issueDate, expiryDate,
                "ACTIVE", token, profile.fullName(), profile.profileCode(),
                institutionName(principal.tenantId(), institutionId), template.layout());
    }

    private Card card(UUID tenantId, UUID issuanceId) {
        Card card = jdbc.query("""
                SELECT i.id_card_issuance_id,i.institution_id,i.user_id,i.card_number,i.issue_date,
                       i.expiry_date,i.status,COALESCE(sp.full_name,st.full_name) AS full_name,
                       COALESCE(sp.student_code,st.staff_code) AS profile_code,ins.institution_name,
                       t.layout,i.student_profile_id,i.staff_profile_id
                  FROM app.id_card_issuance i
                  LEFT JOIN app.student_profile sp ON sp.tenant_id=i.tenant_id AND sp.student_profile_id=i.student_profile_id
                  LEFT JOIN app.staff_profile st ON st.tenant_id=i.tenant_id AND st.staff_profile_id=i.staff_profile_id
                  JOIN app.institution ins ON ins.tenant_id=i.tenant_id AND ins.institution_id=i.institution_id
                  JOIN app.id_card_template t ON t.tenant_id=i.tenant_id AND t.id_card_template_id=i.template_id
                 WHERE i.tenant_id=? AND i.id_card_issuance_id=?
                """, rs -> rs.next() ? new Card(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getObject(3, UUID.class), rs.getString(4), rs.getObject(5, LocalDate.class),
                rs.getObject(6, LocalDate.class), rs.getString(7),
                qrToken(tenantId, rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                        rs.getString(4), rs.getObject(5, LocalDate.class), rs.getObject(6, LocalDate.class)),
                rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11)) : null,
                tenantId, issuanceId);
        if (card == null) throw new ResourceNotFoundException("Identity card not found.");
        return card;
    }

    private Profile profile(UUID tenantId, UUID institutionId, String subjectType, UUID profileId) {
        if ("STUDENT".equals(subjectType)) {
            Profile profile = jdbc.query("""
                    SELECT user_account_id,institution_id,student_profile_id,full_name,student_code
                      FROM app.student_profile
                     WHERE tenant_id=? AND institution_id=? AND student_profile_id=?
                    """, rs -> rs.next() ? new Profile(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                    rs.getObject(3, UUID.class), null, rs.getString(4), rs.getString(5)) : null,
                    tenantId, institutionId, profileId);
            if (profile == null) throw new ResourceNotFoundException("Student profile not found.");
            if (profile.userId() == null) throw new BusinessRuleViolationException("PROFILE_USER_NOT_LINKED", "The student must have a linked user account before a card can be issued.");
            return profile;
        }
        if ("STAFF".equals(subjectType)) {
            Profile profile = jdbc.query("""
                    SELECT sp.user_account_id,sa.institution_id,NULL::uuid,sp.staff_profile_id,sp.full_name,sp.staff_code
                      FROM app.staff_profile sp
                      JOIN app.staff_assignment sa ON sa.tenant_id=sp.tenant_id
                       AND sa.staff_profile_id=sp.staff_profile_id
                     WHERE sp.tenant_id=? AND sa.institution_id=? AND sp.staff_profile_id=?
                       AND sa.status='ACTIVE' AND sa.effective_from<=current_date
                       AND (sa.effective_to IS NULL OR sa.effective_to>=current_date)
                     ORDER BY sa.effective_from DESC LIMIT 1
                    """, rs -> rs.next() ? new Profile(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                    null, rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6)) : null,
                    tenantId, institutionId, profileId);
            if (profile == null) throw new ResourceNotFoundException("Staff profile not found in this institution.");
            if (profile.userId() == null) throw new BusinessRuleViolationException("PROFILE_USER_NOT_LINKED", "The staff member must have a linked user account before a card can be issued.");
            return profile;
        }
        throw new BusinessRuleViolationException("ID_CARD_SUBJECT_INVALID", "Subject type must be STUDENT or STAFF.");
    }

    private Template template(UUID tenantId, UUID templateId) {
        Template template = jdbc.query("""
                SELECT id_card_template_id,institution_id,name,layout,background_image_ref,
                       fields_config_json::text,status,row_version
                  FROM app.id_card_template WHERE tenant_id=? AND id_card_template_id=?
                """, rs -> rs.next() ? new Template(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getInt(8)) : null,
                tenantId, templateId);
        if (template == null) throw new ResourceNotFoundException("Identity-card template not found.");
        return template;
    }

    private String institutionName(UUID tenantId, UUID institutionId) {
        return jdbc.query("SELECT institution_name FROM app.institution WHERE tenant_id=? AND institution_id=?",
                rs -> rs.next() ? rs.getString(1) : "", tenantId, institutionId);
    }

    private IdCardRenderer.CardData cardData(Card card) {
        return new IdCardRenderer.CardData(card.cardNumber(), card.fullName(), card.profileCode(),
                card.institutionName(), card.issueDate().toString(),
                card.expiryDate() == null ? null : card.expiryDate().toString(), card.layout());
    }

    private void authorize(TenantPrincipal principal, UUID institutionId, String action, RbacTarget target) {
        RbacPrincipal caller = new RbacPrincipal(principal.tenantId(), principal.userAccountId());
        RbacScope scope = permissions.targetScope(caller, institutionId, null);
        permissions.authorize(caller, action, scope, target);
    }

    private String qrToken(UUID tenantId, UUID institutionId, UUID userId,
            String cardNumber, LocalDate issueDate, LocalDate expiryDate) {
        if (signingSecret == null || signingSecret.length() < 32) {
            throw new BusinessRuleViolationException("DOCUMENT_SIGNING_NOT_CONFIGURED",
                    "The identity-card signing key is not configured with at least 32 characters.");
        }
        String payload = String.join("|", tenantId.toString(), institutionId.toString(),
                userId.toString(), cardNumber, issueDate.toString(),
                expiryDate == null ? "" : expiryDate.toString());
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String encoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return encoded + "." + signature;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not sign identity-card QR payload.", ex);
        }
    }

    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("SHA-256 is unavailable.", ex); }
    }
}

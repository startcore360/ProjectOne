package com.edu360.api.platformops.role;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.DuplicateResourceException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.role.dto.PlatformActionCatalogItem;
import com.edu360.api.platformops.role.dto.PlatformRoleTemplateResponse;
import com.edu360.api.platformops.role.dto.SavePlatformRoleTemplateRequest;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.rbac.ActionCatalog;
import com.edu360.api.rbac.ActionCatalogRepository;
import com.edu360.api.rbac.StandardRoleAction;
import com.edu360.api.rbac.StandardRoleActionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Platform role templates. Fully editable within the limits in PlatformActionPolicy (TPA-03). A
 * change affects only roles created afterwards (OD-62). The locked Data Protection Officer template
 * is a read-only reference.
 */
@Service
@RequiredArgsConstructor
public class PlatformRoleTemplateService {

    private static final String ACTION = "PLATFORM_USER_MANAGE";
    private static final String ENTITY = "platform_role_template";
    private static final Set<String> SIGN_IN_ACTIONS =
            Set.of("AUTH_LOGIN", "AUTH_PASSWORD_CHANGE", "AUTH_DEVICES_MANAGE", "PROFILE_EDIT_OWN", "ROLE_SWITCH");

    private final PlatformRoleTemplateRepository templateRepository;
    private final PlatformRoleTemplateActionRepository actionRepository;
    private final StandardRoleActionRepository standardRoleActionRepository;
    private final ActionCatalogRepository actionCatalogRepository;
    private final PlatformAuditWriter auditWriter;

    /** Every action on the platform, for the picker. lockedReason marks those that can't be given. */
    public List<PlatformActionCatalogItem> actionCatalog() {
        return actionCatalogRepository.findAllById(PlatformPermissionService.knownActions()).stream()
                .sorted(Comparator.comparing(ActionCatalog::getModuleName).thenComparing(ActionCatalog::getActionNo))
                .map(action -> new PlatformActionCatalogItem(
                        action.getActionCode(),
                        action.getDescription(),
                        action.getModuleName(),
                        action.getSensitiveClass() != null || action.isApprovalAction(),
                        PlatformActionPolicy.reservedReason(action.getActionCode()).orElse(null)))
                .toList();
    }

    @Transactional(transactionManager = "platformTransactionManager", readOnly = true)
    public List<PlatformRoleTemplateResponse> listTemplates() {
        List<PlatformRoleTemplate> templates = templateRepository.findAllByOrderByTemplateNameAsc();
        Map<UUID, List<String>> actions = actionRepository
                .findByPlatformRoleTemplateIdIn(templates.stream().map(PlatformRoleTemplate::getPlatformRoleTemplateId).toList())
                .stream()
                .collect(Collectors.groupingBy(PlatformRoleTemplateAction::getPlatformRoleTemplateId,
                        Collectors.mapping(PlatformRoleTemplateAction::getActionCode, Collectors.toList())));
        return templates.stream()
                .map(t -> toResponse(t, actions.getOrDefault(t.getPlatformRoleTemplateId(), List.of())))
                .toList();
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public PlatformRoleTemplateResponse createTemplate(SavePlatformRoleTemplateRequest request, PlatformPrincipal actor) {
        String name = request.templateName().trim();
        validate(request);
        if (templateRepository.existsByTemplateNameIgnoreCase(name)) {
            throw new DuplicateResourceException("A template named '" + name + "' already exists.");
        }
        UUID id = UUID.randomUUID();
        PlatformRoleTemplate template = templateRepository.save(PlatformRoleTemplate.builder()
                .platformRoleTemplateId(id)
                .templateName(name)
                .description(request.description())
                .basePlatformRole(request.basePlatformRole())
                .standard(false)
                .locked(false)
                .status("ACTIVE")
                .createdBy(actor.platformUserId())
                .updatedBy(actor.platformUserId())
                .build());
        saveActions(id, request.actionCodes(), actor);
        auditWriter.record(actor, "CREATE", ACTION, ENTITY, id);
        return toResponse(template, request.actionCodes());
    }

    @Transactional(transactionManager = "platformTransactionManager")
    public PlatformRoleTemplateResponse updateTemplate(UUID id, SavePlatformRoleTemplateRequest request, PlatformPrincipal actor) {
        PlatformRoleTemplate template = requireUsable(id);
        String name = request.templateName().trim();
        validate(request);
        if (templateRepository.existsByTemplateNameIgnoreCaseAndPlatformRoleTemplateIdNot(name, id)) {
            throw new DuplicateResourceException("A template named '" + name + "' already exists.");
        }
        template.setTemplateName(name);
        template.setDescription(request.description());
        template.setBasePlatformRole(request.basePlatformRole());
        template.setUpdatedBy(actor.platformUserId());
        templateRepository.save(template);
        replaceRows(id, request.actionCodes(), actor);
        auditWriter.record(actor, "UPDATE", ACTION, ENTITY, id);
        return toResponse(template, request.actionCodes());
    }

    /** Saves a role's permissions as this template's new suggestion (OD-62). */
    @Transactional(transactionManager = "platformTransactionManager")
    public void replaceActions(UUID id, List<String> actionCodes, PlatformPrincipal actor) {
        PlatformRoleTemplate template = requireUsable(id);
        PlatformActionPolicy.validate(actionCodes, template.getBasePlatformRole());
        template.setUpdatedBy(actor.platformUserId());
        templateRepository.save(template);
        replaceRows(id, actionCodes, actor);
        auditWriter.record(actor, "UPDATE", ACTION, ENTITY, id);
    }

    /** Puts a standard template back to the rows in ref.standard_role_action. */
    @Transactional(transactionManager = "platformTransactionManager")
    public PlatformRoleTemplateResponse resetTemplate(UUID id, PlatformPrincipal actor) {
        PlatformRoleTemplate template = requireUsable(id);
        if (!template.isStandard()) {
            throw new BusinessRuleViolationException("Only a standard template can be reset.");
        }
        List<String> actionCodes = standardRoleActionRepository
                .findByRoleCode(template.getBasePlatformRole().name()).stream()
                .map(StandardRoleAction::getActionCode)
                .filter(action -> !SIGN_IN_ACTIONS.contains(action))
                .toList();
        template.setUpdatedBy(actor.platformUserId());
        templateRepository.save(template);
        replaceRows(id, actionCodes, actor);
        auditWriter.record(actor, "UPDATE", ACTION, ENTITY, id);
        return toResponse(template, actionCodes);
    }

    /** Existing roles keep their permissions (rule 17: history is kept, nothing is deleted). */
    @Transactional(transactionManager = "platformTransactionManager")
    public PlatformRoleTemplateResponse archiveTemplate(UUID id, PlatformPrincipal actor) {
        PlatformRoleTemplate template = requireUsable(id);
        template.setStatus("ARCHIVED");
        template.setUpdatedBy(actor.platformUserId());
        templateRepository.save(template);
        auditWriter.record(actor, "UPDATE", ACTION, ENTITY, id);
        return toResponse(template, actionRepository.findByPlatformRoleTemplateId(id).stream()
                .map(PlatformRoleTemplateAction::getActionCode).toList());
    }

    PlatformRoleTemplate requireUsable(UUID id) {
        PlatformRoleTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template " + id + " was not found."));
        if (template.isLocked()) {
            throw new BusinessRuleViolationException("The " + template.getTemplateName()
                    + " template is locked. Assign the standard role instead.");
        }
        if (!"ACTIVE".equals(template.getStatus())) {
            throw new BusinessRuleViolationException("This template is archived.");
        }
        return template;
    }

    private void validate(SavePlatformRoleTemplateRequest request) {
        PlatformActionPolicy.requireCustomisable(request.basePlatformRole());
        Set<String> unique = new HashSet<>(request.actionCodes());
        if (unique.size() != request.actionCodes().size()) {
            throw new BusinessRuleViolationException("A permission is listed more than once.");
        }
        PlatformActionPolicy.validate(unique, request.basePlatformRole());
    }

    private void replaceRows(UUID id, List<String> actionCodes, PlatformPrincipal actor) {
        actionRepository.deleteByPlatformRoleTemplateId(id);
        actionRepository.flush();
        saveActions(id, actionCodes, actor);
    }

    private void saveActions(UUID id, List<String> actionCodes, PlatformPrincipal actor) {
        actionRepository.saveAll(actionCodes.stream()
                .map(code -> PlatformRoleTemplateAction.builder()
                        .platformRoleTemplateActionId(UUID.randomUUID())
                        .platformRoleTemplateId(id)
                        .actionCode(code)
                        .createdBy(actor.platformUserId())
                        .build())
                .toList());
    }

    private PlatformRoleTemplateResponse toResponse(PlatformRoleTemplate t, List<String> actions) {
        return new PlatformRoleTemplateResponse(t.getPlatformRoleTemplateId(), t.getTemplateName(), t.getDescription(),
                t.getBasePlatformRole(), t.isStandard(), t.isLocked(), t.getStatus(), actions.stream().sorted().toList());
    }
}

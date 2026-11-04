package com.edu360.api.rbac.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * WHAT: the request body shared by USER_DEACTIVATE, and the two USER_PASSWORD_RESET_UNLOCK
 * operations (password-reset, unlock) - all three are specified as "Own institution" scope only
 * (sql/05_ref_data.sql ref.standard_role_action rows for these action codes), unlike USER_VIEW/
 * USER_EDIT which allow any scope shape. institutionId is therefore required here, never
 * optional, and the evaluator is always called with RbacScope.institution(institutionId) - never
 * RbacScope.tenant()/department() - for these three operations.
 */
public record InstitutionScopeRequest(@NotNull UUID institutionId) {
}

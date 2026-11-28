package com.edu360.api.platformops.dto;

/**
 * Who the first user of a new school or college tenant is, chosen when the platform admin creates it.
 *
 * ADMIN: School Admin / College Admin (University Admin for a university). The tenant owner who runs
 * the institution and hires a Principal later. Available now.
 *
 * PRINCIPAL_ADMIN: one account that is both owner and Principal, holding both permission lists. It needs
 * a combined standard role that every role-keyed rule (approval routing, result publishing, leave
 * approvers, confidential grievances) knows about, so it is deliberately NOT available yet and the
 * API refuses it until that role exists. The value is accepted by the contract now so the screen and
 * API do not change again when it arrives.
 */
public enum FirstUserRole {
    ADMIN,
    PRINCIPAL_ADMIN
}

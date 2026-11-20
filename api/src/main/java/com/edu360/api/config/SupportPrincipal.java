package com.edu360.api.config;

import java.util.UUID;

/** Authenticated identity for a tenant-scoped, ticket-bound support session.
 *  It deliberately carries no tenant role or authorities. */
public record SupportPrincipal(
        UUID platformUserId,
        UUID tenantId,
        UUID ticketId,
        UUID grantId,
        UUID sessionId,
        UUID institutionId,
        String email) {
}

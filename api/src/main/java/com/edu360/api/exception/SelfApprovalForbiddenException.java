package com.edu360.api.exception;

/**
 * Addendum rule 11 / D1 section 7: nobody decides their own request. Still a business-rule
 * violation by type (so existing callers keep working) but answered with 403 (P-01, report
 * Part 4: "the requester: 403").
 */
public class SelfApprovalForbiddenException extends BusinessRuleViolationException {
    public SelfApprovalForbiddenException(String message) {
        super("SELF_APPROVAL_FORBIDDEN", message);
    }
}

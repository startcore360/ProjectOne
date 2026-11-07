package com.edu360.api.guardian.dto;

public record GuardianChangeDecisionRequest(
        boolean approved,
        String decisionNotes
) {
}

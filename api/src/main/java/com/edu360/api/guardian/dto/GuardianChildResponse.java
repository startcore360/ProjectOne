package com.edu360.api.guardian.dto;

import java.util.UUID;

/** Minimal data for a parent's child switcher. */
public record GuardianChildResponse(UUID studentProfileId, UUID institutionId, String studentCode, String status) {}

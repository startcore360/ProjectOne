package com.edu360.api.institution;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

public record EndStaffAssignmentRequest(@NotNull LocalDate effectiveTo) {}

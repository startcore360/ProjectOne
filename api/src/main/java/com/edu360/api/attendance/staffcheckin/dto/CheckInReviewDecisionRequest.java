package com.edu360.api.attendance.staffcheckin.dto;

/** {@code reason} is mandatory for a reject decision; optional for approve. */
public record CheckInReviewDecisionRequest(String reason) {
}

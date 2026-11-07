package com.edu360.api.platformops.dto;

public record TenantActivationRequest(
        String paymentReference,
        String note) {
}

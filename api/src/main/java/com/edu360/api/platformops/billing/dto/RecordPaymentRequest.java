package com.edu360.api.platformops.billing.dto;

public record RecordPaymentRequest(
        String paymentMode,
        String paymentReference
) {}

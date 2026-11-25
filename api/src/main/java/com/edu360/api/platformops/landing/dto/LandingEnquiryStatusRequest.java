package com.edu360.api.platformops.landing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LandingEnquiryStatusRequest(
        @NotBlank @Pattern(regexp = "NEW|CONTACTED|QUALIFIED|CLOSED") String status,
        @Size(max = 2000) String internalNotes
) { }

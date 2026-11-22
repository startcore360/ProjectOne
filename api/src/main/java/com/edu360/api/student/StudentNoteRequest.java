package com.edu360.api.student;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StudentNoteRequest(
        @NotBlank String category,
        @NotBlank @Size(max = 5000) String body,
        String correctsNoteId,
        String deleteAfter
) {}
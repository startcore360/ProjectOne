package com.edu360.api.selfservice.dto;

import java.time.Instant;
import java.util.UUID;

public record NoticeDto(
        UUID noticeId,
        String noticeLevel,
        String noticeKind,
        String title,
        String body,
        String authoredActiveRole,
        Instant publishedAt
) {}

package com.edu360.api.audit;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * A tiny helper used across the app to write one audit-log row. Other code
 * (like the login logic) calls record(...) instead of building an AuditLog
 * by hand, so logging an event is always a single, consistent line.
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public void record(AuditEventType eventType, Long userId, String email, String ipAddress, String details) {
        auditLogRepository.save(AuditLog.builder()
                .eventType(eventType)
                .userId(userId)
                .email(email)
                .ipAddress(ipAddress)
                .details(details)
                .build());
    }
}

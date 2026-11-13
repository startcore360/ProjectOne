package com.edu360.api.approval;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.edu360.api.live.LiveEventPublisher;

class ApprovalCountPublisherTest {

    @Test
    void publishesCurrentPendingCountToEachResolvedApprover() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LiveEventPublisher liveEvents = mock(LiveEventPublisher.class);
        UUID tenantId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        UUID firstApprover = UUID.randomUUID();
        UUID secondApprover = UUID.randomUUID();
        when(jdbc.queryForList(anyString(), eq(UUID.class), any(Object[].class)))
                .thenReturn(List.of(firstApprover, secondApprover));
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(4L);

        new ApprovalCountPublisher(jdbc, liveEvents).publishForRole(tenantId, institutionId, "hod");

        verify(liveEvents).publishToUser(eq(tenantId), eq(firstApprover), eq("APPROVAL_COUNT"),
                eq(java.util.Map.of("pendingCount", 4L)));
        verify(liveEvents).publishToUser(eq(tenantId), eq(secondApprover), eq("APPROVAL_COUNT"),
                eq(java.util.Map.of("pendingCount", 4L)));
        verify(jdbc, times(2)).queryForObject(anyString(), eq(Long.class), any(Object[].class));
    }

    @Test
    void skipsInvalidRoleScopeWithoutQuerying() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LiveEventPublisher liveEvents = mock(LiveEventPublisher.class);

        ApprovalCountPublisher publisher = new ApprovalCountPublisher(jdbc, liveEvents);
        publisher.publishForRole(null, UUID.randomUUID(), "HOD");
        publisher.publishForRole(UUID.randomUUID(), UUID.randomUUID(), " ");

        verifyNoInteractions(jdbc, liveEvents);
    }

    @Test
    void recipientLookupFailureDoesNotEscapeApprovalFlow() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LiveEventPublisher liveEvents = mock(LiveEventPublisher.class);
        when(jdbc.queryForList(anyString(), eq(UUID.class), any(Object[].class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        new ApprovalCountPublisher(jdbc, liveEvents)
                .publishForRole(UUID.randomUUID(), UUID.randomUUID(), "HOD");

        verifyNoInteractions(liveEvents);
    }

    @Test
    void countFailureForOneApproverDoesNotEscapeOrPublish() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LiveEventPublisher liveEvents = mock(LiveEventPublisher.class);
        UUID userId = UUID.randomUUID();
        when(jdbc.queryForList(anyString(), eq(UUID.class), any(Object[].class)))
                .thenReturn(List.of(userId));
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenThrow(new IllegalStateException("count unavailable"));

        new ApprovalCountPublisher(jdbc, liveEvents)
                .publishForRole(UUID.randomUUID(), UUID.randomUUID(), "HOD");

        verifyNoInteractions(liveEvents);
    }
}

package com.edu360.api.institution;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(schema = "app", name = "staff_service_record")
@Getter
@Setter
@NoArgsConstructor
public class StaffServiceRecord {
    @EmbeddedId
    private StaffServiceRecordId id;
    @Column(name = "staff_profile_id", nullable = false)
    private UUID staffProfileId;
    @Column(name = "event_type", nullable = false)
    private String eventType;
    @Column(name = "effective_on", nullable = false)
    private LocalDate effectiveOn;
    @Column(name = "details", nullable = false)
    private String details;
    @Column(name = "staff_assignment_id")
    private UUID staffAssignmentId;
    @Column(name = "document_file_id")
    private UUID documentFileId;
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;
    @Column(name = "row_version", insertable = false, updatable = false)
    private Integer rowVersion;
}

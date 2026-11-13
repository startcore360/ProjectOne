package com.edu360.api.institution;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class StaffDocumentId implements Serializable {
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "staff_document_id", nullable = false)
    private UUID staffDocumentId;
}

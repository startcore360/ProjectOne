package com.edu360.api.academic;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubjectAllocationId implements Serializable {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_allocation_id", nullable = false)
    private UUID subjectAllocationId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SubjectAllocationId that)) return false;
        return Objects.equals(tenantId, that.tenantId) && Objects.equals(subjectAllocationId, that.subjectAllocationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, subjectAllocationId);
    }
}

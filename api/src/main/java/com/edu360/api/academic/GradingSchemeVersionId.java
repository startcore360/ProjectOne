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
public class GradingSchemeVersionId implements Serializable {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "grading_scheme_version_id", nullable = false)
    private UUID gradingSchemeVersionId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GradingSchemeVersionId that)) return false;
        return Objects.equals(tenantId, that.tenantId) && Objects.equals(gradingSchemeVersionId, that.gradingSchemeVersionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, gradingSchemeVersionId);
    }
}

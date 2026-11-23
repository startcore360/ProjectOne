package com.edu360.api.institution;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ProgramCurriculumVersionId implements Serializable {
    private UUID tenantId;
    private UUID programCurriculumVersionId;

    public ProgramCurriculumVersionId() {}
    public ProgramCurriculumVersionId(UUID tenantId, UUID programCurriculumVersionId) {
        this.tenantId = tenantId;
        this.programCurriculumVersionId = programCurriculumVersionId;
    }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getProgramCurriculumVersionId() { return programCurriculumVersionId; }
    public void setProgramCurriculumVersionId(UUID programCurriculumVersionId) { this.programCurriculumVersionId = programCurriculumVersionId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ProgramCurriculumVersionId that)) return false;
        return Objects.equals(tenantId, that.tenantId)
                && Objects.equals(programCurriculumVersionId, that.programCurriculumVersionId);
    }
    @Override public int hashCode() { return Objects.hash(tenantId, programCurriculumVersionId); }
}

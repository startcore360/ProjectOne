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
public class ProgramRuleSetId implements Serializable {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "program_rule_set_id", nullable = false)
    private UUID programRuleSetId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProgramRuleSetId that)) return false;
        return Objects.equals(tenantId, that.tenantId) && Objects.equals(programRuleSetId, that.programRuleSetId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, programRuleSetId);
    }
}

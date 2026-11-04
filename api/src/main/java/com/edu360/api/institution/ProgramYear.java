package com.edu360.api.institution;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * WHAT: a minimal, read-only mapping of app.program_year - see Semester's javadoc for why this
 * exists (one link in the semester -> program_year -> program -> department_id chain used by
 * MENTOR_ASSIGN).
 */
@Entity
@Table(schema = "app", name = "program_year")
@Getter
@Setter
@NoArgsConstructor
public class ProgramYear {

    @Id
    @Column(name = "program_year_id")
    private UUID programYearId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "program_id", nullable = false)
    private UUID programId;
}

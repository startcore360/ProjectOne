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
 * WHAT: a minimal, read-only mapping of app.program - see Semester's javadoc for why this exists
 * (last link in the semester -> program_year -> program -> department_id chain used by
 * MENTOR_ASSIGN to find which department owns a college section).
 */
@Entity
@Table(schema = "app", name = "program")
@Getter
@Setter
@NoArgsConstructor
public class Program {

    @Id
    @Column(name = "program_id")
    private UUID programId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;
}

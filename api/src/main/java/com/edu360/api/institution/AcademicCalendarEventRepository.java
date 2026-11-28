package com.edu360.api.institution;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicCalendarEventRepository extends JpaRepository<AcademicCalendarEvent, UUID> {

    List<AcademicCalendarEvent> findByTenantIdAndInstitutionIdOrderByStartDateAsc(UUID tenantId, UUID institutionId);

    List<AcademicCalendarEvent> findByTenantIdAndSourceLevelOrderByStartDateAsc(UUID tenantId, String sourceLevel);

    List<AcademicCalendarEvent> findByTenantIdAndInstitutionIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            UUID tenantId, UUID institutionId, LocalDate startDate, LocalDate endDate);

    List<AcademicCalendarEvent> findByTenantIdAndInstitutionIdIsNullAndSourceLevelAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            UUID tenantId, String sourceLevel, LocalDate startDate, LocalDate endDate);

    List<AcademicCalendarEvent> findByTenantIdAndAcademicSessionIdAndSourceLevelOrderByStartDateAsc(
            UUID tenantId, UUID academicSessionId, String sourceLevel);
}

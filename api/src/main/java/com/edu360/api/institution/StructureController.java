package com.edu360.api.institution;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.academic.ProgramRuleSetResponse;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.institution.dto.AcademicSessionResponse;
import com.edu360.api.institution.dto.CourseOfferingResponse;
import com.edu360.api.institution.dto.CreateCourseOfferingRequest;
import com.edu360.api.institution.dto.CreateDepartmentRequest;
import com.edu360.api.institution.dto.CreateProgramRequest;
import com.edu360.api.institution.dto.CreateProgramYearRequest;
import com.edu360.api.institution.dto.CreateSchoolClassRequest;
import com.edu360.api.institution.dto.CreateSectionRequest;
import com.edu360.api.institution.dto.CreateSemesterRequest;
import com.edu360.api.institution.dto.CreateSubjectRequest;
import com.edu360.api.institution.dto.DepartmentResponse;
import com.edu360.api.institution.dto.ProgramResponse;
import com.edu360.api.institution.dto.ProgramYearResponse;
import com.edu360.api.institution.dto.SchoolClassResponse;
import com.edu360.api.institution.dto.SectionResponse;
import com.edu360.api.institution.dto.SemesterResponse;
import com.edu360.api.institution.dto.SubjectResponse;
import com.edu360.api.rbac.RbacPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controller exposing STRUCTURE_MANAGE (Action Code #26) operations:
 * - Departments: create & list
 * - Programs: create & list
 * - Program Years & Semesters: create & list
 * - Classes & Sections: create & list
 * - Subjects & Course Offerings: create & list
 * - Academic Sessions & Program Rule Sets: list
 */
@RestController
@RequestMapping({
        "/v1/tenants/{tenantId}/institutions/{institutionId}",
        "/api/tenants/{tenantId}/institutions/{institutionId}"
})
@RequiredArgsConstructor
@Tag(name = "Institution setup", description = "Academic structure management (STRUCTURE_MANAGE)")
public class StructureController {

    private final StructureService structureService;

    @PostMapping("/departments")
    @Operation(summary = "Create an academic department (STRUCTURE_MANAGE, Colleges only per Rule HI-12)")
    public ResponseEntity<DepartmentResponse> createDepartment(
            @PathVariable UUID tenantId,
            @PathVariable UUID institutionId,
            @Valid @RequestBody CreateDepartmentRequest request,
            Authentication authentication) {

        RbacPrincipal principal = requireTenantPrincipal(tenantId, authentication);
        DepartmentResponse response = structureService.createDepartment(principal, institutionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/departments")
    @Operation(summary = "List academic departments for an institution (STRUCTURE_MANAGE)")
    public ResponseEntity<List<DepartmentResponse>> listDepartments(
            @PathVariable UUID tenantId,
            @PathVariable UUID institutionId,
            Authentication authentication) {

        RbacPrincipal principal = requireTenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(structureService.listDepartments(principal, institutionId));
    }

    @PostMapping("/programs")
    @Operation(summary = "Create a degree program under a department (STRUCTURE_MANAGE)")
    public ResponseEntity<ProgramResponse> createProgram(
            @PathVariable UUID tenantId,
            @PathVariable UUID institutionId,
            @Valid @RequestBody CreateProgramRequest request,
            Authentication authentication) {

        RbacPrincipal principal = requireTenantPrincipal(tenantId, authentication);
        ProgramResponse response = structureService.createProgram(principal, institutionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/programs")
    @Operation(summary = "List degree programs for an institution (STRUCTURE_MANAGE)")
    public ResponseEntity<List<ProgramResponse>> listPrograms(
            @PathVariable UUID tenantId,
            @PathVariable UUID institutionId,
            Authentication authentication) {

        RbacPrincipal principal = requireTenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(structureService.listPrograms(principal, institutionId));
    }

    @PostMapping("/programs/{programId}/years")
    @Operation(summary = "Create a program year (STRUCTURE_MANAGE)")
    public ResponseEntity<ProgramYearResponse> createProgramYear(
            @PathVariable UUID tenantId,
            @PathVariable UUID institutionId,
            @PathVariable UUID programId,
            @Valid @RequestBody CreateProgramYearRequest request,
            Authentication authentication) {

        RbacPrincipal principal = requireTenantPrincipal(tenantId, authentication);
        ProgramYearResponse response = structureService.createProgramYear(principal, institutionId, programId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/programs/{programId}/years")
    @Operation(summary = "List program years for a program (STRUCTURE_MANAGE)")
    public ResponseEntity<List<ProgramYearResponse>> listProgramYears(
            @PathVariable UUID tenantId,
            @PathVariable UUID institutionId,
            @PathVariable UUID programId,
            Authentication authentication) {

        RbacPrincipal principal = requireTenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(structureService.listProgramYears(principal, institutionId, programId));
    }

    @PostMapping("/program-years/{programYearId}/semesters")
    @Operation(summary = "Create a semester under a program year (STRUCTURE_MANAGE)")
    public ResponseEntity<SemesterResponse> createSemester(
            @PathVariable UUID tenantId,
            @PathVariable UUID institutionId,
            @PathVariable UUID programYearId,
            @Valid @RequestBody CreateSemesterRequest request,
            Authentication authentication) {

        RbacPrincipal principal = requireTenantPrincipal(tenantId, authentication);
        SemesterResponse response = structureService.createSemester(principal, institutionId, programYearId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/program-years/{programYearId}/semesters")
    @Operation(summary = "List semesters for a program year (STRUCTURE_MANAGE)")
    public ResponseEntity<List<SemesterResponse>> listSemesters(
            @PathVariable UUID tenantId,
            @PathVariable UUID institutionId,
            @PathVariable UUID programYearId,
            Authentication authentication) {

        RbacPrincipal principal = requireTenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(structureService.listSemesters(principal, institutionId, programYearId));
    }

    @PostMapping("/classes")
    @Operation(summary = "Create a school class (STRUCTURE_MANAGE, Schools only)")
    public ResponseEntity<SchoolClassResponse> createSchoolClass(
            @PathVariable UUID tenantId,
            @PathVariable UUID institutionId,
            @Valid @RequestBody CreateSchoolClassRequest request,
            Authentication authentication) {

        RbacPrincipal principal = requireTenantPrincipal(tenantId, authentication);
        SchoolClassResponse response = structureService.createSchoolClass(principal, institutionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/classes")
    @Operation(summary = "List school classes for a school (STRUCTURE_MANAGE)")
    public ResponseEntity<List<SchoolClassResponse>> listSchoolClasses(
            @PathVariable UUID tenantId,
            @PathVariable UUID institutionId,
            Authentication authentication) {

        RbacPrincipal principal = requireTenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(structureService.listSchoolClasses(principal, institutionId));
    }

    @PostMapping("/sections")
    @Operation(summary = "Create a section (STRUCTURE_MANAGE)")
    public ResponseEntity<SectionResponse> createSection(
            @PathVariable UUID tenantId,
            @PathVariable UUID institutionId,
            @Valid @RequestBody CreateSectionRequest request,
            Authentication authentication) {

        RbacPrincipal principal = requireTenantPrincipal(tenantId, authentication);
        SectionResponse response = structureService.createSection(principal, institutionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/sections")
    @Operation(summary = "List sections for an institution (STRUCTURE_MANAGE)")
    public ResponseEntity<List<SectionResponse>> listSections(
            @PathVariable UUID tenantId,
            @PathVariable UUID institutionId,
            @RequestParam(required = false) UUID academicSessionId,
            @RequestParam(required = false) UUID semesterId,
            @RequestParam(required = false) UUID classId,
            Authentication authentication) {

        RbacPrincipal principal = requireTenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(structureService.listSections(principal, institutionId, academicSessionId, semesterId, classId));
    }

    @PostMapping("/subjects")
    @Operation(summary = "Create a subject (STRUCTURE_MANAGE)")
    public ResponseEntity<SubjectResponse> createSubject(
            @PathVariable UUID tenantId,
            @PathVariable UUID institutionId,
            @Valid @RequestBody CreateSubjectRequest request,
            Authentication authentication) {

        RbacPrincipal principal = requireTenantPrincipal(tenantId, authentication);
        SubjectResponse response = structureService.createSubject(principal, institutionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/subjects")
    @Operation(summary = "List subjects for an institution (STRUCTURE_MANAGE)")
    public ResponseEntity<List<SubjectResponse>> listSubjects(
            @PathVariable UUID tenantId,
            @PathVariable UUID institutionId,
            Authentication authentication) {

        RbacPrincipal principal = requireTenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(structureService.listSubjects(principal, institutionId));
    }

    @PostMapping("/course-offerings")
    @Operation(summary = "Create a course offering (STRUCTURE_MANAGE)")
    public ResponseEntity<CourseOfferingResponse> createCourseOffering(
            @PathVariable UUID tenantId,
            @PathVariable UUID institutionId,
            @Valid @RequestBody CreateCourseOfferingRequest request,
            Authentication authentication) {

        RbacPrincipal principal = requireTenantPrincipal(tenantId, authentication);
        CourseOfferingResponse response = structureService.createCourseOffering(principal, institutionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/course-offerings")
    @Operation(summary = "List course offerings for an institution (STRUCTURE_MANAGE)")
    public ResponseEntity<List<CourseOfferingResponse>> listCourseOfferings(
            @PathVariable UUID tenantId,
            @PathVariable UUID institutionId,
            @RequestParam(required = false) UUID academicSessionId,
            Authentication authentication) {

        RbacPrincipal principal = requireTenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(structureService.listCourseOfferings(principal, institutionId, academicSessionId));
    }

    @GetMapping("/academic-sessions")
    @Operation(summary = "List academic sessions for this institution (STRUCTURE_MANAGE)")
    public ResponseEntity<List<AcademicSessionResponse>> listAcademicSessions(
            @PathVariable UUID tenantId,
            @PathVariable UUID institutionId,
            Authentication authentication) {

        RbacPrincipal principal = requireTenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(structureService.listAcademicSessions(principal, institutionId));
    }

    @GetMapping("/program-rule-sets")
    @Operation(summary = "List program rule sets for this institution (STRUCTURE_MANAGE)")
    public ResponseEntity<List<ProgramRuleSetResponse>> listProgramRuleSets(
            @PathVariable UUID tenantId,
            @PathVariable UUID institutionId,
            Authentication authentication) {

        RbacPrincipal principal = requireTenantPrincipal(tenantId, authentication);
        return ResponseEntity.ok(structureService.listProgramRuleSets(principal, institutionId));
    }

    private RbacPrincipal requireTenantPrincipal(UUID tenantId, Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new UnauthorizedException("STRUCTURE_MANAGE is a tenant-side action; no tenant principal on this request.");
        }
        if (!tenantId.equals(principal.tenantId())) {
            throw new UnauthorizedException("tenantId in the URL does not match the authenticated tenant.");
        }
        return new RbacPrincipal(principal.tenantId(), principal.userAccountId());
    }
}

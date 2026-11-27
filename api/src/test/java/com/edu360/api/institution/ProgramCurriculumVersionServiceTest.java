package com.edu360.api.institution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.institution.dto.CreateCurriculumVersionRequest;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ProgramCurriculumVersionServiceTest {
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID INSTITUTION = UUID.randomUUID();
    private static final UUID PROGRAM = UUID.randomUUID();

    @Mock private ProgramCurriculumVersionRepository versionRepository;
    @Mock private ProgramRepository programRepository;
    @Mock private PermissionEvaluatorService permissionEvaluatorService;
    @Mock private AuditLogService auditLogService;

    private ProgramCurriculumVersionService service;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new ProgramCurriculumVersionService(versionRepository, programRepository,
                permissionEvaluatorService, auditLogService, mapper);
    }

    @Test
    void create_appendsNextVersionAndAuditsImmutableSnapshot() throws Exception {
        Program program = Program.builder().programId(PROGRAM).tenantId(TENANT)
                .institutionId(INSTITUTION).programCode("BTECH").status("ACTIVE").build();
        when(programRepository.findForUpdate(TENANT, PROGRAM)).thenReturn(Optional.of(program));
        when(versionRepository.findMaxVersionNumber(TENANT, PROGRAM)).thenReturn(2);
        when(versionRepository.save(any(ProgramCurriculumVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> jsonMap = Map.of("courses", List.of(Map.of("code", "CS101", "credits", 4)));
        var request = new CreateCurriculumVersionRequest(
                jsonMap,
                LocalDate.parse("2026-07-01"), null);
        var response = service.create(new RbacPrincipal(TENANT, USER), INSTITUTION, PROGRAM, request);

        assertThat(response.versionNumber()).isEqualTo(3);
        assertThat(response.curriculumJson().path("courses").get(0).path("code").asText()).isEqualTo("CS101");
        verify(permissionEvaluatorService).authorize(any(), eq("STRUCTURE_MANAGE"),
                eq(RbacScope.institution(INSTITUTION)), eq(RbacTarget.NONE));
        verify(auditLogService).record(eq(AuditEventType.PROGRAM_CURRICULUM_VERSION_CREATED), any(), any(), any(), any());
    }

    @Test
    void listAndGet_retrieveOnlyTheRequestedProgramsVersionHistory() throws Exception {
        Program program = Program.builder().programId(PROGRAM).tenantId(TENANT)
                .institutionId(INSTITUTION).programCode("BTECH").status("ACTIVE").build();
        var versionId = UUID.randomUUID();
        var curriculum = mapper.readTree("{\"courses\":[{\"code\":\"CS101\"}]}");
        ProgramCurriculumVersion version = ProgramCurriculumVersion.builder()
                .tenantId(TENANT).programCurriculumVersionId(versionId).institutionId(INSTITUTION)
                .programId(PROGRAM).versionNumber(1).curriculumJson(curriculum)
                .effectiveFrom(LocalDate.parse("2026-07-01")).createdBy(USER).build();
        when(programRepository.findByTenantIdAndProgramId(TENANT, PROGRAM)).thenReturn(Optional.of(program));
        when(versionRepository.findByTenantIdAndInstitutionIdAndProgramIdOrderByVersionNumberDesc(TENANT, INSTITUTION, PROGRAM))
                .thenReturn(List.of(version));
        when(versionRepository.findByTenantIdAndInstitutionIdAndProgramIdAndProgramCurriculumVersionId(
                TENANT, INSTITUTION, PROGRAM, versionId)).thenReturn(Optional.of(version));

        var listed = service.list(new RbacPrincipal(TENANT, USER), INSTITUTION, PROGRAM);
        var retrieved = service.get(new RbacPrincipal(TENANT, USER), INSTITUTION, PROGRAM, versionId);

        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).programCurriculumVersionId()).isEqualTo(versionId);
        assertThat(retrieved.versionNumber()).isEqualTo(1);
        assertThat(retrieved.curriculumJson().path("courses").get(0).path("code").asText()).isEqualTo("CS101");
    }

    @Test
    void create_rejectsEmptyJsonBeforeWriting() {
        var request = new CreateCurriculumVersionRequest(Map.of(),
                LocalDate.parse("2026-07-01"), null);
        assertThatThrownBy(() -> service.create(new RbacPrincipal(TENANT, USER), INSTITUTION, PROGRAM, request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("JSON object");
    }

    @Test
    void create_rejectsEndDateBeforeStartDate() {
        var request = new CreateCurriculumVersionRequest(Map.of("courses", List.of()),
                LocalDate.parse("2026-07-01"), LocalDate.parse("2026-06-30"));
        assertThatThrownBy(() -> service.create(new RbacPrincipal(TENANT, USER), INSTITUTION, PROGRAM, request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("effectiveTo");
    }
}

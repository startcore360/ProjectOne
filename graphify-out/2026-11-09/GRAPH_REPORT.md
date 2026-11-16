# Graph Report - Personal project  (2026-11-09)

## Corpus Check
- 816 files · ~381,345 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 21 file(s) not represented in the graph (top: .properties 7, .css 7, (none) 5)

## Summary
- 4479 nodes · 22342 edges · 136 communities (91 shown, 45 thin omitted)
- Extraction: 91% EXTRACTED · 9% INFERRED · 0% AMBIGUOUS · INFERRED: 2111 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `263cc49a`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- userManagementService.ts
- Institution Structure & Hierarchy
- list
- RbacPrincipal
- AuthActionTest.java
- Services
- Institution Structure & Hierarchy
- Self-Service & Role Switching
- Platform Operations & Governance
- Platform Operations & Governance
- RBAC & Permission Evaluator
- SessionController
- Institution Structure & Hierarchy
- AuthServiceTest
- Institution Structure & Hierarchy
- TenantIssuerAuthenticationManagerResolver.java
- ui/index.ts
- UserBulkUploadService
- PlatformCustomRoleService
- Institution Structure & Hierarchy
- PlatformRoleController
- AuditLogService
- org.junit.jupiter.api.Test
- .institution
- Audit
- RolldownService
- design-system/components/index.ts
- TenantsView.tsx
- InstitutionControllerTest
- .addLocalEvent
- RoleDefinitionId
- .decideApproval
- ReleaseResponse
- .setStudentLogin
- Platform Operations & Governance
- StructureService
- BackupStatusControllerTest
- Application Configuration & Security
- Application Configuration & Security
- uuid
- org.springframework.security.core.Authentication
- InstitutionAdminPages.tsx
- Application Configuration & Security
- Self-Service & Role Switching
- TenantManagementService.java
- Multi-Tenant Routing & Lifecycle
- PlatformAccessLookup
- Multi-Tenant Routing & Lifecycle
- Multi-Tenant Routing & Lifecycle
- InstitutionId
- SelfServiceService.java
- PlatformPermissionServiceTest
- Institution Structure & Hierarchy
- jakarta.servlet.http.HttpServletRequest
- Platform Operations & Governance
- PlatformCustomRoleServiceTest
- .grantAccess
- ProfilePage.tsx
- Authentication & Identity Management
- CreateUserRequest
- SelfServiceService
- Platform Operations & Governance
- Core Utilities
- .updatePlatformUser
- StoredPlatformAccessLookup
- .listPendingApprovals
- PlatformDataProtectionService.java
- Core Utilities
- .createRelease
- ProgramRuleSet
- .updatePolicy
- EDU 360 CI/CD Engineering Standard
- OpenApiConfig.java
- AccessMode
- Application Configuration & Security
- .createDelegation
- package.json
- Authentication & Identity Management
- KeycloakAdminClient
- Platform Operations & Governance
- TenantPrincipal
- PlatformAccessLookup
- EDU 360 API
- PlatformReportController
- Docker Localstack
- .Github Workflows
- Docker Postgres
- .Github Workflows
- .Github Workflows
- .Github Workflows
- Platform Operations & Governance
- .record
- PlatformSecurityPolicyController.java
- Core Utilities
- RbacActionTest.java
- AcademicControllerTest
- .Github Workflows
- React + TypeScript + Vite
- PlatformCustomRoleStatus
- .Github Workflows
- Core Utilities
- UsageCountsControllerTest
- AuthServiceTest.java
- RBAC & Permission Evaluator
- TenantRoleTemplateAction
- RBAC & Permission Evaluator
- .Github Workflows
- .Github Workflows
- UpdateRoleActionSetRequest.java
- org.springframework.transaction.annotation.Transactional
- Core Utilities
- Utils
- Institution
- Platform Operations & Governance
- components/README.md
- GuardianLinkService
- PlatformAuditOutboxEvent
- BackupStatusEntryResponse.java
- Core Utilities
- Database Schema & Migrations
- Core Utilities
- Core Utilities

## God Nodes (most connected - your core abstractions)
1. `TenantPrincipal` - 201 edges
2. `RbacPrincipal` - 184 edges
3. `UnauthorizedException` - 148 edges
4. `AuditLogService` - 130 edges
5. `PlatformPrincipal` - 126 edges
6. `PlatformActionTest` - 126 edges
7. `PermissionEvaluatorService` - 118 edges
8. `Button()` - 117 edges
9. `BusinessRuleViolationException` - 115 edges
10. `InstitutionSetupActionTest` - 108 edges

## Surprising Connections (you probably didn't know these)
- `Configuration profiles` --references--> `DevAdminSeeder`  [INFERRED]
  api/README.md → api/src/main/java/com/edu360/api/config/DevAdminSeeder.java
- `PlatformDataProtectionController` --references--> `PlatformDataProtectionService`  [EXTRACTED]
  api/src/main/java/com/edu360/api/platformops/dataprotection/PlatformDataProtectionController.java → api/src/main/java/com/edu360/api/platformops/dataprotection/PlatformDataProtectionService.java
- `PlatformDataProtectionService` --references--> `DeletionRequestRepository`  [EXTRACTED]
  api/src/main/java/com/edu360/api/platformops/dataprotection/PlatformDataProtectionService.java → api/src/main/java/com/edu360/api/platformops/dataprotection/DeletionRequestRepository.java
- `PlatformDataProtectionService` --references--> `ExportRequestRepository`  [EXTRACTED]
  api/src/main/java/com/edu360/api/platformops/dataprotection/PlatformDataProtectionService.java → api/src/main/java/com/edu360/api/platformops/dataprotection/ExportRequestRepository.java
- `PlatformDataProtectionService` --references--> `RetentionChangeRequestRepository`  [EXTRACTED]
  api/src/main/java/com/edu360/api/platformops/dataprotection/PlatformDataProtectionService.java → api/src/main/java/com/edu360/api/platformops/dataprotection/RetentionChangeRequestRepository.java

## Import Cycles
- None detected.

## Communities (136 total, 45 thin omitted)

### Community 0 - "userManagementService.ts"
Cohesion: 0.07
Nodes (36): GuardianChangeDecision, GuardianChangeRequest, GuardianLinkItem, guardianService, LinkGuardianRequest, ActingScope, ActionResponse, CreateUserPayload (+28 more)

### Community 1 - "Institution Structure & Hierarchy"
Cohesion: 0.09
Nodes (52): PromotionRun, SubjectAllocation, ApprovalBatch, Delegation, AuditLog, FileObject, StudentGuardianLink, StudentProfile (+44 more)

### Community 2 - "list"
Cohesion: 0.05
Nodes (16): GradingSchemeVersionRepository, ApprovalBatchRepository, AuditLogRepository, GuardianProfileRepository, ImportJobRepository, ImportRowErrorRepository, LeaveApproverSettingRepository, DeletionRequestRepository (+8 more)

### Community 3 - "RbacPrincipal"
Cohesion: 0.06
Nodes (24): CreateProgramRuleSetRequest, ProgramRuleController, ProgramRuleSetResponse, StudentStatusResponse, ResourceNotFoundException, CourseOfferingResponse, CreateCourseOfferingRequest, CreateProgramRequest (+16 more)

### Community 4 - "AuthActionTest.java"
Cohesion: 0.06
Nodes (30): TopUserResponse, IdentityLinkStatus, ACTIVE, REVOKED, UserAccountStatus, ACTIVE, DEACTIVATED, PENDING_ACTIVATION (+22 more)

### Community 5 - "Services"
Cohesion: 0.04
Nodes (82): axios@^1.12.2, @reduxjs/toolkit@^2.13.0, Edu360Logo(), LogoStyle, RoleSwitcher(), mockRoles, LinkButton(), PasswordField() (+74 more)

### Community 6 - "Institution Structure & Hierarchy"
Cohesion: 0.05
Nodes (54): App(), EmptyState(), academicService, GradingSchemeItem, ProgramRuleSetItem, PromotionRunItem, StudentStatusItem, SubjectAllocationItem (+46 more)

### Community 7 - "Self-Service & Role Switching"
Cohesion: 0.07
Nodes (23): GradingSchemeVersion, GradingSchemeVersionId, PromotionRunId, SubjectAllocationId, ApprovalBatchId, DelegationId, FileObjectId, GuardianProfileId (+15 more)

### Community 8 - "Platform Operations & Governance"
Cohesion: 0.06
Nodes (9): CreateGradingSchemeRequest, ApplyDiscountRequest, CreateInvoiceRequest, TenantLifecycleActionRequest, UpdateTenantSettingsRequest, UpdateSupportTicketRequest, ApiApplicationTests, PlatformBillingControllerTest (+1 more)

### Community 9 - "Platform Operations & Governance"
Cohesion: 0.04
Nodes (164): PageHeader(), Spinner(), Button(), ConfirmDialog(), DataTable(), ErrorState(), MetricCard(), Modal() (+156 more)

### Community 10 - "RBAC & Permission Evaluator"
Cohesion: 0.08
Nodes (7): BulkApprovalRequest, GuardianChangeRequestDto, CreateCustomRoleRequest, CreateRolldownOverrideRequest, RbacActionTest, RoleCustomControllerTest, RolldownControllerTest

### Community 12 - "Institution Structure & Hierarchy"
Cohesion: 0.11
Nodes (18): BulkApprovalResponse, PlatformPrincipal, BusinessRuleViolationException, DuplicateResourceException, UnauthorizedException, PlatformRole, BILLING_MANAGER, DATA_PROTECTION_OFFICER (+10 more)

### Community 13 - "AuthServiceTest"
Cohesion: 0.10
Nodes (17): AuthService, FailedAttemptTracker, RoleInfo, AuthTokenResponse, AuthUserResponse, KeycloakAuthClient, TokenResult, InvalidCredentialsException (+9 more)

### Community 14 - "Institution Structure & Hierarchy"
Cohesion: 0.09
Nodes (16): ClassTeacherAssignmentRepository, ClassTeacherAssignmentService, DepartmentRepository, ClassTeacherAssignRequest, HodAssignRequest, MentorAssignRequest, HodAssignmentService, MentorAssignmentRepository (+8 more)

### Community 15 - "TenantIssuerAuthenticationManagerResolver.java"
Cohesion: 0.13
Nodes (8): TenantIssuerAuthenticationManagerResolver, TenantState, ACTIVE, ARCHIVED, CREATED, DELETED, SUSPENDED, TRIAL

### Community 16 - "ui/index.ts"
Cohesion: 0.07
Nodes (27): EmptyState(), ErrorState(), Button(), ButtonProps, ButtonSize, ButtonVariant, sizes, variants (+19 more)

### Community 17 - "UserBulkUploadService"
Cohesion: 0.11
Nodes (5): StartImportRequest, UserImportRequest, RowValidationError, UserBulkUploadService, UserBulkUploadControllerTest

### Community 19 - "Institution Structure & Hierarchy"
Cohesion: 0.09
Nodes (30): StudentStatusController, TopUserController, CalendarEventController, DepartmentController, ClassTeacherAssignResponse, HodAssignResponse, MentorAssignResponse, LeaveApproverController (+22 more)

### Community 20 - "PlatformRoleController"
Cohesion: 0.20
Nodes (3): PlatformActionCatalogItem, PlatformRoleResponse, PlatformRoleController

### Community 21 - "AuditLogService"
Cohesion: 0.11
Nodes (17): GradingSchemeService, ProgramRuleService, StudentStatusService, SubjectAllocationService, ApprovalInboxService, AuditLogService, TopUserService, UserAccountService (+9 more)

### Community 22 - "org.junit.jupiter.api.Test"
Cohesion: 0.06
Nodes (7): ChangeStudentStatusRequest, CreatePromotionRunRequest, PromotionDecisionRequest, LockTermRequest, UnlockTermRequest, InstitutionSetupActionTest, TermLockControllerTest

### Community 23 - ".institution"
Cohesion: 0.06
Nodes (17): LeaveApproverSetRequest, LeaveApproverSettingResponse, WeeklyOffRuleRequest, WeeklyOffRuleRepository, WeeklyOffRuleService, ActionCatalog, ActionCatalogRepository, InstitutionScopeRequest (+9 more)

### Community 24 - "Audit"
Cohesion: 0.03
Nodes (59): AuditEventType, ACADEMIC_SESSION_CREATED, ACADEMIC_SESSION_UPDATED, ACCOUNT_CREATED, APPROVAL, APPROVAL_BULK_DECIDED, APPROVAL_DELEGATED, APPROVAL_DELEGATION_CANCELLED (+51 more)

### Community 25 - "RolldownService"
Cohesion: 0.22
Nodes (5): RoleRolldownDefaultRepository, RoleRolldownOverrideRepository, RolldownController, RolldownOverrideDto, RolldownService

### Community 26 - "design-system/components/index.ts"
Cohesion: 0.05
Nodes (68): Icon(), IconName, paths, BadgeProps, BadgeTone, tones, ButtonProps, ButtonSize (+60 more)

### Community 27 - "TenantsView.tsx"
Cohesion: 0.06
Nodes (44): Badge(), Kicker(), CreatePlanPayload, ExportRequestDto, IsolationMode, PlanItem, platformTenantService, RetentionPolicyDto (+36 more)

### Community 28 - "InstitutionControllerTest"
Cohesion: 0.16
Nodes (5): BlockDecisionRequest, BlockInstitutionRequest, CreateInstitutionRequest, InstitutionBlockResponse, InstitutionControllerTest

### Community 29 - ".addLocalEvent"
Cohesion: 0.24
Nodes (4): AcademicCalendarEventRepository, CalendarEventService, CalendarEventRequest, CalendarEventServiceTest

### Community 30 - "RoleDefinitionId"
Cohesion: 0.08
Nodes (11): AssignRoleRequest, UserRoleAssignmentResponse, RoleActionRepository, RoleCustomService, RoleDefinitionId, DashboardPreferencesRequest, SwitchActiveRoleRequest, UserActiveRoleRepository (+3 more)

### Community 31 - ".decideApproval"
Cohesion: 0.21
Nodes (4): BulkApproveItem, BulkApproveRequestDto, DecideApprovalRequestDto, ApprovalInboxControllerTest

### Community 32 - "ReleaseResponse"
Cohesion: 0.26
Nodes (3): ReleaseResponse, ReleaseTransitionRequest, UpdateReleaseRequest

### Community 33 - ".setStudentLogin"
Cohesion: 0.38
Nodes (3): StudentLoginSwitchRequest, StudentLoginSwitchService, StudentLoginSwitchServiceTest

### Community 34 - "Platform Operations & Governance"
Cohesion: 0.15
Nodes (4): CreatePlanRequest, TenantActivationRequest, UpdatePlanRequest, PlatformTenantControllerTest

### Community 35 - "StructureService"
Cohesion: 0.09
Nodes (7): ProgramRuleSetRepository, CourseOfferingRepository, ProgramYearRepository, SchoolClassRepository, SemesterRepository, StructureService, SubjectRepository

### Community 37 - "Application Configuration & Security"
Cohesion: 0.12
Nodes (5): JpaRepositoryConfig, RestClientConfig, PlatformJpaConfig, DataSourceConfig, TenantContextFilterConfig

### Community 38 - "Application Configuration & Security"
Cohesion: 0.13
Nodes (7): DevRoleAssignmentSeeder, FileObjectRepository, UserAccount, StandardRoleAction, StandardRoleActionRepository, StandardRoleRepository, DevRoleAssignmentSeederTest

### Community 39 - "uuid"
Cohesion: 0.04
Nodes (4): PlatformUserCreateRequest, TenantCreateRequest, AssumeSupportSessionResponse, TermLock

### Community 40 - "org.springframework.security.core.Authentication"
Cohesion: 0.05
Nodes (25): GradingSchemeController, PromotionController, ApprovalInboxController, IdentityEventRequest, ImportJobResponse, ImportRowErrorResponse, ImportTemplateResponse, UserBulkUploadController (+17 more)

### Community 41 - "InstitutionAdminPages.tsx"
Cohesion: 0.05
Nodes (44): AcademicSessionItem, CreateAcademicSessionPayload, CreateDepartmentPayload, CreateInstitutionRequest, CreateMasterCalendarEventPayload, CreateProgramPayload, DepartmentItem, InstitutionItem (+36 more)

### Community 43 - "Self-Service & Role Switching"
Cohesion: 0.08
Nodes (9): LocalFileStorage, UncheckedFileStorageException, PlatformUser, PlatformUserRepository, ProfileResponse, ProfileUpdateRequest, ProfileService, StoredPhoto (+1 more)

### Community 45 - "Multi-Tenant Routing & Lifecycle"
Cohesion: 0.14
Nodes (5): TenantDatabaseCredentials, CachedPool, TenantRoutingDataSource, TenantSecretsResolver, TenantRoutingDataSourceTest

### Community 49 - "InstitutionId"
Cohesion: 0.13
Nodes (5): CreateDepartmentRequest, DepartmentResponse, InstitutionId, ProgramRepository, StructureControllerTest

### Community 50 - "SelfServiceService.java"
Cohesion: 0.36
Nodes (4): LeaveBalanceDto, LeaveRequestDto, NoticeDto, NotificationDto

### Community 52 - "Institution Structure & Hierarchy"
Cohesion: 0.11
Nodes (10): GradingSchemeResponse, AcademicSessionRepository, AcademicSessionResponse, CreateAcademicSessionRequest, CreateMasterCalendarEventRequest, MasterCalendarEventResponse, UpdateAcademicSessionRequest, MasterCalendarController (+2 more)

### Community 53 - "jakarta.servlet.http.HttpServletRequest"
Cohesion: 0.06
Nodes (12): AuthController, ChangePasswordRequest, LoginRequest, RefreshRequest, CorrelationContext, CorrelationIdFilter, MustChangePasswordFilter, NormalizedRequestWrapper (+4 more)

### Community 54 - "Platform Operations & Governance"
Cohesion: 0.25
Nodes (6): CreateIntegrationTypeRequest, IntegrationTypeResponse, UpdateIntegrationTypeRequest, IntegrationTypeController, IntegrationTypeRepository, IntegrationTypeControllerTest

### Community 55 - "PlatformCustomRoleServiceTest"
Cohesion: 0.20
Nodes (5): CreatePlatformRoleRequest, RoleActionItem, UpdatePlatformRoleRequest, PlatformCustomRoleRepository, PlatformCustomRoleServiceTest

### Community 56 - ".grantAccess"
Cohesion: 0.17
Nodes (6): CreateSupportTicketRequest, GrantSupportAccessRequest, SupportAccessGrantResponse, SupportTicketResponse, PlatformSupportService, TenantSupportControllerTest

### Community 57 - "ProfilePage.tsx"
Cohesion: 0.15
Nodes (17): Badge(), PlatformRole, ProfileResponse, profileService, ProfileUpdateRequest, UserType, initialsFor(), ProfileAvatarSection() (+9 more)

### Community 60 - "SelfServiceService"
Cohesion: 0.28
Nodes (6): NoticeRepository, SelfServiceService, UserLeaveBalanceRepository, UserLeaveRequestRepository, UserNotificationRepository, SelfServiceControllerTest

### Community 61 - "Platform Operations & Governance"
Cohesion: 0.14
Nodes (6): PlatformRoleTemplateResponse, SavePlatformRoleTemplateRequest, PlatformRoleTemplateActionRepository, PlatformRoleTemplateRepository, PlatformRoleTemplateService, PlatformRoleTemplateServiceTest

### Community 62 - "Core Utilities"
Cohesion: 0.09
Nodes (21): compilerOptions, allowImportingTsExtensions, erasableSyntaxOnly, jsx, lib, module, moduleDetection, moduleResolution (+13 more)

### Community 63 - ".updatePlatformUser"
Cohesion: 0.13
Nodes (5): KeycloakProvisioningException, PlatformUserResponse, PlatformUserUpdateRequest, PlatformUserControllerTest, PlatformUserUpdateTest

### Community 64 - "StoredPlatformAccessLookup"
Cohesion: 0.16
Nodes (3): PlatformCustomRoleActionRepository, PlatformStepInRepository, StoredPlatformAccessLookup

### Community 65 - ".listPendingApprovals"
Cohesion: 0.47
Nodes (3): ApprovalInboxResponseDto, PageInfoDto, ApprovalStepDto

### Community 68 - "Core Utilities"
Cohesion: 0.10
Nodes (19): compilerOptions, allowImportingTsExtensions, erasableSyntaxOnly, lib, module, moduleDetection, moduleResolution, noEmit (+11 more)

### Community 69 - ".createRelease"
Cohesion: 0.23
Nodes (4): CreateReleaseRequest, PlatformReleaseRepository, PlatformReleaseService, PlatformReleaseControllerTest

### Community 71 - ".updatePolicy"
Cohesion: 0.26
Nodes (3): EntitlementsUpdateRequest, UpdateSecurityPolicyRequest, PlatformSecurityReportControllerTest

### Community 72 - "EDU 360 CI/CD Engineering Standard"
Cohesion: 0.18
Nodes (10): 1. Purpose and delivery model, 2. Pipeline stages, 3. PostgreSQL migration validation, 4. Pull-request and branch protection, 5. Artifact and release integrity, 6. Deployment and operations — intentionally not enabled, 7. Follow-up acceptance checklist, EDU 360 CI/CD Engineering Standard (+2 more)

### Community 74 - "AccessMode"
Cohesion: 0.50
Nodes (3): AccessMode, HANDED_OVER, SHARED

### Community 76 - ".createDelegation"
Cohesion: 0.17
Nodes (6): ApprovalDelegationController, ApprovalDelegationService, DelegationRepository, CreateDelegationRequest, DelegationResponse, ApprovalDelegationControllerTest

### Community 78 - "package.json"
Cohesion: 0.04
Nodes (61): dependencies, clsx@^2.1.1, date-fns@^4.4.0, react@^19.2.0, react-dom@^19.2.0, react-redux@^9.2.0, react-router-dom@^7.18.5, redux-persist@^6.0.0 (+53 more)

### Community 79 - "Authentication & Identity Management"
Cohesion: 0.13
Nodes (14): KC_BOOTSTRAP_ADMIN_PASSWORD, KC_BOOTSTRAP_ADMIN_USERNAME, KC_SPI_THEME_CACHE_TEMPLATES, KC_SPI_THEME_CACHE_THEMES, KC_SPI_THEME_STATIC_MAX_AGE, command, environment, image (+6 more)

### Community 80 - "KeycloakAdminClient"
Cohesion: 0.04
Nodes (10): DevAccount, DevAdminSeeder, KeycloakAdminClient, KeycloakProperties, SessionResponse, SessionService, AwsSecretsManagerTenantSecretsResolver, SecretsManagerProperties (+2 more)

### Community 83 - "TenantPrincipal"
Cohesion: 0.12
Nodes (6): ApprovalSummaryDto, DecideApprovalResponseDto, TenantPrincipal, UserRequestSupport, ProfileController, ProfileControllerTest

### Community 84 - "PlatformAccessLookup"
Cohesion: 0.18
Nodes (5): PlatformAccessLookup, StepInRequest, StepInResponse, PlatformStepInService, PlatformStepInServiceTest

### Community 85 - "EDU 360 API"
Cohesion: 0.29
Nodes (6): Build and test, Configuration profiles, Current architecture status, EDU 360 API, Requirements, Run locally

### Community 87 - "PlatformReportController"
Cohesion: 0.12
Nodes (6): InvoiceRepository, PlatformReportController, PlatformReportResponse, SupportAccessGrantRepository, SupportTicketRepository, PlatformSupportControllerTest

### Community 88 - "Docker Localstack"
Cohesion: 0.15
Nodes (12): AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, DEFAULT_REGION, SERVICES, environment, image, ports, volumes (+4 more)

### Community 89 - ".Github Workflows"
Cohesion: 0.26
Nodes (13): steps, steps, steps, GITHUB_TOKEN, PGPASSWORD, PSQL, steps, steps (+5 more)

### Community 93 - "Docker Postgres"
Cohesion: 0.17
Nodes (11): POSTGRES_DB, POSTGRES_PASSWORD, POSTGRES_USER, environment, image, ports, volumes, services (+3 more)

### Community 94 - ".Github Workflows"
Cohesion: 0.17
Nodes (11): name, runs-on, concurrency, cancel-in-progress, group, jobs, backend, security (+3 more)

### Community 95 - ".Github Workflows"
Cohesion: 0.17
Nodes (12): name, runs-on, services, POSTGRES_DB, POSTGRES_PASSWORD, POSTGRES_USER, database-migration-validation, env (+4 more)

### Community 96 - ".Github Workflows"
Cohesion: 0.17
Nodes (12): with, cache, cache-dependency-path, distribution, fail-on-severity, if-no-files-found, java-version, languages (+4 more)

### Community 98 - ".record"
Cohesion: 0.10
Nodes (5): PromotionRunResponse, PromotionService, TermLockRepository, TermLockService, AuditLogServiceTest

### Community 100 - "PlatformSecurityPolicyController.java"
Cohesion: 0.15
Nodes (7): EntitlementsResponse, PlatformFeatureToggleController, CreateSecurityIncidentRequest, PlatformSecurityPolicyController, SecurityIncidentResponse, SecurityPolicyResponse, UpdateSecurityIncidentRequest

### Community 101 - "Core Utilities"
Cohesion: 0.20
Nodes (10): scripts, build, dev, format, format:check, lint, preview, test (+2 more)

### Community 103 - "RbacActionTest.java"
Cohesion: 0.17
Nodes (10): ApprovalRequest, ApprovalRequestId, ApprovalRequestRepository, ApprovalStep, ApprovalStepId, ApprovalStepRepository, SubjectRef, GuardianProfile (+2 more)

### Community 106 - "AcademicControllerTest"
Cohesion: 0.18
Nodes (6): CreateSubjectAllocationRequest, PromotionRunRepository, SubjectAllocationController, SubjectAllocationRepository, SubjectAllocationResponse, AcademicControllerTest

### Community 108 - ".Github Workflows"
Cohesion: 0.25
Nodes (8): if, name, permissions, runs-on, dependency-review, permissions, contents, pull-requests

### Community 109 - "React + TypeScript + Vite"
Cohesion: 0.50
Nodes (3): Expanding the ESLint configuration, React Compiler, React + TypeScript + Vite

### Community 110 - "PlatformCustomRoleStatus"
Cohesion: 0.33
Nodes (5): PlatformCustomRoleStatus, ACTIVE, ARCHIVED, DRAFT, INACTIVE

### Community 111 - ".Github Workflows"
Cohesion: 0.29
Nodes (7): name, permissions, runs-on, steps, codeql, actions, security-events

### Community 113 - "UsageCountsControllerTest"
Cohesion: 0.30
Nodes (4): UsageCountsResponse, UsageMetricSnapshot, UsageMetricDailyRepository, UsageCountsControllerTest

### Community 114 - "AuthServiceTest.java"
Cohesion: 0.06
Nodes (50): TenantUserLookup, UserAccountRepository, GoLiveBlockerItem, GoLiveCheckResponse, PlatformUserStatus, ACTIVE, INACTIVE, AssignmentStatus (+42 more)

### Community 119 - ".Github Workflows"
Cohesion: 0.33
Nodes (6): run, defaults, name, runs-on, frontend, working-directory

### Community 120 - ".Github Workflows"
Cohesion: 0.33
Nodes (6): on, pull_request, push, workflow_dispatch, branches, branches

### Community 122 - "org.springframework.transaction.annotation.Transactional"
Cohesion: 0.14
Nodes (3): RecordPaymentRequest, PlatformBillingService, PlatformAuditWriter

### Community 123 - "Core Utilities"
Cohesion: 0.40
Nodes (4): printWidth, proseWrap, semi, trailingComma

### Community 124 - "Utils"
Cohesion: 0.70
Nodes (3): formErrorMapping, normalizeFormErrors(), splitFullName()

### Community 133 - "GuardianLinkService"
Cohesion: 0.15
Nodes (7): GuardianChangeDecisionRequest, GuardianLinkResponse, LinkGuardianRequest, GuardianLinkController, GuardianLinkService, StudentGuardianLinkRepository, GuardianLinkControllerTest

## Knowledge Gaps
- **112 isolated node(s):** `ACTIVE`, `ARCHIVED`, `DRAFT`, `INACTIVE`, `DEPARTMENT` (+107 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 619 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **45 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `RbacPrincipal` connect `RbacPrincipal` to `GuardianLinkService`, `Institution Structure & Hierarchy`, `Institution Structure & Hierarchy`, `Institution Structure & Hierarchy`, `AuditLogService`, `org.junit.jupiter.api.Test`, `.institution`, `RolldownService`, `InstitutionControllerTest`, `.addLocalEvent`, `RoleDefinitionId`, `.setStudentLogin`, `StructureService`, `Application Configuration & Security`, `uuid`, `org.springframework.security.core.Authentication`, `InstitutionId`, `SelfServiceService.java`, `Institution Structure & Hierarchy`, `.grantAccess`, `Authentication & Identity Management`, `CreateUserRequest`, `SelfServiceService`, `.createDelegation`, `.record`, `RbacActionTest.java`, `AcademicControllerTest`?**
  _High betweenness centrality (0.019) - this node is a cross-community bridge._
- **What connects `ACTIVE`, `ARCHIVED`, `DRAFT` to the rest of the system?**
  _112 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `userManagementService.ts` be split into smaller, more focused modules?**
  _Cohesion score 0.06612244897959184 - nodes in this community are weakly interconnected._
- **Why does `KeycloakAdminClient` connect `KeycloakAdminClient` to `Platform Operations & Governance`, `AuthActionTest.java`, `uuid`, `Platform Operations & Governance`, `TenantManagementService.java`, `AuthServiceTest`, `Institution Structure & Hierarchy`, `AuthServiceTest.java`, `AuditLogService`, `.institution`, `Authentication & Identity Management`, `RoleDefinitionId`, `.updatePlatformUser`?**
  _High betweenness centrality (0.015) - this node is a cross-community bridge._
- **Should `Institution Structure & Hierarchy` be split into smaller, more focused modules?**
  _Cohesion score 0.08840996168582375 - nodes in this community are weakly interconnected._
- **Why does `AuditEventType` connect `Audit` to `Institution Structure & Hierarchy`, `.record`, `RbacPrincipal`, `AuthActionTest.java`, `RbacActionTest.java`, `Self-Service & Role Switching`, `Institution Structure & Hierarchy`, `KeycloakAdminClient`, `AuthServiceTest.java`, `Institution Structure & Hierarchy`, `AuditLogService`, `.grantAccess`?**
  _High betweenness centrality (0.013) - this node is a cross-community bridge._
- **Should `list` be split into smaller, more focused modules?**
  _Cohesion score 0.04644194756554307 - nodes in this community are weakly interconnected._
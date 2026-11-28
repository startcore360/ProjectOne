# Graph Report - Personal project  (2026-11-11)

## Corpus Check
- 939 files · ~446,006 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 25 file(s) not represented in the graph (top: .css 8, .properties 7, (none) 6)

## Summary
- 5852 nodes · 27023 edges · 205 communities (115 shown, 90 thin omitted)
- Extraction: 87% EXTRACTED · 13% INFERRED · 0% AMBIGUOUS · INFERRED: 3483 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `714b3bd5`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- userManagementService.ts
- Notice.java
- list
- RbacActionTest
- .authorize
- Services
- api
- lombok.NoArgsConstructor
- org.junit.jupiter.api.DisplayName
- PageHeader
- org.springframework.stereotype.Service
- SessionControllerTest
- Institution Structure & Hierarchy
- AuthServiceTest
- com.edu360.api.config.TenantPrincipal
- RegB3SignInTest.java
- .signIn
- UserType
- AuthService
- lombok.RequiredArgsConstructor
- .createCustomRole
- AuditLogService
- SelfServicePage.tsx
- .institution
- Audit
- AuthService
- design-system/components/index.ts
- TenantsView.tsx
- InstitutionId
- RoleDashboardPage.tsx
- .tenantPrincipal
- .can
- .createRelease
- Edu360OtpResource.java
- Platform Operations & Governance
- org.springframework.transaction.annotation.Transactional
- BackupStatusControllerTest
- Application Configuration & Security
- RoleDefinition
- InstitutionSetupActionTest.java
- io.swagger.v3.oas.annotations.Operation
- InstitutionAdminPages.tsx
- Application Configuration & Security
- ProfileService
- TenantRoutingDataSource
- StoredPlatformAccessLookup
- PlatformRoleResponse
- Multi-Tenant Routing & Lifecycle
- KeycloakAdminClient
- UserLeaveBalance.java
- PlatformPermissionServiceTest
- CalendarEventController
- org.springframework.stereotype.Component
- Platform Operations & Governance
- AcademicSessionRepository
- .grantAccess
- SessionDeviceCard.tsx
- .provisionUser
- RbacPrincipal
- RbacActionTest.java
- PlatformPrincipal
- Core Utilities
- PlatformUserManagementService
- PlatformCustomRoleServiceTest
- RegB3SignInTest
- jakarta.servlet.http.HttpServletRequest
- PlatformDataProtectionService
- Core Utilities
- .assign
- PlatformAccessLookup
- PermissionEvaluatorService
- EDU 360 CI/CD Engineering Standard
- RoleDefinitionId
- SessionService
- Application Configuration & Security
- .as
- InstitutionSetupActionTest
- package.json
- Authentication & Identity Management
- KeycloakProperties
- Platform Operations & Governance
- lombok.Setter
- org.springframework.security.core.Authentication
- .stepIn
- KeycloakRealmSecurityService
- PlatformReportController.java
- Docker Localstack
- .Github Workflows
- RegM03SecurityChainTest
- ProfileControllerTest
- SessionServiceTest
- Docker Postgres
- .Github Workflows
- .Github Workflows
- .Github Workflows
- SecurityPolicyService
- UserLeaveRequest.java
- PrintedPasswordPolicy
- PlatformSupportController
- GradingSchemeVersion.java
- ProgramRuleSet
- .listSessions
- UserBulkUploadControllerTest
- StudentGuardianLink
- .Github Workflows
- React + TypeScript + Vite
- SessionActivityInterceptorTest
- .Github Workflows
- Core Utilities
- RegM03SecurityChainTest.java
- RoleDefinition
- lombok.Data
- RoleCustomController
- OtpConfirmOutcome
- .Github Workflows
- .Github Workflows
- DeletionRequest
- map
- Core Utilities
- Utils
- Delegation
- TemporaryPasswordResponseAdvice.java
- .decideApproval
- JdbcTenantAuditStore
- ApprovalStep
- ApiHelper
- components/README.md
- SessionActivityInterceptor
- .assertRoleInStatusGrantsNothing
- PlatformRole
- .set
- scripts
- PlatformActionTest.java
- Core Utilities
- Database Schema & Migrations
- ModuleEntitlementEnforcer.java
- Core Utilities
- Core Utilities
- UserBulkUploadService.java
- ResourceNotFoundException
- ReferenceCodeArchitectureTest
- hooks/index.ts
- .resolve
- RbacTestWorld
- ActiveRoleA02Test
- SelfServiceScopeA08Test
- SubjectAllocation
- .createUserImport
- OpenApiConfig.java
- MutableClock
- UsernameKind
- ImportJob
- com.edu360:edu360-otp-extension
- theme.ts

## God Nodes (most connected - your core abstractions)
1. `AuditLogService` - 129 edges
2. `RbacPrincipal` - 127 edges
3. `PlatformActionTest` - 127 edges
4. `AuditEventType` - 124 edges
5. `TenantPrincipal` - 121 edges
6. `PermissionEvaluatorService` - 119 edges
7. `InstitutionSetupActionTest` - 109 edges
8. `react` - 103 edges
9. `KeycloakAdminClient` - 100 edges
10. `UserAccountId` - 86 edges

## Surprising Connections (you probably didn't know these)
- `Part A — Sprint 1 dependency audit` --references--> `ApprovalOutcomeHandler`  [INFERRED]
  SPRINT2_PROGRESS.md → api/src/main/java/com/edu360/api/approval/ApprovalOutcomeHandler.java
- `Part A — Sprint 1 dependency audit` --references--> `GlobalExceptionHandler`  [INFERRED]
  SPRINT2_PROGRESS.md → api/src/main/java/com/edu360/api/exception/GlobalExceptionHandler.java
- `Part A — Sprint 1 dependency audit` --references--> `MentorAssignmentService`  [INFERRED]
  SPRINT2_PROGRESS.md → api/src/main/java/com/edu360/api/institution/MentorAssignmentService.java
- `Part A — Sprint 1 dependency audit` --references--> `GlobalExceptionHandlerTest`  [INFERRED]
  SPRINT2_PROGRESS.md → api/src/test/java/com/edu360/api/exception/GlobalExceptionHandlerTest.java
- `Part A — Sprint 1 dependency audit` --references--> `Section`  [INFERRED]
  SPRINT2_PROGRESS.md → api/src/main/java/com/edu360/api/institution/Section.java

## Import Cycles
- None detected.

## Communities (205 total, 90 thin omitted)

### Community 0 - "userManagementService.ts"
Cohesion: 0.04
Nodes (130): Badge(), Button(), CheckboxField(), DateField(), DateTimeField(), EmptyState(), Kicker(), Modal() (+122 more)

### Community 1 - "Notice.java"
Cohesion: 0.31
Nodes (3): Notice, NoticeId, NoticeRepository

### Community 2 - "list"
Cohesion: 0.05
Nodes (9): ApprovalBatchRepository, PlanRepository, UsageCountsResponse, PlatformUserDeviceRepository, SupportTicketRepository, StandardRoleActionRepository, RoleRolldownDefaultRepository, UserActiveRoleRepository (+1 more)

### Community 3 - "RbacActionTest"
Cohesion: 0.09
Nodes (5): CreateCustomRoleRequest, RolldownMapEntryDto, RbacActionTest, RoleCustomControllerTest, RolldownControllerTest

### Community 4 - ".authorize"
Cohesion: 0.04
Nodes (26): ApplyDiscountRequest, InvoiceResponse, RecordPaymentRequest, PlatformBillingController, PlatformBillingService, PlanResponse, TenantPageResponse, TenantResponse (+18 more)

### Community 5 - "Services"
Cohesion: 0.03
Nodes (107): RoleSwitcher(), RoleSwitcherProps, mockRoles, ChangePasswordPage(), mockedLogin, mockedApi, ACTIVE_ROLE, CATALOG (+99 more)

### Community 6 - "api"
Cohesion: 0.11
Nodes (16): approvalDelegationService, ApprovalStepItem, BulkApprovalRequest, BulkApprovalResponse, CreateDelegationRequest, DelegationItem, LockTermRequest, TermLockItem (+8 more)

### Community 7 - "lombok.NoArgsConstructor"
Cohesion: 0.10
Nodes (31): PromotionRun, ApprovalBatch, ApprovalBatchId, ApprovalRequestId, ApprovalStepId, DelegationId, AuditLog, FileObject (+23 more)

### Community 8 - "org.junit.jupiter.api.DisplayName"
Cohesion: 0.05
Nodes (10): CreateInvoiceRequest, UpdateTenantSettingsRequest, EntitlementsUpdateRequest, CreateReleaseRequest, CreateSupportTicketRequest, UpdateSupportTicketRequest, ApprovalInboxServiceActionResolutionTest, PlatformActionTest (+2 more)

### Community 9 - "PageHeader"
Cohesion: 0.05
Nodes (118): PageHeader(), Badge(), Spinner(), ConfirmDialog(), DataTable(), MetricCard(), Notice(), Panel() (+110 more)

### Community 10 - "org.springframework.stereotype.Service"
Cohesion: 0.06
Nodes (11): GradingSchemeService, StudentStatusService, SubjectAllocationService, CalendarEventService, ClassTeacherAssignmentService, HodAssignmentService, StudentLoginSwitchService, SelfServiceService (+3 more)

### Community 13 - "AuthServiceTest"
Cohesion: 0.15
Nodes (6): CredentialInfo, StudentLoginLookup, ResolvedUser, UserAccountId, AuthActionTest, AuthServiceTest

### Community 14 - "com.edu360.api.config.TenantPrincipal"
Cohesion: 0.05
Nodes (20): ApprovalInboxService, ApprovalOutcomeHandler, ApprovalRequest, ApprovalRequestRepository, ApprovalRouting, Route, Via, DELEGATION (+12 more)

### Community 15 - "RegB3SignInTest.java"
Cohesion: 0.05
Nodes (6): TenantIssuerAuthenticationManagerResolver, IdentityLinkStatus, ACTIVE, REVOKED, TenantUserLookup, TenantManagementService

### Community 16 - ".signIn"
Cohesion: 0.17
Nodes (3): CreateSubjectAllocationRequest, CreateDelegationRequest, ScopeA05Test

### Community 17 - "UserType"
Cohesion: 0.12
Nodes (15): AuthTokenResponse, AuthUserResponse, TopUserResponse, UserAccountStatus, ACTIVE, DEACTIVATED, PENDING_ACTIVATION, UserType (+7 more)

### Community 18 - "AuthService"
Cohesion: 0.08
Nodes (11): AccountState, AuthService, RoleInfo, MfaRequirementService, AccountSetupRequiredException, KeycloakAuthClient, PasswordCheck, INVALID (+3 more)

### Community 19 - "lombok.RequiredArgsConstructor"
Cohesion: 0.10
Nodes (20): AuthController, MfaEnrolRequest, SessionSettingsController, SessionSettingsResponse, TenantPrincipal, UnauthorizedException, TopUserController, DepartmentController (+12 more)

### Community 21 - "AuditLogService"
Cohesion: 0.06
Nodes (9): ApprovalDelegationService, AuditLogRepository, AuditLogService, TenantAuditEvent, TenantAuditStore, TopUserService, UserAccountService, RoleAssignmentService (+1 more)

### Community 22 - "SelfServicePage.tsx"
Cohesion: 0.10
Nodes (20): EmptyState(), ErrorState(), Button(), ButtonProps, ButtonSize, ButtonVariant, sizes, variants (+12 more)

### Community 23 - ".institution"
Cohesion: 0.09
Nodes (10): ActionCatalog, ActionCatalogRepository, InstitutionScopeRequest, RoleActionRepository, PermissionEvaluatorServiceTest, UserDeactivationActionTest, UserEditActionTest, UserPasswordResetUnlockActionTest (+2 more)

### Community 24 - "Audit"
Cohesion: 0.03
Nodes (75): AuditEventType, ACADEMIC_SESSION_CREATED, ACADEMIC_SESSION_UPDATED, ACCOUNT_CREATED, APPROVAL, APPROVAL_BULK_DECIDED, APPROVAL_DELEGATED, APPROVAL_DELEGATION_CANCELLED (+67 more)

### Community 25 - "AuthService"
Cohesion: 0.11
Nodes (5): ChangePasswordRequest, IdentityEventRequest, IdentityEventTokenValidator, AuthControllerTest, RegM03Test

### Community 26 - "design-system/components/index.ts"
Cohesion: 0.06
Nodes (36): App(), BadgeProps, BadgeTone, tones, ButtonProps, ButtonSize, ButtonVariant, CheckboxFieldProps (+28 more)

### Community 27 - "TenantsView.tsx"
Cohesion: 0.05
Nodes (52): Edu360Logo(), Edu360LogoProps, LogoStyle, AuthenticatorSetup(), AuthenticatorSetupProps, PendingEnrolment, LoginPage(), renderPage() (+44 more)

### Community 28 - "InstitutionId"
Cohesion: 0.09
Nodes (11): DepartmentRepository, BlockDecisionRequest, BlockInstitutionRequest, CreateDepartmentRequest, CreateInstitutionRequest, CreateProgramRequest, InstitutionId, InstitutionRepository (+3 more)

### Community 29 - "RoleDashboardPage.tsx"
Cohesion: 0.09
Nodes (42): Icon(), IconName, paths, EmptyStateProps, Kpi, Metric, MetricCardProps, NoticeProps (+34 more)

### Community 30 - ".tenantPrincipal"
Cohesion: 0.05
Nodes (12): ChangeStudentStatusRequest, CreateGradingSchemeRequest, CreateProgramRuleSetRequest, CreatePromotionRunRequest, PromotionDecisionRequest, PromotionRunId, PromotionRunRepository, LockTermRequest (+4 more)

### Community 31 - ".can"
Cohesion: 0.15
Nodes (6): LeaveApproverSetRequest, LeaveApproverSettingResponse, LeaveApproverService, LeaveApproverSettingRepository, PermissionDecision, LeaveApproverControllerTest

### Community 32 - ".createRelease"
Cohesion: 0.12
Nodes (7): ReleaseResponse, ReleaseTransitionRequest, UpdateReleaseRequest, PlatformReleaseController, PlatformReleaseRepository, PlatformReleaseService, PlatformReleaseControllerTest

### Community 33 - "Edu360OtpResource.java"
Cohesion: 0.06
Nodes (5): Edu360OtpResource, Edu360OtpResourceProvider, Edu360OtpResourceProviderFactory, TotpCheck, TotpCheckTest

### Community 34 - "Platform Operations & Governance"
Cohesion: 0.12
Nodes (7): GoLiveBlockerItem, GoLiveCheckResponse, CreatePlanRequest, TenantActivationRequest, TenantLifecycleActionRequest, UpdatePlanRequest, PlatformTenantControllerTest

### Community 35 - "org.springframework.transaction.annotation.Transactional"
Cohesion: 0.04
Nodes (13): GradingSchemeResponse, DecideApprovalRequestDto, AuditEntry, CourseOfferingRepository, InstitutionService, MasterCalendarService, ProgramYearRepository, SchoolClassRepository (+5 more)

### Community 36 - "BackupStatusControllerTest"
Cohesion: 0.21
Nodes (7): BackupStatusController, BackupStatusEntryResponse, BackupStatusResponse, TenantBackupStatusController, TenantBackupStatusResponse, TenantBackupStatusRepository, BackupStatusControllerTest

### Community 37 - "Application Configuration & Security"
Cohesion: 0.12
Nodes (5): JpaRepositoryConfig, RestClientConfig, PlatformJpaConfig, DataSourceConfig, TenantContextFilterConfig

### Community 38 - "RoleDefinition"
Cohesion: 0.10
Nodes (6): DevRoleAssignmentSeeder, Caller, RoleCustomService, RoleDefinitionRepository, StandardRoleRepository, DevRoleAssignmentSeederTest

### Community 39 - "InstitutionSetupActionTest.java"
Cohesion: 0.03
Nodes (19): BulkApprovalRequest, BulkApproveItem, BulkApproveRequestDto, LinkGuardianRequest, InstitutionBlockResponse, InstitutionPageResponse, InstitutionResponse, UpdateAcademicSessionRequest (+11 more)

### Community 40 - "io.swagger.v3.oas.annotations.Operation"
Cohesion: 0.08
Nodes (25): ProgramRuleController, ProgramRuleSetResponse, AcademicSessionResponse, CourseOfferingResponse, CreateCourseOfferingRequest, CreateProgramYearRequest, CreateSchoolClassRequest, CreateSectionRequest (+17 more)

### Community 41 - "InstitutionAdminPages.tsx"
Cohesion: 0.05
Nodes (37): AcademicSessionItem, CreateAcademicSessionPayload, CreateDepartmentPayload, CreateInstitutionRequest, CreateMasterCalendarEventPayload, CreateProgramPayload, DepartmentItem, InstitutionPageResponse (+29 more)

### Community 43 - "ProfileService"
Cohesion: 0.10
Nodes (6): FileObjectRepository, UncheckedFileStorageException, ProfileUpdateRequest, ProfileService, StoredPhoto, ProfileServiceTest

### Community 45 - "TenantRoutingDataSource"
Cohesion: 0.12
Nodes (5): TenantDatabaseCredentials, CachedPool, TenantRoutingDataSource, TenantSecretsResolver, TenantRoutingDataSourceTest

### Community 46 - "StoredPlatformAccessLookup"
Cohesion: 0.18
Nodes (3): PlatformCustomRoleActionRepository, PlatformStepInRepository, StoredPlatformAccessLookup

### Community 47 - "PlatformRoleResponse"
Cohesion: 0.14
Nodes (8): PlatformRoleResponse, PlatformCustomRoleRepository, PlatformCustomRoleService, PlatformCustomRoleStatus, ACTIVE, ARCHIVED, DRAFT, INACTIVE

### Community 49 - "KeycloakAdminClient"
Cohesion: 0.19
Nodes (3): KeycloakAdminClient, KeycloakUser, OtpEnrolment

### Community 50 - "UserLeaveBalance.java"
Cohesion: 0.31
Nodes (3): UserLeaveBalance, UserLeaveBalanceId, UserLeaveBalanceRepository

### Community 53 - "org.springframework.stereotype.Component"
Cohesion: 0.04
Nodes (13): ActiveProfileValidatorInitializer, ActiveRoleRequiredFilter, CorrelationIdFilter, MustChangePasswordFilter, NormalizedRequestWrapper, PathNormalizationFilter, TrialReadOnlyFilter, LocalFileStorage (+5 more)

### Community 54 - "Platform Operations & Governance"
Cohesion: 0.19
Nodes (6): CreateIntegrationTypeRequest, IntegrationTypeResponse, UpdateIntegrationTypeRequest, IntegrationType, IntegrationTypeRepository, IntegrationTypeControllerTest

### Community 55 - "AcademicSessionRepository"
Cohesion: 0.05
Nodes (26): ApiException, Claim, IdempotencyService, AcademicCalendarEvent, AcademicCalendarEventRepository, AcademicSessionRepository, CalendarDayStatus, CalendarDayStatusService (+18 more)

### Community 56 - ".grantAccess"
Cohesion: 0.23
Nodes (3): GrantSupportAccessRequest, PlatformSupportControllerTest, TenantSupportControllerTest

### Community 57 - "SessionDeviceCard.tsx"
Cohesion: 0.26
Nodes (11): DeviceType, SessionResponse, sessionsService, ActivityKind, activityStatus(), deviceTitle(), base, NOW (+3 more)

### Community 58 - ".provisionUser"
Cohesion: 0.14
Nodes (5): TopUserCreateRequest, CreateUserRequest, TopUserControllerTest, UserCreateKeycloakA07Test, UserCreationControllerTest

### Community 59 - "RbacPrincipal"
Cohesion: 0.08
Nodes (14): ProgramRuleService, PromotionRunResponse, PromotionService, StudentStatusResponse, WeeklyOffRuleRequest, WeeklyOffRuleResponse, WeeklyOffRuleService, RbacPrincipal (+6 more)

### Community 60 - "RbacActionTest.java"
Cohesion: 0.08
Nodes (4): BulkApprovalResponse, ItemResult, SelfApprovalForbiddenException, TenantContext

### Community 61 - "PlatformPrincipal"
Cohesion: 0.16
Nodes (6): PlatformPrincipal, PlatformRoleTemplateResponse, PlatformRoleTemplateActionRepository, PlatformRoleTemplateRepository, PlatformRoleTemplateService, PlatformRoleTemplateServiceTest

### Community 62 - "Core Utilities"
Cohesion: 0.09
Nodes (21): compilerOptions, allowImportingTsExtensions, erasableSyntaxOnly, jsx, lib, module, moduleDetection, moduleResolution (+13 more)

### Community 63 - "PlatformUserManagementService"
Cohesion: 0.07
Nodes (14): Build and test, Configuration profiles, Current architecture status, EDU 360 API, Requirements, Run locally, DevAccount, DevAdminSeeder (+6 more)

### Community 64 - "PlatformCustomRoleServiceTest"
Cohesion: 0.22
Nodes (6): AccessMode, HANDED_OVER, SHARED, CreatePlatformRoleRequest, RoleActionItem, PlatformCustomRoleServiceTest

### Community 66 - "jakarta.servlet.http.HttpServletRequest"
Cohesion: 0.08
Nodes (6): AuditFailureException, ActiveRoleRequiredException, FeatureNotAvailableException, GlobalExceptionHandler, InvalidCredentialsException, GlobalExceptionHandlerTest

### Community 67 - "PlatformDataProtectionService"
Cohesion: 0.10
Nodes (13): CreateDeletionRequestDto, CreateRetentionChangeRequest, DataProtectionDtos, DecideDeletionRequestDto, DeletionRequestResponse, ExportRequestResponse, RetentionChangeRequestResponse, RetentionPolicyResponse (+5 more)

### Community 68 - "Core Utilities"
Cohesion: 0.10
Nodes (19): compilerOptions, allowImportingTsExtensions, erasableSyntaxOnly, lib, module, moduleDetection, moduleResolution, noEmit (+11 more)

### Community 71 - "PermissionEvaluatorService"
Cohesion: 0.14
Nodes (8): UserIdentityLinkRepository, ActiveResolution, PermissionEvaluatorService, UserDeactivationController, UserEditController, UserGovernanceGuard, UserRoleAssignment, UserRoleAssignmentRepository

### Community 72 - "EDU 360 CI/CD Engineering Standard"
Cohesion: 0.18
Nodes (10): 1. Purpose and delivery model, 2. Pipeline stages, 3. PostgreSQL migration validation, 4. Pull-request and branch protection, 5. Artifact and release integrity, 6. Deployment and operations — intentionally not enabled, 7. Follow-up acceptance checklist, EDU 360 CI/CD Engineering Standard (+2 more)

### Community 73 - "RoleDefinitionId"
Cohesion: 0.13
Nodes (7): AssignRoleRequest, RoleDefinitionId, UserRoleAssignmentId, SwitchActiveRoleRequest, RoleAssignmentControllerTest, UserGovernanceGuardTest, UserMeControllerTest

### Community 76 - ".as"
Cohesion: 0.06
Nodes (8): GuardianChangeDecisionRequest, GuardianChangeRequestDto, ApprovalDelegationControllerTest, ApprovalEngineB2Test, ApprovalInboxControllerTest, ApprovalTestWorld, GuardianInstitutionPromotionB2Test, GuardianLinkControllerTest

### Community 77 - "InstitutionSetupActionTest"
Cohesion: 0.07
Nodes (14): ClassTeacherAssignmentRepository, ClassTeacherAssignRequest, HodAssignRequest, MentorAssignRequest, StudentLoginSwitchRequest, MentorAssignmentRepository, MentorAssignmentService, StaffAssignment (+6 more)

### Community 78 - "package.json"
Cohesion: 0.04
Nodes (61): dependencies, axios@^1.12.2, clsx@^2.1.1, date-fns@^4.4.0, react@^19.2.0, react-dom@^19.2.0, react-redux@^9.2.0, react-router-dom@^7.18.5 (+53 more)

### Community 79 - "Authentication & Identity Management"
Cohesion: 0.13
Nodes (14): KC_BOOTSTRAP_ADMIN_PASSWORD, KC_BOOTSTRAP_ADMIN_USERNAME, KC_SPI_THEME_CACHE_TEMPLATES, KC_SPI_THEME_CACHE_THEMES, KC_SPI_THEME_STATIC_MAX_AGE, command, environment, image (+6 more)

### Community 81 - "Platform Operations & Governance"
Cohesion: 0.24
Nodes (4): AuditLogEntryResponse, AuditLogPageResponse, PlatformAuditOutboxEventRepository, PlatformAuditLogControllerTest

### Community 82 - "lombok.Setter"
Cohesion: 0.07
Nodes (40): StudentProfile, AcademicSession, ClassTeacherAssignment, CourseOffering, Department, Institution, MentorAssignment, Program (+32 more)

### Community 83 - "org.springframework.security.core.Authentication"
Cohesion: 0.06
Nodes (15): GradingSchemeController, PromotionController, StudentStatusController, SubjectAllocationController, SubjectAllocationResponse, ApprovalDelegationController, DelegationResponse, GuardianLinkController (+7 more)

### Community 84 - ".stepIn"
Cohesion: 0.25
Nodes (4): StepInRequest, StepInResponse, PlatformStepInService, PlatformStepInServiceTest

### Community 87 - "PlatformReportController.java"
Cohesion: 0.11
Nodes (6): InvoiceRepository, EntitlementsResponse, PlatformFeatureToggleController, PlatformReportController, PlatformReportResponse, PlatformSecurityReportControllerTest

### Community 88 - "Docker Localstack"
Cohesion: 0.15
Nodes (12): AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, DEFAULT_REGION, SERVICES, environment, image, ports, volumes (+4 more)

### Community 89 - ".Github Workflows"
Cohesion: 0.23
Nodes (13): steps, steps, steps, GITHUB_TOKEN, NVD_API_KEY, PGPASSWORD, PSQL, steps (+5 more)

### Community 92 - "SessionServiceTest"
Cohesion: 0.30
Nodes (3): SignInContext, UserDeviceRepository, SessionServiceTest

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

### Community 97 - "SecurityPolicyService"
Cohesion: 0.14
Nodes (6): PasswordPolicyService, PlatformSettingValueRepository, RealmSecuritySyncService, SecurityPolicy, SecurityPolicyService, RegI06PasswordPolicyTest

### Community 100 - "UserLeaveRequest.java"
Cohesion: 0.31
Nodes (3): UserLeaveRequest, UserLeaveRequestId, UserLeaveRequestRepository

### Community 102 - "PlatformSupportController"
Cohesion: 0.16
Nodes (4): SupportAccessGrantResponse, SupportTicketResponse, PlatformSupportController, TenantSupportController

### Community 103 - "GradingSchemeVersion.java"
Cohesion: 0.36
Nodes (3): GradingSchemeVersion, GradingSchemeVersionId, GradingSchemeVersionRepository

### Community 104 - "ProgramRuleSet"
Cohesion: 0.39
Nodes (3): ProgramRuleSet, ProgramRuleSetId, ProgramRuleSetRepository

### Community 106 - "UserBulkUploadControllerTest"
Cohesion: 0.19
Nodes (3): StartImportRequest, UserImportRequest, UserBulkUploadControllerTest

### Community 107 - "StudentGuardianLink"
Cohesion: 0.29
Nodes (3): GuardianLinkResponse, GuardianProfile, StudentGuardianLink

### Community 108 - ".Github Workflows"
Cohesion: 0.25
Nodes (8): if, name, permissions, runs-on, dependency-review, permissions, contents, pull-requests

### Community 109 - "React + TypeScript + Vite"
Cohesion: 0.50
Nodes (3): Expanding the ESLint configuration, React Compiler, React + TypeScript + Vite

### Community 111 - ".Github Workflows"
Cohesion: 0.29
Nodes (7): name, permissions, runs-on, steps, codeql, actions, security-events

### Community 113 - "RegM03SecurityChainTest.java"
Cohesion: 0.18
Nodes (3): ApiApplicationTests, RegM03SecurityChainTest, DeviceDescriptorTest

### Community 114 - "RoleDefinition"
Cohesion: 0.06
Nodes (36): UserAccount, AssignmentStatus, ACTIVE, ENDED, CustomScope, INSTITUTION, UNIVERSITY_OFFICE, CreateUserCheckRequest (+28 more)

### Community 115 - "lombok.Data"
Cohesion: 0.10
Nodes (11): LoginRequest, RefreshRequest, TenantRoleTemplate, TenantRoleTemplateAction, TenantRoleTemplateActionId, TenantRoleTemplateActionRepository, TenantRoleTemplateId, TenantRoleTemplateRepository (+3 more)

### Community 116 - "RoleCustomController"
Cohesion: 0.50
Nodes (3): RoleDetailResponse, UpdateRoleActionSetRequest, RoleCustomController

### Community 118 - "OtpConfirmOutcome"
Cohesion: 0.33
Nodes (6): OtpConfirmOutcome, ALREADY_ENROLLED, CONFIRMED, INVALID_CODE, NOT_FOUND, TOO_MANY_ATTEMPTS

### Community 119 - ".Github Workflows"
Cohesion: 0.29
Nodes (7): run, defaults, name, runs-on, steps, frontend, working-directory

### Community 120 - ".Github Workflows"
Cohesion: 0.33
Nodes (6): on, pull_request, push, workflow_dispatch, branches, branches

### Community 123 - "Core Utilities"
Cohesion: 0.40
Nodes (4): printWidth, proseWrap, semi, trailingComma

### Community 124 - "Utils"
Cohesion: 0.70
Nodes (3): formErrorMapping, normalizeFormErrors(), splitFullName()

### Community 125 - "Delegation"
Cohesion: 0.20
Nodes (3): Delegation, UserNotification, UserNotificationId

### Community 126 - "TemporaryPasswordResponseAdvice.java"
Cohesion: 0.16
Nodes (3): TemporaryPasswordResponseAdvice, PasswordResetResponse, TemporaryPasswordResponseAdviceTest

### Community 127 - ".decideApproval"
Cohesion: 0.27
Nodes (6): ApprovalInboxController, ApprovalInboxResponseDto, PageInfoDto, ApprovalStepDto, ApprovalSummaryDto, DecideApprovalResponseDto

### Community 129 - "ApprovalStep"
Cohesion: 0.25
Nodes (3): ApprovalStep, ImportRowErrorResponse, ImportRowError

### Community 137 - "PlatformRole"
Cohesion: 0.06
Nodes (19): BusinessRuleViolationException, DuplicateResourceException, PlatformRole, BILLING_MANAGER, DATA_PROTECTION_OFFICER, PLATFORM_ADMIN, PRODUCT_OWNER, RELEASE_MANAGER (+11 more)

### Community 139 - "scripts"
Cohesion: 0.20
Nodes (10): scripts, build, dev, format, format:check, lint, preview, test (+2 more)

### Community 140 - "PlatformActionTest.java"
Cohesion: 0.05
Nodes (10): UsageMetricDailyRepository, TenantRepository, TenantState, ACTIVE, ARCHIVED, CREATED, DELETED, SUSPENDED (+2 more)

### Community 145 - "ModuleEntitlementEnforcer.java"
Cohesion: 0.09
Nodes (6): ModuleNotEnabledException, ModuleEntitlementEnforcer, ModuleEntitlementService, PlanEntitlementRepository, TenantEntitlementRepository, RegI07ModuleEntitlementTest

### Community 165 - "UserBulkUploadService.java"
Cohesion: 0.18
Nodes (3): ImportRowErrorRepository, RowValidationError, UserBulkUploadService

### Community 168 - "ResourceNotFoundException"
Cohesion: 0.12
Nodes (7): ResourceNotFoundException, LeaveApproverController, UserListResponse, UserSummaryResponse, UserPasswordResetUnlockController, UserRequestSupport, UserViewController

### Community 171 - "hooks/index.ts"
Cohesion: 0.43
Nodes (3): useClickOutside(), useDebounce(), useUrlFilters()

### Community 175 - ".resolve"
Cohesion: 0.22
Nodes (5): LoginTenantResolver, PlatformLogin, Resolution, TenantLogin, LoginTenantResolverTest

### Community 176 - "RbacTestWorld"
Cohesion: 0.24
Nodes (3): ActionRow, RbacTestWorld, StandardRoleRow

### Community 189 - "SubjectAllocation"
Cohesion: 0.36
Nodes (3): SubjectAllocation, SubjectAllocationId, SubjectAllocationRepository

### Community 190 - ".createUserImport"
Cohesion: 0.39
Nodes (3): ImportJobResponse, ImportTemplateResponse, UserBulkUploadController

### Community 206 - "UsernameKind"
Cohesion: 0.50
Nodes (4): UsernameKind, EMAIL, MOBILE, STUDENT_CODE

### Community 233 - "theme.ts"
Cohesion: 0.32
Nodes (9): ThemeToggle(), AuthLayout(), applyTheme(), getStoredTheme(), initTheme(), setStoredTheme(), systemTheme(), Theme (+1 more)

## Knowledge Gaps
- **143 isolated node(s):** `LOGIN_SUCCESS`, `LOGIN_FAILURE`, `LOGOUT`, `ACCOUNT_CREATED`, `PASSWORD_CHANGED` (+138 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 807 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **90 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `KeycloakAdminClient` connect `KeycloakAdminClient` to `org.junit.jupiter.api.DisplayName`, `PlatformRole`, `Institution Structure & Hierarchy`, `AuthServiceTest`, `PlatformActionTest.java`, `RegB3SignInTest.java`, `AuthService`, `lombok.RequiredArgsConstructor`, `AuditLogService`, `.institution`, `Platform Operations & Governance`, `ResourceNotFoundException`, `org.springframework.stereotype.Component`, `.provisionUser`, `PlatformUserManagementService`, `RegB3SignInTest`, `PermissionEvaluatorService`, `RoleDefinitionId`, `SessionService`, `KeycloakProperties`, `KeycloakRealmSecurityService`, `SessionServiceTest`, `.listSessions`, `OtpConfirmOutcome`, `map`?**
  _High betweenness centrality (0.024) - this node is a cross-community bridge._
- **What connects `LOGIN_SUCCESS`, `LOGIN_FAILURE`, `LOGOUT` to the rest of the system?**
  _143 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `userManagementService.ts` be split into smaller, more focused modules?**
  _Cohesion score 0.03529486447025963 - nodes in this community are weakly interconnected._
- **Why does `KeycloakProperties` connect `KeycloakProperties` to `SecurityPolicyService`, `jakarta.servlet.http.HttpServletRequest`, `PlatformRole`, `SessionService`, `Institution Structure & Hierarchy`, `AuthServiceTest`, `RegB3SignInTest.java`, `.resolve`, `KeycloakAdminClient`, `AuthService`, `org.springframework.stereotype.Component`, `KeycloakRealmSecurityService`, `AuditLogService`, `AuthService`, `map`, `PlatformUserManagementService`?**
  _High betweenness centrality (0.019) - this node is a cross-community bridge._
- **Should `list` be split into smaller, more focused modules?**
  _Cohesion score 0.05491268672417421 - nodes in this community are weakly interconnected._
- **Why does `AuditLogService` connect `AuditLogService` to `RbacActionTest`, `.authorize`, `SelfServiceScopeA08Test.java`, `lombok.NoArgsConstructor`, `org.junit.jupiter.api.DisplayName`, `org.springframework.stereotype.Service`, `Institution Structure & Hierarchy`, `AuthServiceTest`, `com.edu360.api.config.TenantPrincipal`, `RegB3SignInTest.java`, `PlatformActionTest.java`, `lombok.RequiredArgsConstructor`, `.institution`, `AuthService`, `InstitutionId`, `.tenantPrincipal`, `.can`, `.createRelease`, `Platform Operations & Governance`, `org.springframework.transaction.annotation.Transactional`, `RoleDefinition`, `InstitutionSetupActionTest.java`, `ResourceNotFoundException`, `ProfileService`, `RbacTestWorld`, `org.springframework.stereotype.Component`, `AcademicSessionRepository`, `.grantAccess`, `.provisionUser`, `RbacPrincipal`, `RbacActionTest.java`, `PlatformDataProtectionService`, `PermissionEvaluatorService`, `RoleDefinitionId`, `SessionService`, `InstitutionSetupActionTest`, `PlatformReportController.java`, `SessionServiceTest`, `RegM03SecurityChainTest.java`, `map`?**
  _High betweenness centrality (0.016) - this node is a cross-community bridge._
- **Should `RbacActionTest` be split into smaller, more focused modules?**
  _Cohesion score 0.09059233449477352 - nodes in this community are weakly interconnected._
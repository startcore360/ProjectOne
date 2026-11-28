# Graph Report - Personal project  (2026-11-11)

## Corpus Check
- 914 files · ~438,822 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 25 file(s) not represented in the graph (top: .css 8, .properties 7, (none) 6)

## Summary
- 5654 nodes · 26366 edges · 233 communities (116 shown, 117 thin omitted)
- Extraction: 87% EXTRACTED · 13% INFERRED · 0% AMBIGUOUS · INFERRED: 3351 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `5b93c4d7`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- userManagementService.ts
- Institution Structure & Hierarchy
- list
- RbacActionTest
- org.springframework.transaction.annotation.Transactional
- Services
- Institution Structure & Hierarchy
- Self-Service & Role Switching
- org.junit.jupiter.api.DisplayName
- PageHeader
- PermissionEvaluatorService
- SessionControllerTest
- Institution Structure & Hierarchy
- AuthServiceTest
- .decideApproval
- org.springframework.security.oauth2.jwt.Jwt
- .signIn
- PlatformRole
- RegB3SignInTest.java
- lombok.RequiredArgsConstructor
- .createCustomRole
- org.springframework.stereotype.Service
- .lockTerm
- .institution
- Audit
- AuthService
- design-system/components/index.ts
- TenantsView.tsx
- org.junit.jupiter.api.DisplayName
- RoleDashboardPage.tsx
- .tenantPrincipal
- .can
- .createRelease
- Edu360OtpResource.java
- Platform Operations & Governance
- StructureService
- BackupStatusControllerTest
- Application Configuration & Security
- RoleDefinition
- InstitutionSetupActionTest.java
- org.springframework.security.core.Authentication
- InstitutionAdminPages.tsx
- Application Configuration & Security
- ProfileService
- TenantRoutingDataSource
- PlatformStepInService.java
- PlatformRoleResponse
- Multi-Tenant Routing & Lifecycle
- KeycloakAdminClient
- SelfServiceController.java
- PlatformPermissionServiceTest
- .institution
- ioexception
- Platform Operations & Governance
- MasterCalendarControllerTest
- .grantAccess
- SessionsPage.tsx
- .provisionUser
- RbacPrincipal
- org.keycloak.models.KeycloakSession
- PlatformPrincipal
- Core Utilities
- PlatformUserManagementService
- RoleAssignmentControllerTest
- RegB3SignInTest
- jakarta.servlet.http.HttpServletRequest
- PlatformDataProtectionService
- Core Utilities
- .assignClassTeacher
- RoleActionRepository
- UserRoleAssignment
- EDU 360 CI/CD Engineering Standard
- UserRoleAssignmentId
- SessionService
- Application Configuration & Security
- .as
- .assignMentor
- package.json
- Authentication & Identity Management
- KeycloakProperties
- Platform Operations & Governance
- lombok.Builder
- org.springframework.http.ResponseEntity
- .usageCounts
- KeycloakRealmSecurityService
- InstitutionSetupActionTest
- PlatformPermissionService
- Docker Localstack
- .Github Workflows
- RegM03SecurityChainTest
- InstitutionController
- SessionServiceTest
- Docker Postgres
- .Github Workflows
- .Github Workflows
- .Github Workflows
- SecurityPolicyService
- .updatePolicy
- org.springframework.stereotype.Component
- PlatformSupportService
- InstitutionId
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
- RBAC & Permission Evaluator
- TenantRoleTemplateAction
- RBAC & Permission Evaluator
- .set
- .Github Workflows
- .Github Workflows
- ScopeType
- UserBulkUploadService.java
- Core Utilities
- Utils
- Delegation
- TemporaryPasswordResponseAdvice.java
- .decideApproval
- JdbcTenantAuditStore
- ImportRowError
- Platform Operations & Governance
- components/README.md
- .principal
- WebMvcConfig.java
- PlatformUserDeviceRepository
- .assertRoleInStatusGrantsNothing
- .validate
- AuditLogServiceTest
- scripts
- org.junit.jupiter.api.BeforeEach
- Core Utilities
- Database Schema & Migrations
- org.keycloak.models.KeycloakSession
- ModuleEntitlementEnforcer.java
- TotpCheckTest.java
- .createDelegation
- Core Utilities
- Core Utilities
- .startImport
- UserPasswordResetUnlockUnlockActionTest
- UserPasswordResetUnlockController
- UserMeController
- ReferenceCodeArchitectureTest
- hooks/index.ts
- ActiveProfileValidatorInitializer.java
- Tenant
- Institution
- PlatformUserRepository
- RbacTestWorld
- .assignMentor
- AuditLog.java
- TenantState
- PlatformBillingControllerTest
- ActiveRoleA02Test
- SelfServiceScopeA08Test
- UserViewActionTest
- AuditFailureException
- EDU 360 API
- GuardianLinkController.java
- PromotionRunId
- .createGradingScheme
- SubjectAllocationService
- .createUserImport
- UserAccount
- IntegrationType.java
- OpenApiConfig.java
- ApprovalInboxResponseDto
- PlatformAuditOutboxEvent
- .permissions
- ApprovalRouting
- RegM01Test.java
- MutableClock
- UsernameKind
- PlanEntitlement
- ApprovalRequest
- ImportJob
- ApprovalStep
- TermLock
- TermLockEvent
- InstitutionBlockResponse.java
- TenantUsageMonthly
- com.edu360:edu360-otp-extension
- theme.ts

## God Nodes (most connected - your core abstractions)
1. `PermissionEvaluatorService` - 153 edges
2. `RbacPrincipal` - 151 edges
3. `PlatformActionTest` - 127 edges
4. `AuditLogService` - 123 edges
5. `TenantPrincipal` - 120 edges
6. `AuditEventType` - 111 edges
7. `InstitutionSetupActionTest` - 109 edges
8. `KeycloakAdminClient` - 100 edges
9. `react` - 100 edges
10. `UserAccountId` - 85 edges

## Surprising Connections (you probably didn't know these)
- `Configuration profiles` --references--> `DevAdminSeeder`  [INFERRED]
  api/README.md → api/src/main/java/com/edu360/api/config/DevAdminSeeder.java
- `DateField()` --calls--> `TextField()`  [EXTRACTED]
  app/src/design-system/components/DateField.tsx → app/src/design-system/components/TextField.tsx
- `DateTimeField()` --calls--> `TextField()`  [EXTRACTED]
  app/src/design-system/components/DateTimeField.tsx → app/src/design-system/components/TextField.tsx
- `CreateInstitutionModal()` --calls--> `SelectField()`  [EXTRACTED]
  app/src/features/institution-admin/components/institutions/CreateInstitutionModal.tsx → app/src/design-system/components/SelectField.tsx
- `CalendarEventsPage()` --calls--> `TextField()`  [EXTRACTED]
  app/src/features/institution-admin/pages/InstitutionSetupPages.tsx → app/src/design-system/components/TextField.tsx

## Import Cycles
- None detected.

## Communities (233 total, 117 thin omitted)

### Community 0 - "userManagementService.ts"
Cohesion: 0.05
Nodes (106): Badge(), Button(), CheckboxField(), Kicker(), Modal(), Notice(), TextField(), academicService (+98 more)

### Community 1 - "Institution Structure & Hierarchy"
Cohesion: 0.10
Nodes (33): GradingSchemeVersion, ProgramRuleSet, SubjectAllocation, ApprovalBatch, FileObject, StudentProfile, UserIdentityLink, AcademicSession (+25 more)

### Community 2 - "list"
Cohesion: 0.05
Nodes (20): GradingSchemeVersionRepository, ProgramRuleSetRepository, SubjectAllocationRepository, ApprovalBatchRepository, DelegationRepository, GuardianProfileRepository, StudentGuardianLinkRepository, StudentProfileRepository (+12 more)

### Community 3 - "RbacActionTest"
Cohesion: 0.12
Nodes (4): CreateCustomRoleRequest, CreateRolldownOverrideRequest, RbacActionTest, RoleCustomControllerTest

### Community 4 - "org.springframework.transaction.annotation.Transactional"
Cohesion: 0.09
Nodes (5): RecordPaymentRequest, InvoiceRepository, PlatformBillingService, PlatformAuditWriter, TenantManagementService

### Community 5 - "Services"
Cohesion: 0.04
Nodes (87): react-redux@^9.2.0, Edu360Logo(), Edu360LogoProps, LogoStyle, RoleSwitcher(), RoleSwitcherProps, mockRoles, AuthenticatorSetup() (+79 more)

### Community 6 - "Institution Structure & Hierarchy"
Cohesion: 0.05
Nodes (50): App(), DateField(), DateTimeField(), EmptyState(), SelectField(), TextAreaField(), GradingSchemeItem, ProgramRuleSetItem (+42 more)

### Community 7 - "Self-Service & Role Switching"
Cohesion: 0.07
Nodes (27): GradingSchemeVersionId, ProgramRuleSetId, SubjectAllocationId, ApprovalBatchId, ApprovalRequestId, ApprovalStepId, DelegationId, RefreshRequest (+19 more)

### Community 8 - "org.junit.jupiter.api.DisplayName"
Cohesion: 0.04
Nodes (8): CreateProgramRuleSetRequest, CreateSubjectAllocationRequest, UserImportRequest, CreateInvoiceRequest, UpdateTenantSettingsRequest, EntitlementsUpdateRequest, UpdateSupportTicketRequest, PlatformActionTest

### Community 9 - "PageHeader"
Cohesion: 0.05
Nodes (109): PageHeader(), Spinner(), ConfirmDialog(), DataTable(), ErrorState(), MetricCard(), Panel(), SearchBox() (+101 more)

### Community 10 - "PermissionEvaluatorService"
Cohesion: 0.07
Nodes (20): GradingSchemeService, ProgramRuleService, PromotionRunRepository, PromotionService, ApprovalDelegationService, ApprovalInboxService, ApprovalRequestRepository, ApprovalStepRepository (+12 more)

### Community 12 - "Institution Structure & Hierarchy"
Cohesion: 0.10
Nodes (15): BusinessRuleViolationException, DuplicateResourceException, TopUserResponse, UserAccountStatus, ACTIVE, DEACTIVATED, PENDING_ACTIVATION, UserType (+7 more)

### Community 13 - "AuthServiceTest"
Cohesion: 0.10
Nodes (13): CredentialInfo, IdentityLinkStatus, ACTIVE, REVOKED, ResolvedUser, UserAccountId, UserIdentityLinkId, ProfileService (+5 more)

### Community 16 - ".signIn"
Cohesion: 0.18
Nodes (3): CreateDelegationRequest, RoleReadA04Test, ScopeA05Test

### Community 17 - "PlatformRole"
Cohesion: 0.14
Nodes (10): AuthTokenResponse, AuthUserResponse, PlatformRole, BILLING_MANAGER, DATA_PROTECTION_OFFICER, PLATFORM_ADMIN, PRODUCT_OWNER, RELEASE_MANAGER (+2 more)

### Community 18 - "RegB3SignInTest.java"
Cohesion: 0.07
Nodes (11): StudentStatusService, AuthService, RoleInfo, AccountSetupRequiredException, KeycloakAuthClient, PasswordCheck, INVALID, VERIFIED (+3 more)

### Community 19 - "lombok.RequiredArgsConstructor"
Cohesion: 0.11
Nodes (24): BulkApproveItem, BulkApproveRequestDto, AuthController, SessionSettingsController, SessionSettingsResponse, TenantPrincipal, UnauthorizedException, TopUserController (+16 more)

### Community 21 - "org.springframework.stereotype.Service"
Cohesion: 0.06
Nodes (17): MfaRequirementService, TenantUserLookup, TopUserService, UserAccountRepository, UserAccountService, UserIdentityLinkRepository, AssignedRole, PlatformUserManagementService (+9 more)

### Community 22 - ".lockTerm"
Cohesion: 0.14
Nodes (5): LockTermRequest, TermLockResponse, UnlockTermRequest, TermLockController, TermLockControllerTest

### Community 23 - ".institution"
Cohesion: 0.14
Nodes (6): ActionCatalogRepository, InstitutionScopeRequest, PermissionEvaluatorServiceTest, UserDeactivationActionTest, UserEditActionTest, UserPasswordResetUnlockActionTest

### Community 24 - "Audit"
Cohesion: 0.03
Nodes (67): AuditEventType, ACADEMIC_SESSION_CREATED, ACADEMIC_SESSION_UPDATED, ACCOUNT_CREATED, APPROVAL, APPROVAL_BULK_DECIDED, APPROVAL_DELEGATED, APPROVAL_DELEGATION_CANCELLED (+59 more)

### Community 25 - "AuthService"
Cohesion: 0.12
Nodes (4): ChangePasswordRequest, IdentityEventRequest, AuthControllerTest, RegM03Test

### Community 26 - "design-system/components/index.ts"
Cohesion: 0.04
Nodes (52): EmptyState(), ErrorState(), Button(), ButtonProps, ButtonSize, ButtonVariant, sizes, variants (+44 more)

### Community 27 - "TenantsView.tsx"
Cohesion: 0.06
Nodes (56): Badge(), Icon(), paths, CreatePlanPayload, ExportRequestDto, IsolationMode, PlanItem, platformTenantService (+48 more)

### Community 28 - "org.junit.jupiter.api.DisplayName"
Cohesion: 0.14
Nodes (4): BlockDecisionRequest, BlockInstitutionRequest, CreateInstitutionRequest, InstitutionControllerTest

### Community 29 - "RoleDashboardPage.tsx"
Cohesion: 0.11
Nodes (32): Kpi, PageHeader(), DashboardPanel(), DashboardPanelProps, QuietEmpty(), GenericWorkspace(), GenericWorkspaceProps, RoleWorkspace() (+24 more)

### Community 30 - ".tenantPrincipal"
Cohesion: 0.10
Nodes (3): ChangeStudentStatusRequest, CreateGradingSchemeRequest, PromotionDecisionRequest

### Community 31 - ".can"
Cohesion: 0.14
Nodes (7): GoLiveBlockerItem, GoLiveCheckResponse, LeaveApproverSetRequest, LeaveApproverSettingResponse, LeaveApproverService, LeaveApproverSettingRepository, LeaveApproverControllerTest

### Community 32 - ".createRelease"
Cohesion: 0.19
Nodes (4): CreateReleaseRequest, PlatformReleaseRepository, PlatformReleaseService, PlatformReleaseControllerTest

### Community 34 - "Platform Operations & Governance"
Cohesion: 0.15
Nodes (4): CreatePlanRequest, TenantActivationRequest, UpdatePlanRequest, PlatformTenantControllerTest

### Community 35 - "StructureService"
Cohesion: 0.06
Nodes (9): AcademicSessionRepository, CourseOfferingRepository, MentorAssignmentService, ProgramYearRepository, SchoolClassRepository, SectionRepository, SemesterRepository, StructureService (+1 more)

### Community 36 - "BackupStatusControllerTest"
Cohesion: 0.27
Nodes (5): BackupStatusController, BackupStatusEntryResponse, BackupStatusResponse, TenantBackupStatusRepository, BackupStatusControllerTest

### Community 37 - "Application Configuration & Security"
Cohesion: 0.12
Nodes (5): JpaRepositoryConfig, RestClientConfig, PlatformJpaConfig, DataSourceConfig, TenantContextFilterConfig

### Community 38 - "RoleDefinition"
Cohesion: 0.12
Nodes (6): DevRoleAssignmentSeeder, RoleDefinition, RoleDefinitionId, RoleDefinitionRepository, StandardRoleRepository, DevRoleAssignmentSeederTest

### Community 39 - "InstitutionSetupActionTest.java"
Cohesion: 0.03
Nodes (3): LoginRequest, LinkGuardianRequest, UsageMetricSnapshot

### Community 40 - "org.springframework.security.core.Authentication"
Cohesion: 0.06
Nodes (30): SubjectAllocationController, CalendarEventController, AcademicSessionResponse, CourseOfferingResponse, CreateCourseOfferingRequest, CreateProgramYearRequest, CreateSchoolClassRequest, CreateSectionRequest (+22 more)

### Community 41 - "InstitutionAdminPages.tsx"
Cohesion: 0.05
Nodes (35): AcademicSessionItem, CreateAcademicSessionPayload, CreateDepartmentPayload, CreateInstitutionRequest, CreateMasterCalendarEventPayload, CreateProgramPayload, DepartmentItem, InstitutionPageResponse (+27 more)

### Community 43 - "ProfileService"
Cohesion: 0.08
Nodes (7): FileObjectRepository, LocalFileStorage, UncheckedFileStorageException, ProfileUpdateRequest, StoredPhoto, ProfileControllerTest, ProfileServiceTest

### Community 45 - "TenantRoutingDataSource"
Cohesion: 0.11
Nodes (5): TenantDatabaseCredentials, CachedPool, TenantRoutingDataSource, TenantSecretsResolver, TenantRoutingDataSourceTest

### Community 46 - "PlatformStepInService.java"
Cohesion: 0.08
Nodes (8): PlatformAccessLookup, PlatformAccessLookup, StepInRequest, StepInResponse, PlatformStepInRepository, PlatformStepInService, StoredPlatformAccessLookup, PlatformStepInServiceTest

### Community 47 - "PlatformRoleResponse"
Cohesion: 0.10
Nodes (17): AccessMode, HANDED_OVER, SHARED, CreatePlatformRoleRequest, PlatformRoleResponse, RoleActionItem, UpdatePlatformRoleRequest, PlatformCustomRoleAction (+9 more)

### Community 49 - "KeycloakAdminClient"
Cohesion: 0.11
Nodes (11): AccountState, KeycloakAdminClient, KeycloakUser, OtpConfirmOutcome, ALREADY_ENROLLED, CONFIRMED, INVALID_CODE, NOT_FOUND (+3 more)

### Community 50 - "SelfServiceController.java"
Cohesion: 0.11
Nodes (6): LeaveBalanceDto, LeaveRequestDto, NoticeDto, NotificationDto, ProfileResponse, SelfServiceController

### Community 52 - ".institution"
Cohesion: 0.17
Nodes (6): AcademicCalendarEvent, AcademicCalendarEventRepository, CalendarEventService, CalendarEventRequest, CalendarEventResponse, CalendarEventServiceTest

### Community 53 - "ioexception"
Cohesion: 0.17
Nodes (5): CorrelationIdFilter, MustChangePasswordFilter, NormalizedRequestWrapper, PathNormalizationFilter, TenantContextClearingFilter

### Community 54 - "Platform Operations & Governance"
Cohesion: 0.31
Nodes (4): CreateIntegrationTypeRequest, UpdateIntegrationTypeRequest, IntegrationTypeRepository, IntegrationTypeControllerTest

### Community 55 - "MasterCalendarControllerTest"
Cohesion: 0.16
Nodes (6): CreateAcademicSessionRequest, CreateMasterCalendarEventRequest, MasterCalendarEventResponse, UpdateAcademicSessionRequest, MasterCalendarService, MasterCalendarControllerTest

### Community 56 - ".grantAccess"
Cohesion: 0.26
Nodes (3): CreateSupportTicketRequest, GrantSupportAccessRequest, TenantSupportControllerTest

### Community 57 - "SessionsPage.tsx"
Cohesion: 0.06
Nodes (33): PlatformRole, ProfileResponse, profileService, ProfileUpdateRequest, UserType, initialsFor(), ProfileAvatarSection(), ProfileAvatarSectionProps (+25 more)

### Community 58 - ".provisionUser"
Cohesion: 0.17
Nodes (4): TopUserCreateRequest, CreateUserRequest, TopUserControllerTest, UserCreationControllerTest

### Community 59 - "RbacPrincipal"
Cohesion: 0.05
Nodes (17): CreatePromotionRunRequest, ProgramRuleController, ProgramRuleSetResponse, PromotionController, PromotionRunResponse, StudentStatusController, StudentStatusResponse, SupportAccessGrantResponse (+9 more)

### Community 61 - "PlatformPrincipal"
Cohesion: 0.15
Nodes (7): PlatformPrincipal, PlatformRoleTemplateResponse, SavePlatformRoleTemplateRequest, PlatformRoleTemplateActionRepository, PlatformRoleTemplateRepository, PlatformRoleTemplateService, PlatformRoleTemplateServiceTest

### Community 62 - "Core Utilities"
Cohesion: 0.09
Nodes (21): compilerOptions, allowImportingTsExtensions, erasableSyntaxOnly, jsx, lib, module, moduleDetection, moduleResolution (+13 more)

### Community 63 - "PlatformUserManagementService"
Cohesion: 0.09
Nodes (7): KeycloakProvisioningException, PlatformUserStatus, ACTIVE, INACTIVE, PlatformUserUpdateRequest, PlatformUserControllerTest, PlatformUserUpdateTest

### Community 64 - "RoleAssignmentControllerTest"
Cohesion: 0.28
Nodes (3): AssignRoleRequest, PermissionDecision, RoleAssignmentControllerTest

### Community 66 - "jakarta.servlet.http.HttpServletRequest"
Cohesion: 0.07
Nodes (7): ActiveRoleRequiredFilter, ActiveRoleRequiredException, FeatureNotAvailableException, GlobalExceptionHandler, InvalidCredentialsException, ModuleNotEnabledException, RegI07ModuleEntitlementTest

### Community 67 - "PlatformDataProtectionService"
Cohesion: 0.09
Nodes (13): DeletionRequestRepository, CreateRetentionChangeRequest, DataProtectionDtos, DecideDeletionRequestDto, DeletionRequestResponse, ExportRequestResponse, RetentionChangeRequestResponse, RetentionPolicyResponse (+5 more)

### Community 68 - "Core Utilities"
Cohesion: 0.10
Nodes (19): compilerOptions, allowImportingTsExtensions, erasableSyntaxOnly, lib, module, moduleDetection, moduleResolution, noEmit (+11 more)

### Community 69 - ".assignClassTeacher"
Cohesion: 0.32
Nodes (4): ClassTeacherAssignmentRepository, ClassTeacherAssignmentService, ClassTeacherAssignRequest, ClassTeacherAssignmentServiceTest

### Community 70 - "RoleActionRepository"
Cohesion: 0.24
Nodes (3): RoleActionRepository, Caller, RoleCustomService

### Community 71 - "UserRoleAssignment"
Cohesion: 0.15
Nodes (6): AssignmentStatus, ACTIVE, ENDED, UserRoleAssignmentResponse, ActiveResolution, UserRoleAssignment

### Community 72 - "EDU 360 CI/CD Engineering Standard"
Cohesion: 0.18
Nodes (10): 1. Purpose and delivery model, 2. Pipeline stages, 3. PostgreSQL migration validation, 4. Pull-request and branch protection, 5. Artifact and release integrity, 6. Deployment and operations — intentionally not enabled, 7. Follow-up acceptance checklist, EDU 360 CI/CD Engineering Standard (+2 more)

### Community 73 - "UserRoleAssignmentId"
Cohesion: 0.20
Nodes (4): UserRoleAssignmentId, SwitchActiveRoleRequest, UserGovernanceGuardTest, UserMeControllerTest

### Community 76 - ".as"
Cohesion: 0.07
Nodes (9): DecideApprovalRequestDto, GuardianChangeDecisionRequest, GuardianChangeRequestDto, ApprovalDelegationControllerTest, ApprovalEngineB2Test, ApprovalInboxControllerTest, ApprovalTestWorld, GuardianInstitutionPromotionB2Test (+1 more)

### Community 77 - ".assignMentor"
Cohesion: 0.32
Nodes (6): DepartmentRepository, HodAssignRequest, HodAssignmentService, StaffAssignment, StaffAssignmentRepository, HodAssignmentServiceTest

### Community 78 - "package.json"
Cohesion: 0.04
Nodes (60): dependencies, axios@^1.12.2, clsx@^2.1.1, date-fns@^4.4.0, react@^19.2.0, react-dom@^19.2.0, react-router-dom@^7.18.5, redux-persist@^6.0.0 (+52 more)

### Community 79 - "Authentication & Identity Management"
Cohesion: 0.13
Nodes (14): KC_BOOTSTRAP_ADMIN_PASSWORD, KC_BOOTSTRAP_ADMIN_USERNAME, KC_SPI_THEME_CACHE_TEMPLATES, KC_SPI_THEME_CACHE_THEMES, KC_SPI_THEME_STATIC_MAX_AGE, command, environment, image (+6 more)

### Community 80 - "KeycloakProperties"
Cohesion: 0.06
Nodes (3): IdentityEventTokenValidator, KeycloakProperties, KeycloakAdminClientTest

### Community 82 - "lombok.Builder"
Cohesion: 0.10
Nodes (17): Program, ProgramYear, Section, Semester, Subject, Invoice, DeletionRequest, ExportRequest (+9 more)

### Community 83 - "org.springframework.http.ResponseEntity"
Cohesion: 0.06
Nodes (16): MfaEnrolRequest, ApplyDiscountRequest, InvoiceResponse, PlatformBillingController, CreateDeletionRequestDto, PlatformDataProtectionController, PlatformDeletionRequestController, PlatformUserResponse (+8 more)

### Community 84 - ".usageCounts"
Cohesion: 0.30
Nodes (4): UsageCountsResponse, UsageCountsController, UsageMetricDailyRepository, UsageCountsControllerTest

### Community 86 - "InstitutionSetupActionTest"
Cohesion: 0.15
Nodes (6): WeeklyOffRuleRequest, WeeklyOffRuleResponse, WeeklyOffRuleRepository, WeeklyOffRuleService, InstitutionSetupActionTest, WeeklyOffRuleServiceTest

### Community 87 - "PlatformPermissionService"
Cohesion: 0.07
Nodes (11): EntitlementsResponse, PlatformFeatureToggleController, IntegrationTypeController, PlatformPermissionService, PlatformReportController, PlatformReportResponse, PlatformActionCatalogItem, CreateSecurityIncidentRequest (+3 more)

### Community 88 - "Docker Localstack"
Cohesion: 0.15
Nodes (12): AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, DEFAULT_REGION, SERVICES, environment, image, ports, volumes (+4 more)

### Community 89 - ".Github Workflows"
Cohesion: 0.26
Nodes (13): steps, steps, steps, GITHUB_TOKEN, PGPASSWORD, PSQL, steps, steps (+5 more)

### Community 92 - "SessionServiceTest"
Cohesion: 0.25
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
Cohesion: 0.10
Nodes (8): PasswordPolicyService, SecurityPolicyResponse, PlatformSettingValueRepository, RealmSecuritySyncService, RealmSyncResult, SecurityPolicy, SecurityPolicyService, RegI06PasswordPolicyTest

### Community 101 - "org.springframework.stereotype.Component"
Cohesion: 0.07
Nodes (4): PrintedPasswordPolicy, StudentLoginLookup, AwsSecretsManagerTenantSecretsResolver, SecretsManagerProperties

### Community 102 - "PlatformSupportService"
Cohesion: 0.12
Nodes (5): SupportTicketResponse, PlatformSupportService, SupportAccessGrantRepository, SupportTicketRepository, PlatformSupportControllerTest

### Community 103 - "InstitutionId"
Cohesion: 0.17
Nodes (5): CreateDepartmentRequest, CreateProgramRequest, InstitutionId, ProgramRepository, StructureControllerTest

### Community 107 - "StudentGuardianLink"
Cohesion: 0.22
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

### Community 114 - "RoleDefinition"
Cohesion: 0.10
Nodes (21): CustomScope, INSTITUTION, UNIVERSITY_OFFICE, RoleDefinitionResponse, UpdateCustomRoleRequest, RoleDefinitionStatus, ACTIVE, ARCHIVED (+13 more)

### Community 119 - ".Github Workflows"
Cohesion: 0.33
Nodes (6): run, defaults, name, runs-on, frontend, working-directory

### Community 120 - ".Github Workflows"
Cohesion: 0.33
Nodes (6): on, pull_request, push, workflow_dispatch, branches, branches

### Community 121 - "ScopeType"
Cohesion: 0.18
Nodes (10): CreateUserCheckRequest, UpdateUserRequest, ScopeType, DEPARTMENT, INSTITUTION, TENANT, SwitchActiveRoleResponse, UserMeResponse (+2 more)

### Community 122 - "UserBulkUploadService.java"
Cohesion: 0.09
Nodes (4): ImportJobRepository, UserBulkUploadService, UserBulkUploadControllerTest, NoNullPrincipalArchitectureTest

### Community 123 - "Core Utilities"
Cohesion: 0.40
Nodes (4): printWidth, proseWrap, semi, trailingComma

### Community 124 - "Utils"
Cohesion: 0.70
Nodes (3): formErrorMapping, normalizeFormErrors(), splitFullName()

### Community 126 - "TemporaryPasswordResponseAdvice.java"
Cohesion: 0.16
Nodes (3): TemporaryPasswordResponseAdvice, PasswordResetResponse, TemporaryPasswordResponseAdviceTest

### Community 127 - ".decideApproval"
Cohesion: 0.39
Nodes (3): ApprovalInboxController, ApprovalSummaryDto, DecideApprovalResponseDto

### Community 132 - ".principal"
Cohesion: 0.37
Nodes (3): StudentLoginSwitchRequest, StudentLoginSwitchService, StudentLoginSwitchServiceTest

### Community 139 - "scripts"
Cohesion: 0.20
Nodes (10): scripts, build, dev, format, format:check, lint, preview, test (+2 more)

### Community 145 - "ModuleEntitlementEnforcer.java"
Cohesion: 0.15
Nodes (3): ModuleEntitlementEnforcer, ModuleEntitlementService, TenantEntitlementRepository

### Community 148 - ".createDelegation"
Cohesion: 0.39
Nodes (3): ApprovalDelegationController, BulkApprovalRequest, DelegationResponse

### Community 171 - "hooks/index.ts"
Cohesion: 0.43
Nodes (3): useClickOutside(), useDebounce(), useUrlFilters()

### Community 173 - "Tenant"
Cohesion: 0.32
Nodes (5): TenantCreateRequest, IsolationMode, ISOLATED, SHARED, Tenant

### Community 174 - "Institution"
Cohesion: 0.31
Nodes (3): InstitutionPageResponse, InstitutionResponse, Institution

### Community 175 - "PlatformUserRepository"
Cohesion: 0.21
Nodes (3): DevAccount, DevAdminSeeder, PlatformUserRepository

### Community 176 - "RbacTestWorld"
Cohesion: 0.12
Nodes (5): ActionRow, RbacTestWorld, StandardRoleRow, RoleCodesA06Test, UserCreateKeycloakA07Test

### Community 177 - ".assignMentor"
Cohesion: 0.34
Nodes (3): MentorAssignRequest, MentorAssignmentRepository, MentorAssignmentServiceTest

### Community 179 - "TenantState"
Cohesion: 0.25
Nodes (7): TenantState, ACTIVE, ARCHIVED, CREATED, DELETED, SUSPENDED, TRIAL

### Community 185 - "EDU 360 API"
Cohesion: 0.29
Nodes (6): Build and test, Configuration profiles, Current architecture status, EDU 360 API, Requirements, Run locally

### Community 189 - "SubjectAllocationService"
Cohesion: 0.18
Nodes (3): SubjectAllocationResponse, SubjectAllocationService, OfferingDepartmentResolver

### Community 190 - ".createUserImport"
Cohesion: 0.33
Nodes (3): ImportJobResponse, ImportTemplateResponse, UserBulkUploadController

### Community 191 - "UserAccount"
Cohesion: 0.33
Nodes (3): UserAccount, PlatformUser, CreateUserResponse

### Community 194 - "ApprovalInboxResponseDto"
Cohesion: 0.40
Nodes (3): ApprovalInboxResponseDto, PageInfoDto, ApprovalStepDto

### Community 197 - "ApprovalRouting"
Cohesion: 0.18
Nodes (7): ApprovalRouting, Route, Via, DELEGATION, ROLE, ROLLDOWN, USER

### Community 206 - "UsernameKind"
Cohesion: 0.50
Nodes (4): UsernameKind, EMAIL, MOBILE, STUDENT_CODE

### Community 233 - "theme.ts"
Cohesion: 0.32
Nodes (9): ThemeToggle(), AuthLayout(), applyTheme(), getStoredTheme(), initTheme(), setStoredTheme(), systemTheme(), Theme (+1 more)

## Knowledge Gaps
- **132 isolated node(s):** `INSTITUTION`, `UNIVERSITY_OFFICE`, `ACTIVE`, `ARCHIVED`, `DRAFT` (+127 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 783 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **117 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AuditEventType` connect `Audit` to `list`, `SelfServiceScopeA08Test.java`, `InstitutionSetupActionTest.java`, `PermissionEvaluatorService`, `AuditLogServiceTest`, `Institution Structure & Hierarchy`, `org.junit.jupiter.api.BeforeEach`, `AuditLog.java`, `lombok.RequiredArgsConstructor`, `RegB3SignInTest.java`, `org.springframework.stereotype.Service`, `UserBulkUploadService.java`?**
  _High betweenness centrality (0.025) - this node is a cross-community bridge._
- **What connects `INSTITUTION`, `UNIVERSITY_OFFICE`, `ACTIVE` to the rest of the system?**
  _132 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `userManagementService.ts` be split into smaller, more focused modules?**
  _Cohesion score 0.046249125961442414 - nodes in this community are weakly interconnected._
- **Why does `PermissionEvaluatorService` connect `PermissionEvaluatorService` to `list`, `RbacActionTest`, `.principal`, `org.junit.jupiter.api.DisplayName`, `Institution Structure & Hierarchy`, `AuthServiceTest`, `.decideApproval`, `org.junit.jupiter.api.BeforeEach`, `ModuleEntitlementEnforcer.java`, `RegB3SignInTest.java`, `lombok.RequiredArgsConstructor`, `org.springframework.stereotype.Service`, `.lockTerm`, `.institution`, `org.junit.jupiter.api.DisplayName`, `.can`, `StructureService`, `RoleDefinition`, `InstitutionSetupActionTest.java`, `org.springframework.security.core.Authentication`, `UserPasswordResetUnlockController`, `UserPasswordResetUnlockUnlockActionTest`, `RbacTestWorld`, `.assignMentor`, `.institution`, `SelfServiceScopeA08Test`, `MasterCalendarControllerTest`, `.grantAccess`, `UserViewActionTest`, `.provisionUser`, `RbacPrincipal`, `SubjectAllocationService`, `RoleAssignmentControllerTest`, `ApprovalRouting`, `.assignClassTeacher`, `UserRoleAssignment`, `RoleActionRepository`, `.assignMentor`, `InstitutionSetupActionTest`, `InstitutionId`, `RBAC & Permission Evaluator`, `UserBulkUploadService.java`?**
  _High betweenness centrality (0.018) - this node is a cross-community bridge._
- **Should `Institution Structure & Hierarchy` be split into smaller, more focused modules?**
  _Cohesion score 0.09739442946990116 - nodes in this community are weakly interconnected._
- **Why does `TenantRepository` connect `org.springframework.stereotype.Service` to `list`, `org.springframework.transaction.annotation.Transactional`, `org.junit.jupiter.api.DisplayName`, `PermissionEvaluatorService`, `org.junit.jupiter.api.BeforeEach`, `Institution Structure & Hierarchy`, `AuthServiceTest`, `org.springframework.security.oauth2.jwt.Jwt`, `ModuleEntitlementEnforcer.java`, `RegB3SignInTest.java`, `lombok.RequiredArgsConstructor`, `.institution`, `org.junit.jupiter.api.DisplayName`, `.can`, `Platform Operations & Governance`, `Application Configuration & Security`, `RoleDefinition`, `UserPasswordResetUnlockUnlockActionTest`, `UserPasswordResetUnlockController`, `Application Configuration & Security`, `TenantState`, `.provisionUser`, `PlatformDataProtectionService`, `SessionService`, `Application Configuration & Security`, `.usageCounts`, `PlatformPermissionService`, `SessionServiceTest`, `SecurityPolicyService`?**
  _High betweenness centrality (0.016) - this node is a cross-community bridge._
- **Should `list` be split into smaller, more focused modules?**
  _Cohesion score 0.05290322580645161 - nodes in this community are weakly interconnected._
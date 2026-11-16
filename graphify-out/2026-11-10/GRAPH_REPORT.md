# Graph Report - Personal project  (2026-11-10)

## Corpus Check
- 897 files · ~433,541 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 22 file(s) not represented in the graph (top: .properties 7, .css 7, (none) 5)

## Summary
- 5522 nodes · 26038 edges · 220 communities (105 shown, 115 thin omitted)
- Extraction: 87% EXTRACTED · 13% INFERRED · 0% AMBIGUOUS · INFERRED: 3309 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `263cc49a`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- userManagementService.ts
- Institution Structure & Hierarchy
- list
- org.springframework.transaction.annotation.Transactional
- TenantState
- Services
- Institution Structure & Hierarchy
- Self-Service & Role Switching
- org.junit.jupiter.api.DisplayName
- Platform Operations & Governance
- RbacActionTest
- SessionControllerTest
- Institution Structure & Hierarchy
- AuthServiceTest
- ApprovalInboxService
- org.springframework.security.oauth2.jwt.Jwt
- .signIn
- PlatformRole
- AuthService
- lombok.RequiredArgsConstructor
- RbacTestWorld
- PermissionEvaluatorService
- InstitutionSetupActionTest
- .institution
- Audit
- AuthService
- design-system/components/index.ts
- TenantsView.tsx
- org.junit.jupiter.api.DisplayName
- RoleDashboardPage.tsx
- UserRoleAssignmentId
- .can
- ResourceNotFoundException
- .setStudentLogin
- Platform Operations & Governance
- StructureService
- BackupStatusControllerTest
- Application Configuration & Security
- RoleDefinition
- notblank
- org.springframework.security.core.Authentication
- InstitutionAdminPages.tsx
- Application Configuration & Security
- ProfileService
- Multi-Tenant Routing & Lifecycle
- PlatformAccessLookup
- DevRoleAssignmentSeeder.java
- Multi-Tenant Routing & Lifecycle
- KeycloakAdminClient
- SelfServiceController.java
- PlatformPermissionServiceTest
- Institution Structure & Hierarchy
- TenantIssuerAuthenticationManagerResolverIT.java
- Platform Operations & Governance
- AccessMode
- .grantAccess
- SessionsPage.tsx
- .provisionUser
- .institution
- SelfServiceService
- PlatformPrincipal
- Core Utilities
- .updatePlatformUser
- StoredPlatformAccessLookup
- RegB3SignInTest
- jakarta.servlet.http.HttpServletRequest
- .authorize
- Core Utilities
- .createRelease
- RoleActionRepository
- PlatformFeatureToggleController
- EDU 360 CI/CD Engineering Standard
- RoleDefinitionId
- SessionService
- Application Configuration & Security
- .as
- .assignMentor
- package.json
- Authentication & Identity Management
- KeycloakProperties
- Platform Operations & Governance
- SubjectAllocationService
- PlatformUserManagementService
- PlatformAccessLookup
- KeycloakRealmSecurityService
- .setRule
- PlatformReportController.java
- Docker Localstack
- .Github Workflows
- RegM03SecurityChainTest
- notnull
- SessionServiceTest
- Docker Postgres
- .Github Workflows
- .Github Workflows
- .Github Workflows
- PasswordPolicyService
- org.junit.jupiter.api.AfterEach
- PlatformSecurityPolicyController
- AwsSecretsManagerTenantSecretsResolver.java
- PlatformSupportService
- .listSessions
- UserBulkUploadControllerTest
- .Github Workflows
- React + TypeScript + Vite
- SessionActivityInterceptorTest
- .Github Workflows
- Core Utilities
- .usageCounts
- RoleDefinition
- RBAC & Permission Evaluator
- TenantRoleTemplateAction
- RBAC & Permission Evaluator
- RolldownService
- .Github Workflows
- .Github Workflows
- SubjectAllocation
- RbacActionTest.java
- Core Utilities
- Utils
- Delegation
- TemporaryPasswordResponseAdvice.java
- .decideApproval
- TenantRoutingDataSource.java
- ImportRowError
- Platform Operations & Governance
- components/README.md
- DeletionRequest
- ExportRequest
- SessionActivityInterceptor
- ScopeType
- RoleAssignmentService
- PlatformAuditOutboxEvent
- AuditLogServiceTest
- .handleModuleNotEnabled
- SupportAccessGrant
- Core Utilities
- Database Schema & Migrations
- TemporaryPasswordResponseAdviceTest
- ModuleEntitlementEnforcer.java
- .createDelegation
- ProgramRuleSet
- Core Utilities
- Core Utilities
- UserPasswordResetUnlockUnlockActionTest
- .doFilterInternal
- UserMeController
- ReferenceCodeArchitectureTest
- hooks/index.ts
- ActiveProfileValidatorInitializer.java
- .handleNotAvailable
- Institution
- IntegrationType.java
- UserCreateKeycloakA07Test
- MutableClock
- AuditLog.java
- LoginTenantResolver.java
- StudentGuardianLink
- UsernameKind
- ImportJob
- RetentionChangeRequest
- AuditFailureException
- LoginRequest
- ActiveRoleRequiredException
- PlatformRelease
- PlatformStepIn
- TermLockEvent
- InstitutionBlockResponse.java
- UsageMetricSnapshot.java
- UserPermissionsResponse.java
- TermLock

## God Nodes (most connected - your core abstractions)
1. `PermissionEvaluatorService` - 153 edges
2. `RbacPrincipal` - 151 edges
3. `AuditLogService` - 129 edges
4. `PlatformActionTest` - 127 edges
5. `TenantPrincipal` - 120 edges
6. `AuditEventType` - 110 edges
7. `InstitutionSetupActionTest` - 109 edges
8. `Button()` - 101 edges
9. `react` - 96 edges
10. `KeycloakAdminClient` - 95 edges

## Surprising Connections (you probably didn't know these)
- `Configuration profiles` --references--> `DevAdminSeeder`  [INFERRED]
  api/README.md → api/src/main/java/com/edu360/api/config/DevAdminSeeder.java
- `GradingSchemeController` --references--> `GradingSchemeService`  [EXTRACTED]
  api/src/main/java/com/edu360/api/academic/GradingSchemeController.java → api/src/main/java/com/edu360/api/academic/GradingSchemeService.java
- `GradingSchemeService` --references--> `AuditLogService`  [EXTRACTED]
  api/src/main/java/com/edu360/api/academic/GradingSchemeService.java → api/src/main/java/com/edu360/api/audit/AuditLogService.java
- `GradingSchemeService` --references--> `PermissionEvaluatorService`  [EXTRACTED]
  api/src/main/java/com/edu360/api/academic/GradingSchemeService.java → api/src/main/java/com/edu360/api/rbac/PermissionEvaluatorService.java
- `InstitutionSetupActionTest` --references--> `GradingSchemeService`  [EXTRACTED]
  api/src/test/java/com/edu360/api/institution/InstitutionSetupActionTest.java → api/src/main/java/com/edu360/api/academic/GradingSchemeService.java

## Import Cycles
- None detected.

## Communities (220 total, 115 thin omitted)

### Community 0 - "userManagementService.ts"
Cohesion: 0.05
Nodes (98): Badge(), Button(), Modal(), TextField(), InstitutionItem, institutionService, ApproverKind, leaveApproverService (+90 more)

### Community 1 - "Institution Structure & Hierarchy"
Cohesion: 0.08
Nodes (33): PromotionRun, StudentProfile, AcademicSession, ClassTeacherAssignment, CourseOffering, Department, MentorAssignment, Program (+25 more)

### Community 2 - "list"
Cohesion: 0.05
Nodes (15): ApprovalBatchRepository, ApprovalRequestRepository, GuardianProfileRepository, StudentGuardianLinkRepository, StudentProfileRepository, ImportRowErrorRepository, PlanRepository, PlatformReleaseRepository (+7 more)

### Community 3 - "org.springframework.transaction.annotation.Transactional"
Cohesion: 0.07
Nodes (5): GradingSchemeResponse, ProgramRuleService, PromotionRunResponse, PromotionService, SupportAccessGrantResponse

### Community 4 - "TenantState"
Cohesion: 0.25
Nodes (7): TenantState, ACTIVE, ARCHIVED, CREATED, DELETED, SUSPENDED, TRIAL

### Community 5 - "Services"
Cohesion: 0.05
Nodes (58): Edu360Logo(), Edu360LogoProps, LogoStyle, ChangePasswordPage(), LoginPage(), mockedLogin, renderPage(), ACTIVE_ROLE (+50 more)

### Community 6 - "Institution Structure & Hierarchy"
Cohesion: 0.03
Nodes (91): App(), RoleSwitcher(), RoleSwitcherProps, mockRoles, EmptyState(), academicService, GradingSchemeItem, ProgramRuleSetItem (+83 more)

### Community 7 - "Self-Service & Role Switching"
Cohesion: 0.08
Nodes (37): GradingSchemeVersion, GradingSchemeVersionId, ApprovalBatch, ApprovalBatchId, ApprovalRequestId, ApprovalStepId, DelegationId, RefreshRequest (+29 more)

### Community 8 - "org.junit.jupiter.api.DisplayName"
Cohesion: 0.06
Nodes (5): CreateInvoiceRequest, UpdateTenantSettingsRequest, EntitlementsUpdateRequest, UpdateSupportTicketRequest, PlatformActionTest

### Community 9 - "Platform Operations & Governance"
Cohesion: 0.05
Nodes (126): PageHeader(), Badge(), Spinner(), ConfirmDialog(), DataTable(), Kicker(), MetricCard(), Notice() (+118 more)

### Community 10 - "RbacActionTest"
Cohesion: 0.11
Nodes (3): CreateCustomRoleRequest, RolldownMapEntryDto, RbacActionTest

### Community 12 - "Institution Structure & Hierarchy"
Cohesion: 0.11
Nodes (4): AuditLogService, CreateUserCheckResponse, RbacTarget, SessionResponse

### Community 13 - "AuthServiceTest"
Cohesion: 0.12
Nodes (11): CredentialInfo, IdentityLinkStatus, ACTIVE, REVOKED, ResolvedUser, LoginTenantResolver, PlatformLogin, Resolution (+3 more)

### Community 14 - "ApprovalInboxService"
Cohesion: 0.06
Nodes (15): ApprovalDelegationService, ApprovalInboxService, ApprovalRouting, Route, Via, DELEGATION, ROLE, ROLLDOWN (+7 more)

### Community 16 - ".signIn"
Cohesion: 0.12
Nodes (5): CreateDelegationRequest, ActiveRoleA02Test, RoleCodesA06Test, RoleReadA04Test, ScopeA05Test

### Community 17 - "PlatformRole"
Cohesion: 0.05
Nodes (33): AuthTokenResponse, AuthUserResponse, TopUserResponse, UserAccountStatus, ACTIVE, DEACTIVATED, PENDING_ACTIVATION, UserType (+25 more)

### Community 18 - "AuthService"
Cohesion: 0.08
Nodes (11): AccountState, AuthService, RoleInfo, AccountSetupRequiredException, KeycloakAuthClient, PasswordCheck, INVALID, VERIFIED (+3 more)

### Community 19 - "lombok.RequiredArgsConstructor"
Cohesion: 0.11
Nodes (19): BulkApproveItem, BulkApproveRequestDto, SessionSettingsController, UnauthorizedException, TopUserController, CalendarEventController, DepartmentController, ClassTeacherAssignResponse (+11 more)

### Community 20 - "RbacTestWorld"
Cohesion: 0.13
Nodes (5): UpdateRoleActionSetRequest, CustomRoleA03Test, ActionRow, RbacTestWorld, StandardRoleRow

### Community 21 - "PermissionEvaluatorService"
Cohesion: 0.04
Nodes (24): MfaRequirementService, TenantUserLookup, TopUserService, UserAccountRepository, UserAccountService, UserIdentityLinkRepository, ClassTeacherAssignmentService, HodAssignmentService (+16 more)

### Community 22 - "InstitutionSetupActionTest"
Cohesion: 0.05
Nodes (12): ChangeStudentStatusRequest, CreatePromotionRunRequest, CreateSubjectAllocationRequest, PromotionDecisionRequest, PromotionRunId, PromotionRunRepository, LockTermRequest, TermLockResponse (+4 more)

### Community 23 - ".institution"
Cohesion: 0.07
Nodes (11): InstitutionScopeRequest, UpdateUserRequest, ActiveResolution, RbacPrincipal, UserRequestSupport, UserRoleAssignment, PermissionEvaluatorServiceTest, UserDeactivationActionTest (+3 more)

### Community 24 - "Audit"
Cohesion: 0.03
Nodes (68): AuditEventType, ACADEMIC_SESSION_CREATED, ACADEMIC_SESSION_UPDATED, ACCOUNT_CREATED, APPROVAL, APPROVAL_BULK_DECIDED, APPROVAL_DELEGATED, APPROVAL_DELEGATION_CANCELLED (+60 more)

### Community 25 - "AuthService"
Cohesion: 0.09
Nodes (6): AuthController, ChangePasswordRequest, IdentityEventRequest, IdentityEventTokenValidator, AuthControllerTest, RegM03Test

### Community 26 - "design-system/components/index.ts"
Cohesion: 0.05
Nodes (43): EmptyState(), ErrorState(), Button(), ButtonProps, ButtonSize, ButtonVariant, sizes, variants (+35 more)

### Community 27 - "TenantsView.tsx"
Cohesion: 0.07
Nodes (35): PlatformAuditLogItem, CreatePlanPayload, ExportRequestDto, IsolationMode, PlanItem, platformTenantService, RetentionPolicyDto, TenantCreateRequest (+27 more)

### Community 28 - "org.junit.jupiter.api.DisplayName"
Cohesion: 0.10
Nodes (7): BlockDecisionRequest, BlockInstitutionRequest, CreateInstitutionRequest, InstitutionController, InstitutionRepository, InstitutionService, InstitutionControllerTest

### Community 29 - "RoleDashboardPage.tsx"
Cohesion: 0.08
Nodes (46): Icon(), IconName, paths, ThemeToggle(), EmptyStateProps, Kpi, Metric, MetricCardProps (+38 more)

### Community 30 - "UserRoleAssignmentId"
Cohesion: 0.18
Nodes (4): UserRoleAssignmentId, SwitchActiveRoleRequest, UserGovernanceGuardTest, UserMeControllerTest

### Community 31 - ".can"
Cohesion: 0.15
Nodes (6): LeaveApproverSetRequest, LeaveApproverSettingResponse, InstitutionId, LeaveApproverSettingRepository, PermissionDecision, LeaveApproverControllerTest

### Community 32 - "ResourceNotFoundException"
Cohesion: 0.15
Nodes (5): ReleaseResponse, ReleaseTransitionRequest, UpdateReleaseRequest, PlatformReleaseController, PlatformReleaseService

### Community 33 - ".setStudentLogin"
Cohesion: 0.28
Nodes (3): StudentLoginSwitchRequest, StudentLoginSwitchResponse, StudentLoginSwitchServiceTest

### Community 34 - "Platform Operations & Governance"
Cohesion: 0.11
Nodes (5): CreatePlanRequest, TenantActivationRequest, TenantLifecycleActionRequest, UpdatePlanRequest, PlatformTenantControllerTest

### Community 35 - "StructureService"
Cohesion: 0.07
Nodes (12): CourseOfferingRepository, DepartmentRepository, CreateDepartmentRequest, CreateProgramRequest, ProgramRepository, ProgramYearRepository, SchoolClassRepository, SectionRepository (+4 more)

### Community 36 - "BackupStatusControllerTest"
Cohesion: 0.27
Nodes (5): BackupStatusController, BackupStatusEntryResponse, BackupStatusResponse, TenantBackupStatusRepository, BackupStatusControllerTest

### Community 37 - "Application Configuration & Security"
Cohesion: 0.09
Nodes (6): JpaRepositoryConfig, RestClientConfig, OpenApiConfig, PlatformJpaConfig, DataSourceConfig, TenantContextFilterConfig

### Community 38 - "RoleDefinition"
Cohesion: 0.17
Nodes (4): DevRoleAssignmentSeeder, RoleDefinitionRepository, StandardRoleRepository, DevRoleAssignmentSeederTest

### Community 39 - "notblank"
Cohesion: 0.10
Nodes (4): CreateGradingSchemeRequest, LinkGuardianRequest, UpdateAcademicSessionRequest, CreateRolldownOverrideRequest

### Community 40 - "org.springframework.security.core.Authentication"
Cohesion: 0.04
Nodes (38): GradingSchemeController, ProgramRuleController, ProgramRuleSetResponse, PromotionController, StudentStatusController, SubjectAllocationController, SessionSettingsResponse, GuardianLinkController (+30 more)

### Community 41 - "InstitutionAdminPages.tsx"
Cohesion: 0.07
Nodes (23): GoLiveBlockerItem, GoLiveCheckResponse, LeaveApproverSetPayload, LeaveApproverSettingItem, AssignmentStatus, AssignRoleRequest, roleAssignmentService, RoleDefinitionItem (+15 more)

### Community 43 - "ProfileService"
Cohesion: 0.08
Nodes (11): TenantPrincipal, FileObjectRepository, LocalFileStorage, UncheckedFileStorageException, UserAccountId, ProfileUpdateRequest, ProfileController, ProfileService (+3 more)

### Community 45 - "Multi-Tenant Routing & Lifecycle"
Cohesion: 0.10
Nodes (5): CachedPool, TenantRoutingDataSource, TenantSecretsResolver, TenantContextTest, TenantRoutingDataSourceTest

### Community 47 - "DevRoleAssignmentSeeder.java"
Cohesion: 0.18
Nodes (9): UserAccount, TenantCreateRequest, RoleAction, StandardRole, StandardRoleAction, IsolationMode, ISOLATED, SHARED (+1 more)

### Community 50 - "SelfServiceController.java"
Cohesion: 0.33
Nodes (5): LeaveBalanceDto, LeaveRequestDto, NoticeDto, NotificationDto, SelfServiceController

### Community 51 - "PlatformPermissionServiceTest"
Cohesion: 0.07
Nodes (6): CreateProgramRuleSetRequest, AcademicControllerTest, PlatformPermissionServiceTest, StubLookup, PlatformSecurityReportControllerTest, RegM01Test

### Community 52 - "Institution Structure & Hierarchy"
Cohesion: 0.12
Nodes (10): AcademicCalendarEvent, AcademicCalendarEventRepository, AcademicSessionRepository, CalendarEventService, CalendarEventRequest, CalendarEventResponse, CreateAcademicSessionRequest, CreateMasterCalendarEventRequest (+2 more)

### Community 53 - "TenantIssuerAuthenticationManagerResolverIT.java"
Cohesion: 0.05
Nodes (8): PrintedPasswordPolicy, ActiveRoleRequiredFilter, CorrelationIdFilter, MustChangePasswordFilter, PathNormalizationFilter, TrialReadOnlyFilter, TenantContextClearingFilter, NoNullPrincipalArchitectureTest

### Community 54 - "Platform Operations & Governance"
Cohesion: 0.20
Nodes (6): CreateIntegrationTypeRequest, IntegrationTypeResponse, UpdateIntegrationTypeRequest, IntegrationTypeController, IntegrationTypeRepository, IntegrationTypeControllerTest

### Community 55 - "AccessMode"
Cohesion: 0.40
Nodes (3): AccessMode, HANDED_OVER, SHARED

### Community 56 - ".grantAccess"
Cohesion: 0.17
Nodes (4): CreateSupportTicketRequest, GrantSupportAccessRequest, PlatformSupportControllerTest, TenantSupportControllerTest

### Community 57 - "SessionsPage.tsx"
Cohesion: 0.07
Nodes (32): PlatformRole, ProfileResponse, profileService, ProfileUpdateRequest, UserType, initialsFor(), ProfileAvatarSection(), ProfileAvatarSectionProps (+24 more)

### Community 58 - ".provisionUser"
Cohesion: 0.19
Nodes (4): TopUserCreateRequest, CreateUserRequest, TopUserControllerTest, UserCreationControllerTest

### Community 59 - ".institution"
Cohesion: 0.11
Nodes (7): StudentStatusResponse, RowValidationError, UserBulkUploadService, AcademicSessionResponse, MasterCalendarEventResponse, MasterCalendarService, RbacScope

### Community 61 - "PlatformPrincipal"
Cohesion: 0.05
Nodes (27): PlatformPrincipal, BusinessRuleViolationException, DuplicateResourceException, ResourceNotFoundException, PlatformUserRepository, CreatePlatformRoleRequest, PlatformActionCatalogItem, PlatformRoleResponse (+19 more)

### Community 62 - "Core Utilities"
Cohesion: 0.09
Nodes (21): compilerOptions, allowImportingTsExtensions, erasableSyntaxOnly, jsx, lib, module, moduleDetection, moduleResolution (+13 more)

### Community 63 - ".updatePlatformUser"
Cohesion: 0.08
Nodes (12): Build and test, Configuration profiles, Current architecture status, EDU 360 API, Requirements, Run locally, DevAccount, DevAdminSeeder (+4 more)

### Community 67 - ".authorize"
Cohesion: 0.05
Nodes (21): InvoiceResponse, RecordPaymentRequest, PlatformBillingController, PlatformBillingService, CreateDeletionRequestDto, CreateRetentionChangeRequest, DataProtectionDtos, DecideDeletionRequestDto (+13 more)

### Community 68 - "Core Utilities"
Cohesion: 0.10
Nodes (19): compilerOptions, allowImportingTsExtensions, erasableSyntaxOnly, lib, module, moduleDetection, moduleResolution, noEmit (+11 more)

### Community 70 - "RoleActionRepository"
Cohesion: 0.13
Nodes (5): ActionCatalogRepository, RoleActionRepository, Caller, RoleCustomService, RoleCustomControllerTest

### Community 71 - "PlatformFeatureToggleController"
Cohesion: 0.21
Nodes (5): ModuleEntitlementService, PlanEntitlementRepository, EntitlementsResponse, PlatformFeatureToggleController, TenantEntitlementRepository

### Community 72 - "EDU 360 CI/CD Engineering Standard"
Cohesion: 0.18
Nodes (10): 1. Purpose and delivery model, 2. Pipeline stages, 3. PostgreSQL migration validation, 4. Pull-request and branch protection, 5. Artifact and release integrity, 6. Deployment and operations — intentionally not enabled, 7. Follow-up acceptance checklist, EDU 360 CI/CD Engineering Standard (+2 more)

### Community 73 - "RoleDefinitionId"
Cohesion: 0.24
Nodes (3): AssignRoleRequest, RoleDefinitionId, RoleAssignmentControllerTest

### Community 76 - ".as"
Cohesion: 0.06
Nodes (10): ApprovalRequest, DecideApprovalRequestDto, GuardianChangeDecisionRequest, GuardianChangeRequestDto, ApprovalDelegationControllerTest, ApprovalEngineB2Test, ApprovalInboxControllerTest, ApprovalTestWorld (+2 more)

### Community 77 - ".assignMentor"
Cohesion: 0.12
Nodes (10): ClassTeacherAssignmentRepository, ClassTeacherAssignRequest, HodAssignRequest, HodAssignResponse, MentorAssignRequest, MentorAssignmentRepository, StaffAssignmentRepository, ClassTeacherAssignmentServiceTest (+2 more)

### Community 78 - "package.json"
Cohesion: 0.04
Nodes (72): dependencies, axios@^1.12.2, clsx@^2.1.1, date-fns@^4.4.0, react@^19.2.0, react-dom@^19.2.0, react-redux@^9.2.0, react-router-dom@^7.18.5 (+64 more)

### Community 79 - "Authentication & Identity Management"
Cohesion: 0.13
Nodes (14): KC_BOOTSTRAP_ADMIN_PASSWORD, KC_BOOTSTRAP_ADMIN_USERNAME, KC_SPI_THEME_CACHE_TEMPLATES, KC_SPI_THEME_CACHE_THEMES, KC_SPI_THEME_STATIC_MAX_AGE, command, environment, image (+6 more)

### Community 81 - "Platform Operations & Governance"
Cohesion: 0.29
Nodes (3): AuditLogPageResponse, PlatformAuditOutboxEventRepository, PlatformAuditLogControllerTest

### Community 82 - "SubjectAllocationService"
Cohesion: 0.12
Nodes (6): GradingSchemeService, GradingSchemeVersionRepository, StudentStatusService, SubjectAllocationResponse, SubjectAllocationService, OfferingDepartmentResolver

### Community 83 - "PlatformUserManagementService"
Cohesion: 0.26
Nodes (4): PlatformUserResponse, PlatformUserController, AssignedRole, PlatformUserManagementService

### Community 84 - "PlatformAccessLookup"
Cohesion: 0.21
Nodes (4): StepInRequest, StepInResponse, PlatformStepInService, PlatformStepInServiceTest

### Community 86 - ".setRule"
Cohesion: 0.25
Nodes (5): WeeklyOffRuleRequest, WeeklyOffRuleResponse, WeeklyOffRuleRepository, WeeklyOffRuleService, WeeklyOffRuleServiceTest

### Community 87 - "PlatformReportController.java"
Cohesion: 0.20
Nodes (3): InvoiceRepository, PlatformReportController, PlatformReportResponse

### Community 88 - "Docker Localstack"
Cohesion: 0.15
Nodes (12): AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, DEFAULT_REGION, SERVICES, environment, image, ports, volumes (+4 more)

### Community 89 - ".Github Workflows"
Cohesion: 0.26
Nodes (13): steps, steps, steps, GITHUB_TOKEN, PGPASSWORD, PSQL, steps, steps (+5 more)

### Community 90 - "RegM03SecurityChainTest"
Cohesion: 0.13
Nodes (4): DeviceDescriptor, ApiApplicationTests, RegM03SecurityChainTest, DeviceDescriptorTest

### Community 92 - "SessionServiceTest"
Cohesion: 0.20
Nodes (4): PlatformUserDeviceRepository, SignInContext, UserDeviceRepository, SessionServiceTest

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

### Community 97 - "PasswordPolicyService"
Cohesion: 0.19
Nodes (3): PasswordPolicyService, SecurityPolicy, RegI06PasswordPolicyTest

### Community 100 - "PlatformSecurityPolicyController"
Cohesion: 0.17
Nodes (9): CreateSecurityIncidentRequest, PlatformSecurityPolicyController, SecurityIncidentResponse, SecurityPolicyResponse, UpdateSecurityIncidentRequest, UpdateSecurityPolicyRequest, PlatformSettingValueRepository, RealmSyncResult (+1 more)

### Community 101 - "AwsSecretsManagerTenantSecretsResolver.java"
Cohesion: 0.12
Nodes (3): AwsSecretsManagerTenantSecretsResolver, SecretsManagerProperties, TenantDatabaseCredentials

### Community 102 - "PlatformSupportService"
Cohesion: 0.20
Nodes (3): SupportTicketResponse, PlatformSupportController, PlatformSupportService

### Community 106 - "UserBulkUploadControllerTest"
Cohesion: 0.20
Nodes (3): StartImportRequest, UserImportRequest, UserBulkUploadControllerTest

### Community 108 - ".Github Workflows"
Cohesion: 0.25
Nodes (8): if, name, permissions, runs-on, dependency-review, permissions, contents, pull-requests

### Community 109 - "React + TypeScript + Vite"
Cohesion: 0.50
Nodes (3): Expanding the ESLint configuration, React Compiler, React + TypeScript + Vite

### Community 111 - ".Github Workflows"
Cohesion: 0.29
Nodes (7): name, permissions, runs-on, steps, codeql, actions, security-events

### Community 113 - ".usageCounts"
Cohesion: 0.27
Nodes (4): UsageCountsResponse, UsageCountsController, UsageMetricDailyRepository, UsageCountsControllerTest

### Community 114 - "RoleDefinition"
Cohesion: 0.10
Nodes (22): CustomScope, INSTITUTION, UNIVERSITY_OFFICE, RoleDefinitionResponse, UpdateCustomRoleRequest, RoleDefinition, RoleDefinitionStatus, ACTIVE (+14 more)

### Community 118 - "RolldownService"
Cohesion: 0.18
Nodes (5): RoleRolldownDefaultRepository, RoleRolldownOverrideRepository, RolldownOverrideDto, RolldownService, RolldownControllerTest

### Community 119 - ".Github Workflows"
Cohesion: 0.33
Nodes (6): run, defaults, name, runs-on, frontend, working-directory

### Community 120 - ".Github Workflows"
Cohesion: 0.33
Nodes (6): on, pull_request, push, workflow_dispatch, branches, branches

### Community 121 - "SubjectAllocation"
Cohesion: 0.39
Nodes (3): SubjectAllocation, SubjectAllocationId, SubjectAllocationRepository

### Community 122 - "RbacActionTest.java"
Cohesion: 0.05
Nodes (4): BulkApprovalResponse, SelfApprovalForbiddenException, DashboardPreferencesRequest, TenantContext

### Community 123 - "Core Utilities"
Cohesion: 0.40
Nodes (4): printWidth, proseWrap, semi, trailingComma

### Community 124 - "Utils"
Cohesion: 0.70
Nodes (3): formErrorMapping, normalizeFormErrors(), splitFullName()

### Community 127 - ".decideApproval"
Cohesion: 0.20
Nodes (7): ApprovalInboxController, ApprovalInboxResponseDto, PageInfoDto, ApprovalStepDto, ApprovalSummaryDto, SubjectRef, DecideApprovalResponseDto

### Community 135 - "ScopeType"
Cohesion: 0.19
Nodes (10): AssignmentStatus, ACTIVE, ENDED, CreateUserCheckRequest, UserRoleAssignmentResponse, ScopeType, DEPARTMENT, INSTITUTION (+2 more)

### Community 140 - "SupportAccessGrant"
Cohesion: 0.22
Nodes (4): SupportAccessGrant, SupportAccessGrantRepository, SupportTicket, SupportTicketRepository

### Community 147 - ".createDelegation"
Cohesion: 0.39
Nodes (3): ApprovalDelegationController, BulkApprovalRequest, DelegationResponse

### Community 148 - "ProgramRuleSet"
Cohesion: 0.39
Nodes (3): ProgramRuleSet, ProgramRuleSetId, ProgramRuleSetRepository

### Community 171 - "hooks/index.ts"
Cohesion: 0.43
Nodes (3): useClickOutside(), useDebounce(), useUrlFilters()

### Community 174 - "Institution"
Cohesion: 0.40
Nodes (3): InstitutionPageResponse, InstitutionResponse, Institution

### Community 181 - "UsernameKind"
Cohesion: 0.50
Nodes (4): UsernameKind, EMAIL, MOBILE, STUDENT_CODE

## Knowledge Gaps
- **125 isolated node(s):** `ROLE`, `USER`, `DELEGATION`, `ROLLDOWN`, `LOGIN_SUCCESS` (+120 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 760 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **115 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `DevAdminSeeder` connect `.updatePlatformUser` to `Application Configuration & Security`, `KeycloakProperties`, `KeycloakAdminClient`, `lombok.RequiredArgsConstructor`, `TenantIssuerAuthenticationManagerResolverIT.java`, `PermissionEvaluatorService`, `PlatformPrincipal`?**
  _High betweenness centrality (0.022) - this node is a cross-community bridge._
- **What connects `ROLE`, `USER`, `DELEGATION` to the rest of the system?**
  _125 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `userManagementService.ts` be split into smaller, more focused modules?**
  _Cohesion score 0.04661114966458478 - nodes in this community are weakly interconnected._
- **Why does `KeycloakProperties` connect `KeycloakProperties` to `PasswordPolicyService`, `AwsSecretsManagerTenantSecretsResolver.java`, `SessionService`, `Application Configuration & Security`, `Institution Structure & Hierarchy`, `AuthServiceTest`, `org.springframework.security.oauth2.jwt.Jwt`, `KeycloakAdminClient`, `AuthService`, `PlatformUserManagementService`, `LoginTenantResolver.java`, `TenantIssuerAuthenticationManagerResolverIT.java`, `KeycloakRealmSecurityService`, `PermissionEvaluatorService`, `AuthService`, `RbacActionTest.java`, `.updatePlatformUser`?**
  _High betweenness centrality (0.019) - this node is a cross-community bridge._
- **Should `Institution Structure & Hierarchy` be split into smaller, more focused modules?**
  _Cohesion score 0.08324388789505069 - nodes in this community are weakly interconnected._
- **Why does `PermissionEvaluatorService` connect `PermissionEvaluatorService` to `list`, `org.springframework.transaction.annotation.Transactional`, `RoleAssignmentService`, `org.junit.jupiter.api.DisplayName`, `RbacActionTest`, `Institution Structure & Hierarchy`, `ApprovalInboxService`, `.signIn`, `ModuleEntitlementEnforcer.java`, `lombok.RequiredArgsConstructor`, `RbacTestWorld`, `InstitutionSetupActionTest`, `.institution`, `org.junit.jupiter.api.DisplayName`, `.can`, `.setStudentLogin`, `StructureService`, `RoleDefinition`, `UserPasswordResetUnlockUnlockActionTest`, `org.springframework.security.core.Authentication`, `ProfileService`, `PlatformPermissionServiceTest`, `Institution Structure & Hierarchy`, `.grantAccess`, `.provisionUser`, `.institution`, `SelfServiceService`, `PlatformPrincipal`, `RoleActionRepository`, `RoleDefinitionId`, `.assignMentor`, `SubjectAllocationService`, `.setRule`, `InstitutionSetupActionTest.java`, `UserBulkUploadControllerTest`, `RBAC & Permission Evaluator`, `RolldownService`, `RbacActionTest.java`?**
  _High betweenness centrality (0.016) - this node is a cross-community bridge._
- **Should `list` be split into smaller, more focused modules?**
  _Cohesion score 0.050666666666666665 - nodes in this community are weakly interconnected._
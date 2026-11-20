# Graph Report - Personal project  (2026-11-08)

## Corpus Check
- 815 files · ~380,131 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 21 file(s) not represented in the graph (top: .properties 7, .css 7, (none) 5)

## Summary
- 4423 nodes · 22415 edges · 154 communities (92 shown, 62 thin omitted)
- Extraction: 91% EXTRACTED · 9% INFERRED · 0% AMBIGUOUS · INFERRED: 2108 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `263cc49a`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Academic & Curriculum Management
- Institution Structure & Hierarchy
- org.springframework.data.jpa.repository.JpaRepository
- org.springframework.transaction.annotation.Transactional
- AuditLogService
- Services
- Institution Structure & Hierarchy
- Self-Service & Role Switching
- Platform Operations & Governance
- Platform Operations & Governance
- RBAC & Permission Evaluator
- SessionService
- Institution Structure & Hierarchy
- AuthServiceTest
- Institution Structure & Hierarchy
- org.springframework.http.ResponseEntity
- Frontend Design System UI
- UserBulkUploadService
- AuthActionTest.java
- Institution Structure & Hierarchy
- org.springframework.http.ResponseEntity
- PermissionEvaluatorService
- org.junit.jupiter.api.Test
- RBAC & Permission Evaluator
- Audit
- .resetPassword
- RBAC & Permission Evaluator
- TenantsView.tsx
- InstitutionId
- org.springframework.web.bind.annotation.GetMapping
- RoleDefinitionId
- ApprovalInboxControllerTest
- ReleaseResponse
- AuthService
- Platform Operations & Governance
- Institution Structure & Hierarchy
- PlatformPermissionService
- Application Configuration & Security
- Application Configuration & Security
- uuid
- org.springframework.security.core.Authentication
- InstitutionAdminPages.tsx
- Application Configuration & Security
- Self-Service & Role Switching
- TenantManagementService.java
- Multi-Tenant Routing & Lifecycle
- ApprovalInboxController
- Multi-Tenant Routing & Lifecycle
- Multi-Tenant Routing & Lifecycle
- Authentication & Identity Management
- Institution Structure & Hierarchy
- PlatformPermissionServiceTest
- Institution Structure & Hierarchy
- Exception
- Platform Operations & Governance
- PlatformRoleResponse
- .grantAccess
- Application Configuration & Security
- Authentication & Identity Management
- org.springframework.stereotype.Repository
- SelfServiceService
- Platform Operations & Governance
- Core Utilities
- .updatePlatformUser
- .linkGuardian
- Test Imports
- .applyDiscount
- PlatformDataProtectionController
- Core Utilities
- .createRelease
- dependencies
- AuthController.java
- EDU 360 CI/CD Engineering Standard
- OpenApiConfig.java
- TenantIssuerAuthenticationManagerResolver
- Application Configuration & Security
- eslint.config.js
- PlatformAuditWriter
- devDependencies
- Authentication & Identity Management
- TenantIssuerAuthenticationManagerResolverIT.java
- Platform Operations & Governance
- KeycloakProperties
- RBAC & Permission Evaluator
- PlatformAccessLookup
- DevAdminSeeder.java
- package.json
- SupportAccessGrantRepository
- Docker Localstack
- .Github Workflows
- .setRule
- InstitutionController
- Docker Postgres
- .Github Workflows
- .Github Workflows
- .Github Workflows
- Platform Operations & Governance
- TermLockControllerTest
- PlatformSecurityPolicyController.java
- Core Utilities
- UserBulkUploadController
- instant
- PlatformUserControllerTest
- Self-Service & Role Switching
- Termlock
- .Github Workflows
- React + TypeScript + Vite
- Hooks
- .Github Workflows
- Core Utilities
- UsageCountsController.java
- AuditLogServiceTest
- RBAC & Permission Evaluator
- PromotionRunId
- RBAC & Permission Evaluator
- Section
- .Github Workflows
- .Github Workflows
- RoleDetailResponse
- .record
- Core Utilities
- Utils
- ProgramRuleSet
- PlatformDataProtectionService
- GradingSchemeController
- SubjectAllocationRepository
- BulkApproveRequestDto
- Platform Operations & Governance
- components/README.md
- Test
- GuardianLinkController
- WeeklyOffRuleController
- PlanEntitlementRepository
- TenantEntitlementRepository
- vite.config.ts
- tailwindcss
- list
- Core Utilities
- Database Schema & Migrations
- Core Utilities
- Core Utilities

## God Nodes (most connected - your core abstractions)
1. `TenantPrincipal` - 201 edges
2. `RbacPrincipal` - 190 edges
3. `UnauthorizedException` - 148 edges
4. `Button()` - 136 edges
5. `AuditLogService` - 134 edges
6. `PlatformPrincipal` - 130 edges
7. `PlatformActionTest` - 126 edges
8. `PermissionEvaluatorService` - 120 edges
9. `BusinessRuleViolationException` - 118 edges
10. `ResourceNotFoundException` - 110 edges

## Surprising Connections (you probably didn't know these)
- `Configuration profiles` --references--> `DevAdminSeeder`  [INFERRED]
  api/README.md → api/src/main/java/com/edu360/api/config/DevAdminSeeder.java
- `renderWithStore()` --calls--> `ProfilePage()`  [EXTRACTED]
  app/src/features/profile/pages/ProfilePage.test.tsx → app/src/features/profile/pages/ProfilePage.tsx
- `TermLockEvent` --references--> `TermLockEventId`  [EXTRACTED]
  api/src/main/java/com/edu360/api/termlock/TermLockEvent.java → api/src/main/java/com/edu360/api/termlock/TermLockEventId.java
- `TermLockEventRepository` --references--> `TermLockEvent`  [EXTRACTED]
  api/src/main/java/com/edu360/api/termlock/TermLockEventRepository.java → api/src/main/java/com/edu360/api/termlock/TermLockEvent.java
- `TermLockController` --references--> `TermLockService`  [EXTRACTED]
  api/src/main/java/com/edu360/api/termlock/TermLockController.java → api/src/main/java/com/edu360/api/termlock/TermLockService.java

## Import Cycles
- None detected.

## Communities (154 total, 62 thin omitted)

### Community 0 - "Academic & Curriculum Management"
Cohesion: 0.05
Nodes (105): Button(), Modal(), Notice(), TextField(), academicService, InstitutionItem, ApproverKind, AddAcademicSessionModal() (+97 more)

### Community 1 - "Institution Structure & Hierarchy"
Cohesion: 0.09
Nodes (44): GradingSchemeVersion, SubjectAllocation, ApprovalBatch, ApprovalRequest, AuditLog, StudentGuardianLink, StudentProfile, AcademicSession (+36 more)

### Community 2 - "org.springframework.data.jpa.repository.JpaRepository"
Cohesion: 0.09
Nodes (6): GradingSchemeVersionRepository, ImportJobRepository, PlatformReleaseRepository, PlatformSettingValueRepository, UserActiveRoleRepository, TermLockRepository

### Community 3 - "org.springframework.transaction.annotation.Transactional"
Cohesion: 0.05
Nodes (14): GradingSchemeResponse, ProgramRuleSetResponse, PromotionRunResponse, PromotionService, StudentStatusResponse, SubjectAllocationResponse, GuardianLinkResponse, SupportAccessGrantResponse (+6 more)

### Community 4 - "AuditLogService"
Cohesion: 0.06
Nodes (52): AuditLogService, KeycloakAdminClient, TenantUserLookup, TopUserService, UserAccountRepository, UserAccountService, UserIdentityLinkRepository, GoLiveBlockerItem (+44 more)

### Community 5 - "Services"
Cohesion: 0.04
Nodes (72): Edu360Logo(), LogoStyle, mockRoles, LinkButton(), PasswordField(), ChangePasswordPage(), LoginPage(), mockedApi (+64 more)

### Community 6 - "Institution Structure & Hierarchy"
Cohesion: 0.07
Nodes (72): App(), RoleSwitcher(), Badge(), EmptyState(), GradingSchemeItem, ProgramRuleSetItem, PromotionRunItem, StudentStatusItem (+64 more)

### Community 7 - "Self-Service & Role Switching"
Cohesion: 0.08
Nodes (25): GradingSchemeVersionId, SubjectAllocationId, ApprovalBatchId, ApprovalRequestId, ApprovalStep, ApprovalStepId, DelegationId, FileObject (+17 more)

### Community 8 - "Platform Operations & Governance"
Cohesion: 0.06
Nodes (9): CreateInvoiceRequest, UpdateTenantSettingsRequest, EntitlementsUpdateRequest, UpdateSecurityPolicyRequest, CreateSupportTicketRequest, UpdateSupportTicketRequest, PlatformActionTest, PlatformSecurityReportControllerTest (+1 more)

### Community 9 - "Platform Operations & Governance"
Cohesion: 0.06
Nodes (100): PageHeader(), Spinner(), ConfirmDialog(), DataTable(), MetricCard(), Panel(), SearchBox(), StatusBadge() (+92 more)

### Community 10 - "RBAC & Permission Evaluator"
Cohesion: 0.06
Nodes (17): ApprovalBatchRepository, ApprovalDelegationController, ApprovalDelegationService, DelegationRepository, BulkApprovalRequest, CreateDelegationRequest, DelegationResponse, GuardianChangeDecisionRequest (+9 more)

### Community 11 - "SessionService"
Cohesion: 0.15
Nodes (4): SessionResponse, SessionService, SessionControllerTest, SessionServiceTest

### Community 12 - "Institution Structure & Hierarchy"
Cohesion: 0.15
Nodes (10): PlatformPrincipal, DuplicateResourceException, PlatformRole, BILLING_MANAGER, DATA_PROTECTION_OFFICER, PLATFORM_ADMIN, PRODUCT_OWNER, RELEASE_MANAGER (+2 more)

### Community 13 - "AuthServiceTest"
Cohesion: 0.17
Nodes (10): TokenResult, StudentProfileRepository, ResolvedUser, UserAccountId, LoginTenantResolver, PlatformLogin, Resolution, TenantLogin (+2 more)

### Community 14 - "Institution Structure & Hierarchy"
Cohesion: 0.17
Nodes (9): ClassTeacherAssignmentRepository, ClassTeacherAssignmentService, DepartmentRepository, ClassTeacherAssignRequest, HodAssignRequest, HodAssignmentService, StaffAssignmentRepository, ClassTeacherAssignmentServiceTest (+1 more)

### Community 15 - "org.springframework.http.ResponseEntity"
Cohesion: 0.12
Nodes (15): CourseOfferingResponse, CreateCourseOfferingRequest, CreateProgramYearRequest, CreateSchoolClassRequest, CreateSectionRequest, CreateSemesterRequest, CreateSubjectRequest, ProgramYearResponse (+7 more)

### Community 16 - "Frontend Design System UI"
Cohesion: 0.04
Nodes (53): react-dom@^19.2.0, EmptyState(), ErrorState(), Button(), ButtonProps, ButtonSize, ButtonVariant, sizes (+45 more)

### Community 17 - "UserBulkUploadService"
Cohesion: 0.17
Nodes (3): StartImportRequest, RowValidationError, UserBulkUploadService

### Community 18 - "AuthActionTest.java"
Cohesion: 0.07
Nodes (27): AuthTokenResponse, AuthUserResponse, TopUserResponse, IdentityLinkStatus, ACTIVE, REVOKED, UserAccountStatus, ACTIVE (+19 more)

### Community 19 - "Institution Structure & Hierarchy"
Cohesion: 0.14
Nodes (10): UnauthorizedException, TopUserController, DepartmentController, SchoolClassController, SectionController, PlatformAuditLogController, UpdatePlatformRoleRequest, CreateUserCheckRequest (+2 more)

### Community 20 - "org.springframework.http.ResponseEntity"
Cohesion: 0.11
Nodes (6): ProgramRuleController, PromotionController, MyPlatformPermissionsResponse, PlatformActionCatalogItem, PlatformRoleController, ProfileController

### Community 21 - "PermissionEvaluatorService"
Cohesion: 0.13
Nodes (11): GradingSchemeService, ProgramRuleService, StudentStatusService, SubjectAllocationService, BulkApprovalResponse, BusinessRuleViolationException, ResourceNotFoundException, PermissionEvaluatorService (+3 more)

### Community 22 - "org.junit.jupiter.api.Test"
Cohesion: 0.05
Nodes (11): ChangeStudentStatusRequest, CreateGradingSchemeRequest, CreateProgramRuleSetRequest, CreatePromotionRunRequest, CreateSubjectAllocationRequest, PromotionDecisionRequest, StudentLoginSwitchRequest, StudentLoginSwitchService (+3 more)

### Community 23 - "RBAC & Permission Evaluator"
Cohesion: 0.13
Nodes (8): ActionCatalog, ActionCatalogRepository, RbacPrincipal, PermissionEvaluatorServiceTest, UserDeactivationActionTest, UserEditActionTest, UserPasswordResetUnlockActionTest, UserViewActionTest

### Community 24 - "Audit"
Cohesion: 0.03
Nodes (59): AuditEventType, ACADEMIC_SESSION_CREATED, ACADEMIC_SESSION_UPDATED, ACCOUNT_CREATED, APPROVAL, APPROVAL_BULK_DECIDED, APPROVAL_DELEGATED, APPROVAL_DELEGATION_CANCELLED (+51 more)

### Community 26 - "RBAC & Permission Evaluator"
Cohesion: 0.08
Nodes (49): Icon(), IconName, paths, ThemeToggle(), EmptyStateProps, Kpi, Metric, MetricCardProps (+41 more)

### Community 27 - "TenantsView.tsx"
Cohesion: 0.07
Nodes (40): Kicker(), CreatePlanPayload, ExportRequestDto, IsolationMode, PlanItem, platformTenantService, RetentionPolicyDto, TenantCreateRequest (+32 more)

### Community 28 - "InstitutionId"
Cohesion: 0.07
Nodes (14): BlockDecisionRequest, BlockInstitutionRequest, CreateDepartmentRequest, CreateInstitutionRequest, CreateProgramRequest, DepartmentResponse, InstitutionPageResponse, InstitutionResponse (+6 more)

### Community 29 - "org.springframework.web.bind.annotation.GetMapping"
Cohesion: 0.14
Nodes (8): LeaveApproverController, MasterCalendarController, RoleAssignmentController, LeaveBalanceDto, LeaveRequestDto, NoticeDto, NotificationDto, SelfServiceController

### Community 30 - "RoleDefinitionId"
Cohesion: 0.08
Nodes (9): AssignRoleRequest, UserRoleAssignmentResponse, RoleCustomService, RoleDefinitionId, UserRoleAssignmentId, SwitchActiveRoleRequest, RoleAssignmentControllerTest, UserGovernanceGuardTest (+1 more)

### Community 31 - "ApprovalInboxControllerTest"
Cohesion: 0.13
Nodes (6): ApprovalInboxResponseDto, PageInfoDto, ApprovalSummaryDto, SubjectRef, DecideApprovalRequestDto, ApprovalInboxControllerTest

### Community 32 - "ReleaseResponse"
Cohesion: 0.23
Nodes (5): ReleaseResponse, ReleaseTransitionRequest, UpdateReleaseRequest, PlatformReleaseController, PlatformReleaseService

### Community 33 - "AuthService"
Cohesion: 0.18
Nodes (4): AuthService, FailedAttemptTracker, RoleInfo, InvalidCredentialsException

### Community 34 - "Platform Operations & Governance"
Cohesion: 0.16
Nodes (5): CreatePlanRequest, TenantActivationRequest, TenantLifecycleActionRequest, UpdatePlanRequest, PlatformTenantControllerTest

### Community 35 - "Institution Structure & Hierarchy"
Cohesion: 0.07
Nodes (13): ProgramRuleSetRepository, CourseOfferingRepository, MentorAssignRequest, MentorAssignmentRepository, MentorAssignmentService, ProgramRepository, ProgramYearRepository, SchoolClassRepository (+5 more)

### Community 36 - "PlatformPermissionService"
Cohesion: 0.09
Nodes (13): BackupStatusController, BackupStatusResponse, PlatformPermissionService, PlatformReportController, PlatformReportResponse, StepInRequest, StepInResponse, PlatformStepInService (+5 more)

### Community 37 - "Application Configuration & Security"
Cohesion: 0.12
Nodes (5): JpaRepositoryConfig, RestClientConfig, PlatformJpaConfig, DataSourceConfig, TenantContextFilterConfig

### Community 38 - "Application Configuration & Security"
Cohesion: 0.17
Nodes (3): DevRoleAssignmentSeeder, StandardRoleRepository, DevRoleAssignmentSeederTest

### Community 39 - "uuid"
Cohesion: 0.04
Nodes (10): ClassTeacherAssignResponse, HodAssignResponse, MentorAssignResponse, StudentLoginSwitchResponse, UpdateAcademicSessionRequest, Plan, PlatformUserCreateRequest, TenantCreateRequest (+2 more)

### Community 40 - "org.springframework.security.core.Authentication"
Cohesion: 0.11
Nodes (12): StudentStatusController, SubjectAllocationController, CalendarEventController, InvoiceResponse, PlatformBillingController, PlanResponse, TenantPageResponse, TenantResponse (+4 more)

### Community 41 - "InstitutionAdminPages.tsx"
Cohesion: 0.07
Nodes (42): Badge(), PageHeader(), AcademicSessionItem, CreateAcademicSessionPayload, CreateDepartmentPayload, CreateInstitutionRequest, CreateMasterCalendarEventPayload, CreateProgramPayload (+34 more)

### Community 43 - "Self-Service & Role Switching"
Cohesion: 0.09
Nodes (11): TenantPrincipal, FileObjectRepository, LocalFileStorage, UncheckedFileStorageException, UserAccount, ProfileResponse, ProfileUpdateRequest, ProfileService (+3 more)

### Community 45 - "Multi-Tenant Routing & Lifecycle"
Cohesion: 0.12
Nodes (5): TenantDatabaseCredentials, CachedPool, TenantRoutingDataSource, TenantSecretsResolver, TenantRoutingDataSourceTest

### Community 50 - "Institution Structure & Hierarchy"
Cohesion: 0.15
Nodes (5): LeaveApproverSetRequest, LeaveApproverSettingResponse, LeaveApproverSettingRepository, PermissionDecision, LeaveApproverControllerTest

### Community 52 - "Institution Structure & Hierarchy"
Cohesion: 0.10
Nodes (13): AcademicCalendarEvent, AcademicCalendarEventRepository, AcademicSessionRepository, CalendarEventService, AcademicSessionResponse, CalendarEventRequest, CalendarEventResponse, CreateAcademicSessionRequest (+5 more)

### Community 54 - "Platform Operations & Governance"
Cohesion: 0.25
Nodes (6): CreateIntegrationTypeRequest, IntegrationTypeResponse, UpdateIntegrationTypeRequest, IntegrationTypeController, IntegrationTypeRepository, IntegrationTypeControllerTest

### Community 55 - "PlatformRoleResponse"
Cohesion: 0.07
Nodes (17): AccessMode, HANDED_OVER, SHARED, CreatePlatformRoleRequest, PlatformRoleResponse, RoleActionItem, PlatformCustomRoleActionRepository, PlatformCustomRoleRepository (+9 more)

### Community 57 - "Application Configuration & Security"
Cohesion: 0.15
Nodes (4): CorrelationContext, CorrelationIdFilter, MustChangePasswordFilter, TenantContextClearingFilter

### Community 58 - "Authentication & Identity Management"
Cohesion: 0.23
Nodes (3): TopUserCreateRequest, CreateUserRequest, TopUserControllerTest

### Community 59 - "org.springframework.stereotype.Repository"
Cohesion: 0.10
Nodes (11): PromotionRunRepository, ApprovalInboxService, ApprovalRequestRepository, ApprovalStepRepository, GuardianLinkService, GuardianProfileRepository, StudentGuardianLinkRepository, ImportRowErrorRepository (+3 more)

### Community 60 - "SelfServiceService"
Cohesion: 0.18
Nodes (6): NoticeRepository, SelfServiceService, UserLeaveBalanceRepository, UserLeaveRequestRepository, UserNotificationRepository, SelfServiceControllerTest

### Community 61 - "Platform Operations & Governance"
Cohesion: 0.15
Nodes (6): PlatformRoleTemplateResponse, SavePlatformRoleTemplateRequest, PlatformRoleTemplateActionRepository, PlatformRoleTemplateRepository, PlatformRoleTemplateService, PlatformRoleTemplateServiceTest

### Community 62 - "Core Utilities"
Cohesion: 0.09
Nodes (21): compilerOptions, allowImportingTsExtensions, erasableSyntaxOnly, jsx, lib, module, moduleDetection, moduleResolution (+13 more)

### Community 63 - ".updatePlatformUser"
Cohesion: 0.20
Nodes (3): KeycloakProvisioningException, PlatformUserUpdateRequest, PlatformUserUpdateTest

### Community 67 - "PlatformDataProtectionController"
Cohesion: 0.21
Nodes (7): CreateRetentionChangeRequest, DataProtectionDtos, DeletionRequestResponse, ExportRequestResponse, RetentionChangeRequestResponse, RetentionPolicyResponse, PlatformDataProtectionController

### Community 68 - "Core Utilities"
Cohesion: 0.10
Nodes (19): compilerOptions, allowImportingTsExtensions, erasableSyntaxOnly, lib, module, moduleDetection, moduleResolution, noEmit (+11 more)

### Community 70 - "dependencies"
Cohesion: 0.14
Nodes (14): dependencies, axios@^1.12.2, clsx@^2.1.1, date-fns@^4.4.0, react@^19.2.0, react-redux@^9.2.0, react-router-dom@^7.18.5, redux-persist@^6.0.0 (+6 more)

### Community 71 - "AuthController.java"
Cohesion: 0.21
Nodes (4): AuthController, IdentityEventRequest, LoginRequest, RefreshRequest

### Community 72 - "EDU 360 CI/CD Engineering Standard"
Cohesion: 0.18
Nodes (10): 1. Purpose and delivery model, 2. Pipeline stages, 3. PostgreSQL migration validation, 4. Pull-request and branch protection, 5. Artifact and release integrity, 6. Deployment and operations — intentionally not enabled, 7. Follow-up acceptance checklist, EDU 360 CI/CD Engineering Standard (+2 more)

### Community 76 - "eslint.config.js"
Cohesion: 0.15
Nodes (12): eslint@^9.39.1, @eslint/js@^9.39.1, eslint-plugin-react-hooks@^7.0.1, eslint-plugin-react-refresh@^0.4.24, globals@^16.5.0, typescript-eslint@^8.46.4, eslint, @eslint/js (+4 more)

### Community 78 - "devDependencies"
Cohesion: 0.12
Nodes (17): devDependencies, jsdom@^27.0.0, prettier@^3.9.10, @testing-library/jest-dom@^6.6.3, @testing-library/react@^16.0.1, @types/node@^24.10.1, @types/react-dom@^19.2.3, typescript@~5.9.3 (+9 more)

### Community 79 - "Authentication & Identity Management"
Cohesion: 0.13
Nodes (14): KC_BOOTSTRAP_ADMIN_PASSWORD, KC_BOOTSTRAP_ADMIN_USERNAME, KC_SPI_THEME_CACHE_TEMPLATES, KC_SPI_THEME_CACHE_THEMES, KC_SPI_THEME_STATIC_MAX_AGE, command, environment, image (+6 more)

### Community 80 - "TenantIssuerAuthenticationManagerResolverIT.java"
Cohesion: 0.05
Nodes (19): PlatformUser, PlatformUserRepository, PlatformUserStatus, ACTIVE, INACTIVE, PlatformUserResponse, AssignedRole, PlatformUserManagementService (+11 more)

### Community 82 - "KeycloakProperties"
Cohesion: 0.07
Nodes (4): KeycloakAuthClient, KeycloakProperties, AwsSecretsManagerTenantSecretsResolver, SecretsManagerProperties

### Community 83 - "RBAC & Permission Evaluator"
Cohesion: 0.25
Nodes (5): UpdateUserRequest, UserListResponse, UserSummaryResponse, UserRequestSupport, UserViewController

### Community 85 - "DevAdminSeeder.java"
Cohesion: 0.14
Nodes (8): Build and test, Configuration profiles, Current architecture status, EDU 360 API, Requirements, Run locally, DevAccount, DevAdminSeeder

### Community 86 - "package.json"
Cohesion: 0.15
Nodes (12): autoprefixer@^10.4.21, postcss@^8.5.0, @testing-library/user-event@^14.6.0, @types/react@^19.2.5, name, private, type, version (+4 more)

### Community 88 - "Docker Localstack"
Cohesion: 0.15
Nodes (12): AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, DEFAULT_REGION, SERVICES, environment, image, ports, volumes (+4 more)

### Community 89 - ".Github Workflows"
Cohesion: 0.26
Nodes (13): steps, steps, steps, GITHUB_TOKEN, PGPASSWORD, PSQL, steps, steps (+5 more)

### Community 90 - ".setRule"
Cohesion: 0.31
Nodes (5): WeeklyOffRuleRequest, WeeklyOffRuleResponse, WeeklyOffRuleRepository, WeeklyOffRuleService, WeeklyOffRuleServiceTest

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

### Community 98 - "TermLockControllerTest"
Cohesion: 0.36
Nodes (3): LockTermRequest, UnlockTermRequest, TermLockControllerTest

### Community 100 - "PlatformSecurityPolicyController.java"
Cohesion: 0.17
Nodes (7): EntitlementsResponse, PlatformFeatureToggleController, CreateSecurityIncidentRequest, PlatformSecurityPolicyController, SecurityIncidentResponse, SecurityPolicyResponse, UpdateSecurityIncidentRequest

### Community 101 - "Core Utilities"
Cohesion: 0.20
Nodes (10): scripts, build, dev, format, format:check, lint, preview, test (+2 more)

### Community 102 - "UserBulkUploadController"
Cohesion: 0.31
Nodes (3): ImportJobResponse, ImportTemplateResponse, UserBulkUploadController

### Community 103 - "instant"
Cohesion: 0.05
Nodes (14): Delegation, ApprovalStepDto, GuardianProfile, ImportRowErrorResponse, ImportJob, ImportRowError, Institution, LeaveApproverSetting (+6 more)

### Community 108 - ".Github Workflows"
Cohesion: 0.25
Nodes (8): if, name, permissions, runs-on, dependency-review, permissions, contents, pull-requests

### Community 109 - "React + TypeScript + Vite"
Cohesion: 0.50
Nodes (3): Expanding the ESLint configuration, React Compiler, React + TypeScript + Vite

### Community 110 - "Hooks"
Cohesion: 0.43
Nodes (3): useClickOutside(), useDebounce(), useUrlFilters()

### Community 111 - ".Github Workflows"
Cohesion: 0.29
Nodes (7): name, permissions, runs-on, steps, codeql, actions, security-events

### Community 113 - "UsageCountsController.java"
Cohesion: 0.27
Nodes (5): UsageCountsResponse, UsageMetricSnapshot, UsageCountsController, UsageMetricDailyRepository, UsageCountsControllerTest

### Community 116 - "PromotionRunId"
Cohesion: 0.20
Nodes (4): PromotionRun, PromotionRunId, TenantRoleTemplateAction, TenantRoleTemplateActionRepository

### Community 118 - "Section"
Cohesion: 0.50
Nodes (4): Program, ProgramYear, Section, Semester

### Community 119 - ".Github Workflows"
Cohesion: 0.33
Nodes (6): run, defaults, name, runs-on, frontend, working-directory

### Community 120 - ".Github Workflows"
Cohesion: 0.33
Nodes (6): on, pull_request, push, workflow_dispatch, branches, branches

### Community 121 - "RoleDetailResponse"
Cohesion: 0.50
Nodes (3): RoleDetailResponse, UpdateRoleActionSetRequest, RoleCustomController

### Community 123 - "Core Utilities"
Cohesion: 0.40
Nodes (4): printWidth, proseWrap, semi, trailingComma

### Community 124 - "Utils"
Cohesion: 0.70
Nodes (3): formErrorMapping, normalizeFormErrors(), splitFullName()

### Community 126 - "PlatformDataProtectionService"
Cohesion: 0.15
Nodes (5): DeletionRequestRepository, ExportRequestRepository, PlatformDataProtectionService, RetentionChangeRequestRepository, TenantRetentionPolicyRepository

## Knowledge Gaps
- **112 isolated node(s):** `INSTITUTION`, `UNIVERSITY_OFFICE`, `BILLING_MANAGER`, `DATA_PROTECTION_OFFICER`, `PLATFORM_ADMIN` (+107 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 601 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **62 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `TenantPrincipal` connect `Self-Service & Role Switching` to `org.springframework.transaction.annotation.Transactional`, `AuditLogService`, `GuardianLinkController`, `Platform Operations & Governance`, `RBAC & Permission Evaluator`, `SessionService`, `Institution Structure & Hierarchy`, `AuthServiceTest`, `org.springframework.http.ResponseEntity`, `AuthActionTest.java`, `Institution Structure & Hierarchy`, `org.springframework.http.ResponseEntity`, `PermissionEvaluatorService`, `org.junit.jupiter.api.Test`, `RBAC & Permission Evaluator`, `InstitutionId`, `org.springframework.web.bind.annotation.GetMapping`, `RoleDefinitionId`, `ApprovalInboxControllerTest`, `AuthService`, `uuid`, `org.springframework.security.core.Authentication`, `ApprovalInboxController`, `Authentication & Identity Management`, `Institution Structure & Hierarchy`, `Institution Structure & Hierarchy`, `.grantAccess`, `Authentication & Identity Management`, `.linkGuardian`, `AuthController.java`, `TenantIssuerAuthenticationManagerResolver`, `TenantIssuerAuthenticationManagerResolverIT.java`, `RBAC & Permission Evaluator`, `TermLockControllerTest`, `Termlock`, `GradingSchemeController`?**
  _High betweenness centrality (0.018) - this node is a cross-community bridge._
- **What connects `INSTITUTION`, `UNIVERSITY_OFFICE`, `BILLING_MANAGER` to the rest of the system?**
  _112 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Academic & Curriculum Management` be split into smaller, more focused modules?**
  _Cohesion score 0.04741641337386018 - nodes in this community are weakly interconnected._
- **Why does `RbacPrincipal` connect `RBAC & Permission Evaluator` to `org.springframework.transaction.annotation.Transactional`, `AuditLogService`, `WeeklyOffRuleController`, `Institution Structure & Hierarchy`, `Institution Structure & Hierarchy`, `org.springframework.http.ResponseEntity`, `UserBulkUploadService`, `Institution Structure & Hierarchy`, `PermissionEvaluatorService`, `org.junit.jupiter.api.Test`, `InstitutionId`, `org.springframework.web.bind.annotation.GetMapping`, `RoleDefinitionId`, `Institution Structure & Hierarchy`, `Application Configuration & Security`, `uuid`, `org.springframework.security.core.Authentication`, `Institution Structure & Hierarchy`, `Institution Structure & Hierarchy`, `.grantAccess`, `Authentication & Identity Management`, `SelfServiceService`, `RBAC & Permission Evaluator`, `.setRule`, `InstitutionController`, `UserBulkUploadController`, `RoleDetailResponse`?**
  _High betweenness centrality (0.014) - this node is a cross-community bridge._
- **Should `Institution Structure & Hierarchy` be split into smaller, more focused modules?**
  _Cohesion score 0.09341736694677871 - nodes in this community are weakly interconnected._
- **Why does `AuditLogService` connect `AuditLogService` to `org.springframework.transaction.annotation.Transactional`, `Platform Operations & Governance`, `RBAC & Permission Evaluator`, `SessionService`, `Institution Structure & Hierarchy`, `AuthServiceTest`, `org.springframework.http.ResponseEntity`, `UserBulkUploadService`, `AuthActionTest.java`, `Institution Structure & Hierarchy`, `PermissionEvaluatorService`, `org.junit.jupiter.api.Test`, `RBAC & Permission Evaluator`, `InstitutionId`, `RoleDefinitionId`, `ApprovalInboxControllerTest`, `ReleaseResponse`, `AuthService`, `Platform Operations & Governance`, `Institution Structure & Hierarchy`, `uuid`, `Self-Service & Role Switching`, `Authentication & Identity Management`, `Institution Structure & Hierarchy`, `Institution Structure & Hierarchy`, `Authentication & Identity Management`, `org.springframework.stereotype.Repository`, `.linkGuardian`, `Test Imports`, `.applyDiscount`, `.createRelease`, `AuthController.java`, `PlatformAuditWriter`, `SupportAccessGrantRepository`, `TermLockControllerTest`, `AuditLogServiceTest`, `PlatformDataProtectionService`?**
  _High betweenness centrality (0.011) - this node is a cross-community bridge._
- **Should `org.springframework.data.jpa.repository.JpaRepository` be split into smaller, more focused modules?**
  _Cohesion score 0.08748615725359911 - nodes in this community are weakly interconnected._
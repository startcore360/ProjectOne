# Graph Report - Personal project  (2026-11-07)

## Corpus Check
- Large corpus: 817 files · ~376,966 words. Semantic extraction will be expensive (many Claude tokens). Consider running on a subfolder.

## Summary
- 4311 nodes · 22445 edges · 160 communities (89 shown, 71 thin omitted)
- Extraction: 91% EXTRACTED · 9% INFERRED · 0% AMBIGUOUS · INFERRED: 2090 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- Academic & Curriculum Management
- Institution Structure & Hierarchy
- Self-Service & Role Switching
- Institution Structure & Hierarchy
- RBAC & Permission Evaluator
- Services
- Institution Structure & Hierarchy
- Self-Service & Role Switching
- Platform Operations & Governance
- Platform Operations & Governance
- RBAC & Permission Evaluator
- Authentication & Identity Management
- Institution Structure & Hierarchy
- Authentication & Identity Management
- Institution Structure & Hierarchy
- Institution Structure & Hierarchy
- Frontend Design System UI
- RBAC & Permission Evaluator
- Institution Structure & Hierarchy
- Platform Operations & Governance
- Institution Structure & Hierarchy
- Institution Structure & Hierarchy
- RBAC & Permission Evaluator
- Audit
- Platform Operations & Governance
- RBAC & Permission Evaluator
- Platform Operations & Governance
- Institution Structure & Hierarchy
- Institution Structure & Hierarchy
- RBAC & Permission Evaluator
- Approval
- Platform Operations & Governance
- Institution Structure & Hierarchy
- Platform Operations & Governance
- Institution Structure & Hierarchy
- Platform Operations & Governance
- Application Configuration & Security
- Application Configuration & Security
- Institution Structure & Hierarchy
- Platform Operations & Governance
- Platform Operations & Governance
- Application Configuration & Security
- Self-Service & Role Switching
- Imports
- Multi-Tenant Routing & Lifecycle
- Authentication & Identity Management
- Multi-Tenant Routing & Lifecycle
- Multi-Tenant Routing & Lifecycle
- Authentication & Identity Management
- Institution Structure & Hierarchy
- Platform Operations & Governance
- Institution Structure & Hierarchy
- Exception
- Platform Operations & Governance
- Platform Operations & Governance
- Platform Operations & Governance
- Application Configuration & Security
- Authentication & Identity Management
- Platform Operations & Governance
- Platform Operations & Governance
- Platform Operations & Governance
- Core Utilities
- Platform Operations & Governance
- User Profile UI
- Test Imports
- Platform Operations & Governance
- Self-Service & Role Switching
- Core Utilities
- Institution Structure & Hierarchy
- Platform Operations & Governance
- Institution Structure & Hierarchy
- Platform Operations & Governance
- Core Utilities
- Core Utilities
- Application Configuration & Security
- Platform Operations & Governance
- Platform Operations & Governance
- Core Utilities
- Authentication & Identity Management
- Platform Operations & Governance
- Platform Operations & Governance
- Multi-Tenant Routing & Lifecycle
- RBAC & Permission Evaluator
- Self-Service & Role Switching
- Authentication & Identity Management
- RBAC & Permission Evaluator
- Platform Operations & Governance
- Docker Localstack
- .Github Workflows
- Institution Structure & Hierarchy
- RBAC & Permission Evaluator
- Self-Service & Role Switching
- Docker Postgres
- .Github Workflows
- .Github Workflows
- .Github Workflows
- Platform Operations & Governance
- Multi-Tenant Routing & Lifecycle
- Platform Operations & Governance
- Core Utilities
- Lib
- Approval
- Self-Service & Role Switching
- Termlock
- .Github Workflows
- Guardian
- Hooks
- .Github Workflows
- Core Utilities
- Platform Operations & Governance
- Platform Operations & Governance
- RBAC & Permission Evaluator
- RBAC & Permission Evaluator
- RBAC & Permission Evaluator
- Test Audit
- .Github Workflows
- .Github Workflows
- Academic & Curriculum Management
- Platform Operations & Governance
- Core Utilities
- Utils
- Institution Structure & Hierarchy
- Platform Operations & Governance
- Platform Operations & Governance
- Platform Operations & Governance
- Platform Operations & Governance
- Platform Operations & Governance
- Platform Operations & Governance
- Test
- Core Utilities
- Academic & Curriculum Management
- Imports Dto
- Institution Structure & Hierarchy
- Institution Structure & Hierarchy
- Platform Operations & Governance
- Platform Operations & Governance
- Core Utilities
- Core Utilities
- Database Schema & Migrations
- Core Utilities
- Core Utilities
- Core Utilities
- Core Utilities
- Core Utilities
- Core Utilities
- Core Utilities
- Core Utilities

## God Nodes (most connected - your core abstractions)
1. `TenantPrincipal` - 216 edges
2. `RbacPrincipal` - 193 edges
3. `UnauthorizedException` - 165 edges
4. `PlatformPrincipal` - 161 edges
5. `AuditLogService` - 155 edges
6. `BusinessRuleViolationException` - 139 edges
7. `Button()` - 138 edges
8. `ResourceNotFoundException` - 135 edges
9. `PermissionEvaluatorService` - 127 edges
10. `PlatformActionTest` - 126 edges

## Surprising Connections (you probably didn't know these)
- `GradingSchemeController` --references--> `GradingSchemeService`  [EXTRACTED]
  api/src/main/java/com/edu360/api/academic/GradingSchemeController.java → api/src/main/java/com/edu360/api/academic/GradingSchemeService.java
- `AcademicControllerTest` --references--> `GradingSchemeController`  [EXTRACTED]
  api/src/test/java/com/edu360/api/academic/AcademicControllerTest.java → api/src/main/java/com/edu360/api/academic/GradingSchemeController.java
- `GradingSchemeService` --references--> `GradingSchemeVersionRepository`  [EXTRACTED]
  api/src/main/java/com/edu360/api/academic/GradingSchemeService.java → api/src/main/java/com/edu360/api/academic/GradingSchemeVersionRepository.java
- `InstitutionSetupActionTest` --references--> `GradingSchemeService`  [EXTRACTED]
  api/src/test/java/com/edu360/api/institution/InstitutionSetupActionTest.java → api/src/main/java/com/edu360/api/academic/GradingSchemeService.java
- `GradingSchemeVersionRepository` --references--> `GradingSchemeVersion`  [EXTRACTED]
  api/src/main/java/com/edu360/api/academic/GradingSchemeVersionRepository.java → api/src/main/java/com/edu360/api/academic/GradingSchemeVersion.java

## Import Cycles
- None detected.

## Communities (160 total, 71 thin omitted)

### Community 0 - "Academic & Curriculum Management"
Cohesion: 0.05
Nodes (96): Button(), Modal(), Notice(), TextField(), InstitutionItem, ApproverKind, AddAcademicSessionModal(), AddAcademicSessionModalProps (+88 more)

### Community 1 - "Institution Structure & Hierarchy"
Cohesion: 0.08
Nodes (22): AuditLog, StudentProfile, AcademicCalendarEvent, AcademicSession, CourseOffering, Department, MentorAssignment, Program (+14 more)

### Community 2 - "Self-Service & Role Switching"
Cohesion: 0.04
Nodes (30): GradingSchemeVersionRepository, ApprovalBatchRepository, AuditLogRepository, GuardianProfileRepository, ImportJob, ImportJobRepository, ImportRowErrorRepository, PlanEntitlement (+22 more)

### Community 3 - "Institution Structure & Hierarchy"
Cohesion: 0.07
Nodes (17): GradingSchemeResponse, ProgramRuleSetResponse, PromotionRunResponse, StudentStatusResponse, SubjectAllocationResponse, DelegationResponse, BusinessRuleViolationException, AcademicSessionResponse (+9 more)

### Community 4 - "RBAC & Permission Evaluator"
Cohesion: 0.06
Nodes (44): TenantUserLookup, UserAccountRepository, DepartmentRepository, InstitutionRepository, LeaveApproverService, LeaveApproverSettingRepository, PlanRepository, TenantManagementService (+36 more)

### Community 5 - "Services"
Cohesion: 0.04
Nodes (62): mockRoles, EmptyState(), ACTIVE_ROLE, CATALOG, mockedApi, TEMPLATES, meService, SwitchActiveRoleResponse (+54 more)

### Community 6 - "Institution Structure & Hierarchy"
Cohesion: 0.06
Nodes (82): App(), RoleSwitcher(), Badge(), Metric, academicService, GradingSchemeItem, ProgramRuleSetItem, PromotionRunItem (+74 more)

### Community 7 - "Self-Service & Role Switching"
Cohesion: 0.08
Nodes (35): GradingSchemeVersion, GradingSchemeVersionId, ProgramRuleSet, ProgramRuleSetId, SubjectAllocationId, ApprovalBatch, ApprovalBatchId, ApprovalRequest (+27 more)

### Community 8 - "Platform Operations & Governance"
Cohesion: 0.07
Nodes (7): CreateInvoiceRequest, UpdateTenantSettingsRequest, SecurityPolicyResponse, UpdateSecurityPolicyRequest, CreateSupportTicketRequest, UpdateSupportTicketRequest, PlatformActionTest

### Community 9 - "Platform Operations & Governance"
Cohesion: 0.09
Nodes (70): PageHeader(), Spinner(), ConfirmDialog(), DataTable(), MetricCard(), Panel(), SearchBox(), StatusBadge() (+62 more)

### Community 10 - "RBAC & Permission Evaluator"
Cohesion: 0.05
Nodes (15): BulkApprovalRequest, CreateDelegationRequest, GuardianChangeDecisionRequest, GuardianChangeRequestDto, LinkGuardianRequest, CustomScope, INSTITUTION, UNIVERSITY_OFFICE (+7 more)

### Community 11 - "Authentication & Identity Management"
Cohesion: 0.04
Nodes (8): DevAccount, DevAdminSeeder, KeycloakProperties, TenantIssuerAuthenticationManagerResolver, SessionService, SecretsManagerProperties, KeycloakAdminClientTest, SessionServiceTest

### Community 12 - "Institution Structure & Hierarchy"
Cohesion: 0.17
Nodes (3): DuplicateResourceException, UnauthorizedException, SessionResponse

### Community 13 - "Authentication & Identity Management"
Cohesion: 0.09
Nodes (18): AuthService, FailedAttemptTracker, RoleInfo, AuthTokenResponse, AuthUserResponse, KeycloakAuthClient, TokenResult, InvalidCredentialsException (+10 more)

### Community 14 - "Institution Structure & Hierarchy"
Cohesion: 0.08
Nodes (12): ClassTeacherAssignRequest, HodAssignRequest, MentorAssignRequest, MentorAssignmentRepository, Section, SectionRepository, StaffAssignment, StaffAssignmentRepository (+4 more)

### Community 15 - "Institution Structure & Hierarchy"
Cohesion: 0.09
Nodes (6): ProgramRuleController, UserBulkUploadController, StructureController, BackupStatusEntryResponse, BackupStatusResponse, SelfServiceController

### Community 16 - "Frontend Design System UI"
Cohesion: 0.05
Nodes (42): EmptyState(), ErrorState(), Button(), ButtonProps, ButtonSize, ButtonVariant, sizes, variants (+34 more)

### Community 18 - "RBAC & Permission Evaluator"
Cohesion: 0.08
Nodes (22): KeycloakAdminClient, IdentityLinkStatus, ACTIVE, REVOKED, UserIdentityLink, UserIdentityLinkRepository, UsernameKind, EMAIL (+14 more)

### Community 19 - "Institution Structure & Hierarchy"
Cohesion: 0.15
Nodes (24): GradingSchemeController, StudentStatusController, SubjectAllocationController, ApprovalDelegationController, ApprovalInboxController, TenantPrincipal, GuardianLinkController, TopUserController (+16 more)

### Community 20 - "Platform Operations & Governance"
Cohesion: 0.08
Nodes (5): PromotionController, PlatformActionCatalogItem, StepInResponse, UpdatePlatformRoleRequest, PlatformRoleController

### Community 21 - "Institution Structure & Hierarchy"
Cohesion: 0.16
Nodes (19): GradingSchemeService, ProgramRuleService, StudentStatusService, SubjectAllocationService, ApprovalDelegationService, AuditLogService, TopUserService, UserAccountService (+11 more)

### Community 22 - "Institution Structure & Hierarchy"
Cohesion: 0.06
Nodes (11): ChangeStudentStatusRequest, CreateGradingSchemeRequest, CreateProgramRuleSetRequest, CreatePromotionRunRequest, CreateSubjectAllocationRequest, PromotionDecisionRequest, PromotionRunId, LockTermRequest (+3 more)

### Community 23 - "RBAC & Permission Evaluator"
Cohesion: 0.12
Nodes (7): ActionCatalogRepository, UpdateUserRequest, PermissionEvaluatorServiceTest, UserDeactivationActionTest, UserEditActionTest, UserPasswordResetUnlockActionTest, UserViewActionTest

### Community 24 - "Audit"
Cohesion: 0.03
Nodes (59): AuditEventType, ACADEMIC_SESSION_CREATED, ACADEMIC_SESSION_UPDATED, ACCOUNT_CREATED, APPROVAL, APPROVAL_BULK_DECIDED, APPROVAL_DELEGATED, APPROVAL_DELEGATION_CANCELLED (+51 more)

### Community 25 - "Platform Operations & Governance"
Cohesion: 0.09
Nodes (15): KeycloakProvisioningException, PlatformUser, PlatformUserRepository, PlatformUserStatus, ACTIVE, INACTIVE, PlatformUserCreateRequest, PlatformUserResponse (+7 more)

### Community 26 - "RBAC & Permission Evaluator"
Cohesion: 0.09
Nodes (41): Icon(), IconName, paths, EmptyStateProps, Kpi, MetricCardProps, PageHeader(), DashboardPanel() (+33 more)

### Community 27 - "Platform Operations & Governance"
Cohesion: 0.09
Nodes (45): AccessMode, ActionCatalogItem, baseLimitReason(), CreateRolePayload, CUSTOMISABLE_ROLES, CustomRoleStatus, PlatformRoleItem, platformRoleService (+37 more)

### Community 28 - "Institution Structure & Hierarchy"
Cohesion: 0.10
Nodes (9): BlockDecisionRequest, BlockInstitutionRequest, CreateDepartmentRequest, CreateInstitutionRequest, CreateProgramRequest, InstitutionController, InstitutionId, InstitutionControllerTest (+1 more)

### Community 30 - "RBAC & Permission Evaluator"
Cohesion: 0.13
Nodes (8): AssignRoleRequest, UserRoleAssignmentResponse, RoleDefinitionId, UserRoleAssignmentId, SwitchActiveRoleRequest, RoleAssignmentControllerTest, UserGovernanceGuardTest, UserMeControllerTest

### Community 31 - "Approval"
Cohesion: 0.10
Nodes (18): PromotionRunRepository, PromotionService, ApprovalInboxService, ApprovalRequestRepository, ApprovalStepRepository, ApprovalInboxResponseDto, PageInfoDto, ApprovalStepDto (+10 more)

### Community 32 - "Platform Operations & Governance"
Cohesion: 0.08
Nodes (12): RecordPaymentRequest, PlatformBillingController, PlatformDataProtectionController, ReleaseResponse, ReleaseTransitionRequest, UpdateReleaseRequest, PlatformReleaseController, PlatformReleaseService (+4 more)

### Community 33 - "Institution Structure & Hierarchy"
Cohesion: 0.06
Nodes (40): Badge(), AcademicSessionItem, CreateAcademicSessionPayload, CreateDepartmentPayload, CreateInstitutionRequest, CreateMasterCalendarEventPayload, CreateProgramPayload, DepartmentItem (+32 more)

### Community 34 - "Platform Operations & Governance"
Cohesion: 0.13
Nodes (4): GoLiveBlockerItem, GoLiveCheckResponse, TenantActivationRequest, PlatformTenantControllerTest

### Community 35 - "Institution Structure & Hierarchy"
Cohesion: 0.08
Nodes (9): ProgramRuleSetRepository, AcademicSessionRepository, CourseOfferingRepository, MentorAssignmentService, ProgramRepository, ProgramYearRepository, SemesterRepository, StructureService (+1 more)

### Community 36 - "Platform Operations & Governance"
Cohesion: 0.10
Nodes (11): PlatformPrincipal, PlatformAccessLookup, PlatformAccessLookup, PlatformPermissionService, StepInRequest, PlatformAuditWriter, PlatformStepInService, StoredPlatformAccessLookup (+3 more)

### Community 37 - "Application Configuration & Security"
Cohesion: 0.12
Nodes (4): JpaRepositoryConfig, OpenApiConfig, PlatformJpaConfig, DataSourceConfig

### Community 38 - "Application Configuration & Security"
Cohesion: 0.14
Nodes (8): DevRoleAssignmentSeeder, UserAccount, UserAccountStatus, ACTIVE, DEACTIVATED, PENDING_ACTIVATION, CreateUserResponse, DevRoleAssignmentSeederTest

### Community 39 - "Institution Structure & Hierarchy"
Cohesion: 0.11
Nodes (20): ClassTeacherAssignResponse, CourseOfferingResponse, CreateCourseOfferingRequest, CreateProgramYearRequest, CreateSchoolClassRequest, CreateSectionRequest, CreateSemesterRequest, CreateSubjectRequest (+12 more)

### Community 40 - "Platform Operations & Governance"
Cohesion: 0.15
Nodes (8): CreatePlanRequest, PlanResponse, TenantLifecycleActionRequest, TenantPageResponse, TenantResponse, TenantSettingsResponse, UpdatePlanRequest, PlatformTenantController

### Community 41 - "Platform Operations & Governance"
Cohesion: 0.11
Nodes (24): Edu360Logo(), LogoStyle, Kicker(), LinkButton(), PasswordField(), ChangePasswordPage(), LoginPage(), Tenant (+16 more)

### Community 43 - "Self-Service & Role Switching"
Cohesion: 0.16
Nodes (4): FileObjectRepository, ProfileService, StoredPhoto, ProfileServiceTest

### Community 44 - "Imports"
Cohesion: 0.14
Nodes (7): ResourceNotFoundException, ImportJobResponse, ImportRowErrorResponse, RowValidationError, UserBulkUploadService, InvoiceResponse, PlatformBillingService

### Community 45 - "Multi-Tenant Routing & Lifecycle"
Cohesion: 0.12
Nodes (4): CachedPool, TenantRoutingDataSource, TenantSecretsResolver, TenantRoutingDataSourceTest

### Community 46 - "Authentication & Identity Management"
Cohesion: 0.11
Nodes (10): BulkApprovalResponse, TopUserResponse, UserType, APPLICANT, GOVERNANCE, GUARDIAN, STAFF, STUDENT (+2 more)

### Community 49 - "Authentication & Identity Management"
Cohesion: 0.15
Nodes (6): AuthController, ChangePasswordRequest, IdentityEventRequest, LoginRequest, RefreshRequest, AuthControllerTest

### Community 50 - "Institution Structure & Hierarchy"
Cohesion: 0.20
Nodes (4): LeaveApproverSetRequest, LeaveApproverSettingResponse, PermissionDecision, LeaveApproverControllerTest

### Community 52 - "Institution Structure & Hierarchy"
Cohesion: 0.20
Nodes (4): CreateAcademicSessionRequest, CreateMasterCalendarEventRequest, MasterCalendarController, MasterCalendarControllerTest

### Community 54 - "Platform Operations & Governance"
Cohesion: 0.24
Nodes (6): CreateIntegrationTypeRequest, IntegrationTypeResponse, UpdateIntegrationTypeRequest, IntegrationTypeController, IntegrationTypeRepository, IntegrationTypeControllerTest

### Community 55 - "Platform Operations & Governance"
Cohesion: 0.18
Nodes (6): AccessMode, HANDED_OVER, SHARED, CreatePlatformRoleRequest, RoleActionItem, PlatformCustomRoleServiceTest

### Community 56 - "Platform Operations & Governance"
Cohesion: 0.20
Nodes (3): GrantSupportAccessRequest, PlatformSupportControllerTest, TenantSupportControllerTest

### Community 57 - "Application Configuration & Security"
Cohesion: 0.15
Nodes (3): CorrelationIdFilter, TenantContextClearingFilter, TenantContextFilterConfig

### Community 58 - "Authentication & Identity Management"
Cohesion: 0.20
Nodes (4): TopUserCreateRequest, CreateUserRequest, TopUserControllerTest, UserCreationControllerTest

### Community 59 - "Platform Operations & Governance"
Cohesion: 0.12
Nodes (11): PlatformRole, BILLING_MANAGER, DATA_PROTECTION_OFFICER, PLATFORM_ADMIN, PRODUCT_OWNER, RELEASE_MANAGER, SUPPORT_MANAGER, MyPlatformPermissionsResponse (+3 more)

### Community 60 - "Platform Operations & Governance"
Cohesion: 0.13
Nodes (6): Invoice, InvoiceRepository, SupportAccessGrant, SupportAccessGrantRepository, SupportTicket, SupportTicketRepository

### Community 61 - "Platform Operations & Governance"
Cohesion: 0.21
Nodes (4): PlatformRoleTemplateResponse, SavePlatformRoleTemplateRequest, PlatformRoleTemplateActionRepository, PlatformRoleTemplateService

### Community 62 - "Core Utilities"
Cohesion: 0.09
Nodes (21): compilerOptions, allowImportingTsExtensions, erasableSyntaxOnly, jsx, lib, module, moduleDetection, moduleResolution (+13 more)

### Community 63 - "Platform Operations & Governance"
Cohesion: 0.20
Nodes (4): EntitlementsResponse, EntitlementsUpdateRequest, PlatformFeatureToggleController, PlatformSecurityReportControllerTest

### Community 64 - "User Profile UI"
Cohesion: 0.16
Nodes (15): PlatformRole, ProfileResponse, profileService, ProfileUpdateRequest, UserType, initialsFor(), ProfileAvatarSection(), ProfileAvatarSectionProps (+7 more)

### Community 65 - "Test Imports"
Cohesion: 0.19
Nodes (5): LocalFileStorage, UncheckedFileStorageException, StartImportRequest, UserImportRequest, UserBulkUploadControllerTest

### Community 67 - "Self-Service & Role Switching"
Cohesion: 0.20
Nodes (3): RolldownController, SessionController, SessionControllerTest

### Community 68 - "Core Utilities"
Cohesion: 0.10
Nodes (19): compilerOptions, allowImportingTsExtensions, erasableSyntaxOnly, lib, module, moduleDetection, moduleResolution, noEmit (+11 more)

### Community 69 - "Institution Structure & Hierarchy"
Cohesion: 0.22
Nodes (5): AcademicCalendarEventRepository, CalendarEventService, CalendarEventRequest, CalendarEventResponse, CalendarEventServiceTest

### Community 70 - "Platform Operations & Governance"
Cohesion: 0.20
Nodes (4): SupportAccessGrantResponse, SupportTicketResponse, PlatformSupportService, TenantSupportController

### Community 71 - "Institution Structure & Hierarchy"
Cohesion: 0.29
Nodes (4): StudentLoginSwitchRequest, SchoolClass, SchoolClassRepository, StudentLoginSwitchServiceTest

### Community 73 - "Core Utilities"
Cohesion: 0.17
Nodes (15): devDependencies, eslint@^9.39.1, @eslint/js@^9.39.1, eslint-plugin-react-hooks@^7.0.1, eslint-plugin-react-refresh@^0.4.24, globals@^16.5.0, @testing-library/react@^16.0.1, typescript-eslint@^8.46.4 (+7 more)

### Community 74 - "Core Utilities"
Cohesion: 0.12
Nodes (16): dependencies, axios@^1.12.2, clsx@^2.1.1, date-fns@^4.4.0, react@^19.2.0, react-dom@^19.2.0, react-redux@^9.2.0, react-router-dom@^7.18.5 (+8 more)

### Community 78 - "Core Utilities"
Cohesion: 0.13
Nodes (14): jsdom@^27.0.0, postcss@^8.5.0, @types/node@^24.10.1, typescript@~5.9.3, vite@^7.2.4, name, private, type (+6 more)

### Community 79 - "Authentication & Identity Management"
Cohesion: 0.13
Nodes (14): KC_BOOTSTRAP_ADMIN_PASSWORD, KC_BOOTSTRAP_ADMIN_USERNAME, KC_SPI_THEME_CACHE_TEMPLATES, KC_SPI_THEME_CACHE_THEMES, KC_SPI_THEME_STATIC_MAX_AGE, command, environment, image (+6 more)

### Community 80 - "Platform Operations & Governance"
Cohesion: 0.25
Nodes (7): CreateRetentionChangeRequest, DataProtectionDtos, DeletionRequestResponse, ExportRequestResponse, RetentionChangeRequestResponse, RetentionPolicyResponse, PlatformDataProtectionService

### Community 83 - "RBAC & Permission Evaluator"
Cohesion: 0.25
Nodes (4): UserListResponse, UserSummaryResponse, UserRequestSupport, UserViewController

### Community 88 - "Docker Localstack"
Cohesion: 0.15
Nodes (12): AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, DEFAULT_REGION, SERVICES, environment, image, ports, volumes (+4 more)

### Community 89 - ".Github Workflows"
Cohesion: 0.26
Nodes (13): steps, steps, steps, GITHUB_TOKEN, PGPASSWORD, PSQL, steps, steps (+5 more)

### Community 91 - "RBAC & Permission Evaluator"
Cohesion: 0.36
Nodes (4): RoleDetailResponse, UpdateCustomRoleRequest, UpdateRoleActionSetRequest, RoleCustomController

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

### Community 98 - "Multi-Tenant Routing & Lifecycle"
Cohesion: 0.20
Nodes (7): TenantState, ACTIVE, ARCHIVED, CREATED, DELETED, SUSPENDED, TRIAL

### Community 100 - "Platform Operations & Governance"
Cohesion: 0.44
Nodes (4): CreateSecurityIncidentRequest, PlatformSecurityPolicyController, SecurityIncidentResponse, UpdateSecurityIncidentRequest

### Community 101 - "Core Utilities"
Cohesion: 0.20
Nodes (10): scripts, build, dev, format, format:check, lint, preview, test (+2 more)

### Community 102 - "Lib"
Cohesion: 0.40
Nodes (8): ThemeToggle(), applyTheme(), getStoredTheme(), initTheme(), setStoredTheme(), systemTheme(), Theme, toggleStoredTheme()

### Community 108 - ".Github Workflows"
Cohesion: 0.25
Nodes (8): if, name, permissions, runs-on, dependency-review, permissions, contents, pull-requests

### Community 110 - "Hooks"
Cohesion: 0.43
Nodes (3): useClickOutside(), useDebounce(), useUrlFilters()

### Community 111 - ".Github Workflows"
Cohesion: 0.29
Nodes (7): name, permissions, runs-on, steps, codeql, actions, security-events

### Community 114 - "Platform Operations & Governance"
Cohesion: 0.33
Nodes (5): PlatformCustomRoleStatus, ACTIVE, ARCHIVED, DRAFT, INACTIVE

### Community 119 - ".Github Workflows"
Cohesion: 0.33
Nodes (6): run, defaults, name, runs-on, frontend, working-directory

### Community 120 - ".Github Workflows"
Cohesion: 0.33
Nodes (6): on, pull_request, push, workflow_dispatch, branches, branches

### Community 123 - "Core Utilities"
Cohesion: 0.40
Nodes (4): printWidth, proseWrap, semi, trailingComma

### Community 124 - "Utils"
Cohesion: 0.70
Nodes (3): formErrorMapping, normalizeFormErrors(), splitFullName()

## Knowledge Gaps
- **112 isolated node(s):** `com.edu360:api`, `LOGIN_SUCCESS`, `LOGIN_FAILURE`, `LOGOUT`, `ACCOUNT_CREATED` (+107 more)
  These have ≤1 connection - possible missing edges. (Counts symbols only; 556 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **71 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `TenantPrincipal` connect `Institution Structure & Hierarchy` to `Self-Service & Role Switching`, `Institution Structure & Hierarchy`, `RBAC & Permission Evaluator`, `Platform Operations & Governance`, `RBAC & Permission Evaluator`, `Authentication & Identity Management`, `Institution Structure & Hierarchy`, `Authentication & Identity Management`, `Institution Structure & Hierarchy`, `Institution Structure & Hierarchy`, `RBAC & Permission Evaluator`, `Platform Operations & Governance`, `Institution Structure & Hierarchy`, `Institution Structure & Hierarchy`, `RBAC & Permission Evaluator`, `Institution Structure & Hierarchy`, `RBAC & Permission Evaluator`, `Approval`, `Institution Structure & Hierarchy`, `Self-Service & Role Switching`, `Authentication & Identity Management`, `Authentication & Identity Management`, `Institution Structure & Hierarchy`, `Institution Structure & Hierarchy`, `Platform Operations & Governance`, `Authentication & Identity Management`, `Test Imports`, `Self-Service & Role Switching`, `Platform Operations & Governance`, `RBAC & Permission Evaluator`, `Self-Service & Role Switching`, `RBAC & Permission Evaluator`, `Self-Service & Role Switching`, `Self-Service & Role Switching`, `Self-Service & Role Switching`, `Termlock`?**
  _High betweenness centrality (0.026) - this node is a cross-community bridge._
- **What connects `com.edu360:api`, `LOGIN_SUCCESS`, `LOGIN_FAILURE` to the rest of the system?**
  _112 weakly-connected nodes found - possible missing edges._
- **Should `Academic & Curriculum Management` be split into smaller, more focused modules?**
  _Cohesion score 0.05249559600704639 - nodes in this community are weakly interconnected._
- **Why does `AuditLogService` connect `Institution Structure & Hierarchy` to `Self-Service & Role Switching`, `Institution Structure & Hierarchy`, `RBAC & Permission Evaluator`, `Platform Operations & Governance`, `RBAC & Permission Evaluator`, `Authentication & Identity Management`, `Institution Structure & Hierarchy`, `Authentication & Identity Management`, `Institution Structure & Hierarchy`, `Institution Structure & Hierarchy`, `RBAC & Permission Evaluator`, `Institution Structure & Hierarchy`, `Institution Structure & Hierarchy`, `RBAC & Permission Evaluator`, `Institution Structure & Hierarchy`, `RBAC & Permission Evaluator`, `Approval`, `Platform Operations & Governance`, `Platform Operations & Governance`, `Institution Structure & Hierarchy`, `Institution Structure & Hierarchy`, `Self-Service & Role Switching`, `Imports`, `Authentication & Identity Management`, `Authentication & Identity Management`, `Institution Structure & Hierarchy`, `Institution Structure & Hierarchy`, `Platform Operations & Governance`, `Authentication & Identity Management`, `Platform Operations & Governance`, `Test Imports`, `Platform Operations & Governance`, `Platform Operations & Governance`, `Platform Operations & Governance`, `Platform Operations & Governance`, `Multi-Tenant Routing & Lifecycle`, `RBAC & Permission Evaluator`, `Platform Operations & Governance`, `Self-Service & Role Switching`, `Test Audit`?**
  _High betweenness centrality (0.024) - this node is a cross-community bridge._
- **Should `Institution Structure & Hierarchy` be split into smaller, more focused modules?**
  _Cohesion score 0.08361454818147732 - nodes in this community are weakly interconnected._
- **Why does `AuditEventType` connect `Audit` to `Institution Structure & Hierarchy`, `Institution Structure & Hierarchy`, `RBAC & Permission Evaluator`, `Platform Operations & Governance`, `Institution Structure & Hierarchy`, `Self-Service & Role Switching`, `Imports`, `Institution Structure & Hierarchy`, `Authentication & Identity Management`, `Platform Operations & Governance`, `RBAC & Permission Evaluator`, `Institution Structure & Hierarchy`, `Institution Structure & Hierarchy`?**
  _High betweenness centrality (0.023) - this node is a cross-community bridge._
- **Should `Self-Service & Role Switching` be split into smaller, more focused modules?**
  _Cohesion score 0.03518017376442102 - nodes in this community are weakly interconnected._
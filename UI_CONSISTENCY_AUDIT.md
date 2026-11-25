# UI/UX Consistency Audit Report (Phase 1)
**Date:** November 2026  
**Scope:** `app/src/` (235 source files inspected line-by-line, excluding `app/src/design-system/` and `app/src/components/ui/` primitives)  
**Status:** Audit Complete (Phase 1 Deliverable — Pending User Review)  

---

## 1. Executive Summary

| Category | Total Findings | Description |
| :--- | :---: | :--- |
| **Raw HTML Form Elements** | **161** | Bare `<input>`, `<select>`, `<textarea>`, and `<button>` tags bypassing design system equivalents |
| **Hardcoded Colors** | **181** | Raw hex (`#...`), `rgb()`, `rgba()`, and arbitrary Tailwind palette classes bypassing design tokens |
| **Hardcoded Fonts** | **16** | Inline `font-family` or `fontFamily` declarations setting fonts outside typography tokens |
| **Hardcoded Spacing / Sizing** | **490** | Inline `style={{ margin, padding, width, height, gap }}` bypassing token spacing scales |
| **Duplicated Components & Patterns** | **6 Patterns** | Duplicate implementations of `EmptyState`, notification popovers, custom modals, and repeated form controls |
| **Missing Interaction States** | **154** | Interactive elements lacking explicit `:hover`, `:focus-visible`, or `disabled:` styling |

---

## 2. Raw HTML Form Elements

The following **161** raw HTML elements bypass design system components (`TextField`, `SelectField`, `TextAreaField`, `Button`, `CheckboxField`, `DateField`, `FileDrop`, `SearchBox`):

| File | Line | Element | Context / What it's doing | Recommended Design System Replacement |
| :--- | :---: | :---: | :--- | :--- |
| `components/common/FileDrop.tsx` | 65 | `<input>` | `<input ref={input} id={id} type="file" accept={accept} aria-invalid={Boolean(vis` | **FileDrop (or hidden native file input)** |
| `components/common/PersonPicker.tsx` | 11 | `<select>` | `<select className="app-input" value={value} disabled={disabled} onChange={(event` | **SelectField** |
| `components/common/SectionPicker.tsx` | 7 | `<select>` | `<select className="app-input" value={value} disabled={disabled} onChange={(event` | **SelectField** |
| `components/common/StatusToggleRoster.tsx` | 5 | `<select>` | `return <ul>{rows.map((row) => <li key={row.id}><span>{row.name}</span><select ar` | **SelectField** |
| `components/common/SubjectAllocationPicker.tsx` | 7 | `<select>` | `<select className="app-input" value={value} disabled={disabled} onChange={(event` | **SelectField** |
| `components/common/WeekGrid.tsx` | 23 | `<button>` | `<button ref={(node) => { buttons.current[index] = node; }}` | **Button** |
| `components/layout/RoleSwitcher.tsx` | 90 | `<select>` | `<select className="role-switcher__select" aria-label="Switch active role" ref={s` | **SelectField** |
| `components/layout/RoleSwitcher.tsx` | 126 | `<select>` | `<select className="role-switcher__select" aria-label="Switch active role" value=` | **SelectField** |
| `features/admissions/pages/ApplicantPortalPage.tsx` | 35 | `<select>` | `<div className="app-inline-actions"><Button type="button" variant="secondary" on` | **SelectField** |
| `features/admissions/pages/ApplicantPortalPage.tsx` | 41 | `<select>` | `<label htmlFor="applicant-category">Quota category</label><select id="applicant-` | **SelectField** |
| `features/admissions/pages/ApplicantPortalPage.tsx` | 45 | `<input>` | `<Panel title="Supporting documents"><p>PDF, JPG or PNG only. Maximum 5 MB per fi` | **FileDrop (or hidden native file input)** |
| `features/institution-admin/components/academic/AddMasterEventModal.tsx` | 72 | `<select>` | `<select id="master-event-session-select" value={targetSessionId \|\| sessions[0]` | **SelectField** |
| `features/institution-admin/components/academic/AddMasterEventModal.tsx` | 99 | `<select>` | `<select id="master-event-type-select" value={masterEventType} onChange={(e) => s` | **SelectField** |
| `features/institution-admin/components/academic/AddProgramModal.tsx` | 71 | `<select>` | `<select id="prog-dept-select" value={deptId \|\| departments[0]?.departmentId \|` | **SelectField** |
| `features/institution-admin/components/academic/UnlockTermModal.tsx` | 47 | `<textarea>` | `<textarea id="unlock-reason" rows={3} required minLength={20} maxLength={500} pl` | **TextAreaField** |
| `features/institution-admin/components/academic/UnlockTermModal.tsx` | 75 | `<input>` | `<input id="unlock-duration" type="number" value={72} disabled readOnly style={{ ` | **TextField** |
| `features/institution-admin/components/institutions/BlockRequestModal.tsx` | 72 | `<textarea>` | `<textarea id="block-reason" required rows={4} value={reason} onChange={(e) => se` | **TextAreaField** |
| `features/institution-admin/components/institutions/GovernanceDecisionModal.tsx` | 73 | `<input>` | `<input type="radio" name="decision-choice" checked={approved} onChange={() => se` | **RadioField / design-system radio** |
| `features/institution-admin/components/institutions/GovernanceDecisionModal.tsx` | 82 | `<input>` | `<input type="radio" name="decision-choice" checked={!approved} onChange={() => s` | **RadioField / design-system radio** |
| `features/institution-admin/components/institutions/GovernanceDecisionModal.tsx` | 100 | `<textarea>` | `<textarea id="decision-notes" rows={3} value={notes} onChange={(e) => setNotes(e` | **TextAreaField** |
| `features/institution-admin/components/institutions/PrincipalLeaveApproverModal.tsx` | 80 | `<select>` | `<select id="approver-kind" value={approverKind} onChange={(e) => setApproverKind` | **SelectField** |
| `features/institution-admin/components/institutions/ViceChancellorModal.tsx` | 77 | `<select>` | `<select id="vc-approver-kind" value={approverKind} onChange={(e) => setApproverK` | **SelectField** |
| `features/institution-admin/components/roles/CreateCustomRoleModal.tsx` | 118 | `<select>` | `<select id="custom-role-level" value={roleLevel} onChange={(e) => setRoleLevel(e` | **SelectField** |
| `features/institution-admin/components/roles/CreateCustomRoleModal.tsx` | 138 | `<select>` | `<select id="custom-role-scope" value={customScope} onChange={(e) => setCustomSco` | **SelectField** |
| `features/institution-admin/components/roles/RolldownOverrideModal.tsx` | 126 | `<select>` | `<select value={permissionKind} onChange={(e) => setPermissionKind(e.target.value` | **SelectField** |
| `features/institution-admin/components/roles/RolldownOverrideModal.tsx` | 144 | `<select>` | `<select value={mode} onChange={(e) => setMode(e.target.value)}` | **SelectField** |
| `features/institution-admin/components/roles/RolldownOverrideModal.tsx` | 181 | `<textarea>` | `<textarea id="override-statutory-reason" rows={3} maxLength={500} value={reason}` | **TextAreaField** |
| `features/institution-admin/pages/AcademicSessionDetailPage.tsx` | 39 | `<button>` | `<button type="button" onClick={makeCurrent} disabled={saving \|\| !tenantId \|\|` | **Button** |
| `features/institution-admin/pages/InstitutionAdminPages.tsx` | 242 | `<button>` | `<button onClick={() => setFeedback(null)}` | **Button** |
| `features/institution-admin/pages/InstitutionAdminPages.tsx` | 267 | `<input>` | `<input value={query} onChange={(e) => setQuery(e.target.value)}` | **TextField** |
| `features/institution-admin/pages/InstitutionAdminPages.tsx` | 275 | `<select>` | `<select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}` | **SelectField** |
| `features/institution-admin/pages/InstitutionAdminPages.tsx` | 581 | `<button>` | `<button onClick={() => setFeedback(null)}` | **Button** |
| `features/institution-admin/pages/InstitutionAdminPages.tsx` | 615 | `<input>` | `<input value={query} onChange={(e) => setQuery(e.target.value)}` | **TextField** |
| `features/institution-admin/pages/InstitutionSetupPages.tsx` | 413 | `<input>` | `<input type="checkbox" checked={editDraft.externalUniversityMode} onChange={(eve` | **CheckboxField** |
| `features/institution-admin/pages/InstitutionSetupPages.tsx` | 687 | `<input>` | `<input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.ch` | **CheckboxField** |
| `features/institution-admin/pages/InstitutionSetupPages.tsx` | 939 | `<input>` | `<input id="cal-institution-id" value={institutionId} onChange={(e) => {` | **TextField** |
| `features/institution-admin/pages/InstitutionSetupPages.tsx` | 1240 | `<input>` | `<input id="wo-institution-id" value={institutionId} aria-invalid={Boolean(fieldE` | **TextField** |
| `features/institution-admin/pages/InstitutionSetupPages.tsx` | 1325 | `<select>` | `<select value={dayOfWeek} onChange={(e) => setDayOfWeek(Number(e.target.value))}` | **SelectField** |
| `features/institution-admin/pages/InstitutionSetupPages.tsx` | 1335 | `<select>` | `<select value={occurrence} onChange={(e) => setOccurrence(e.target.value)}>` | **SelectField** |
| `features/institution-admin/pages/StaffRecordsPage.tsx` | 310 | `<button>` | `<button type="button" aria-pressed={selected?.id === person.id} className={`app-` | **Button** |
| `features/notifications/components/NotificationBell.tsx` | 76 | `<button>` | `<button ref={buttonRef} type="button" className="app-icon-button notif-bell__but` | **Button** |
| `features/notifications/components/NotificationBell.tsx` | 96 | `<button>` | `{hasUnread && <button type="button" className="notif-bell__link" onClick={() => ` | **Button** |
| `features/notifications/components/NotificationBell.tsx` | 97 | `<button>` | `<button type="button" className="app-icon-button notif-bell__close" onClick={clo` | **Button** |
| `features/notifications/components/NotificationBell.tsx` | 135 | `<button>` | `<button type="button" className="notif-bell__link" aria-label={`Mark "${n.title}` | **Button** |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 217 | `<input>` | `<input type="text" className="app-input" style={{ fontSize: "12px", padding: "4p` | **TextField** |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 231 | `<input>` | `<input type="text" className="app-input" style={{ fontSize: "12px", padding: "4p` | **TextField** |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 245 | `<input>` | `<input type="text" className="app-input" style={{ fontSize: "12px", padding: "4p` | **TextField** |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 268 | `<button>` | `<button type="button" onClick={openWhatsApp} title="Share on WhatsApp" style={{ ` | **Button** |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 304 | `<button>` | `<button type="button" onClick={openTelegram} title="Share on Telegram" style={{ ` | **Button** |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 341 | `<button>` | `<button type="button" onClick={() => void handleNativeShare()}` | **Button** |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 380 | `<button>` | `<button type="button" onClick={handleDownloadPdf} disabled={downloadingFormat !=` | **Button** |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 417 | `<button>` | `<button type="button" onClick={() => void handleDownloadImage()}` | **Button** |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 454 | `<button>` | `<button type="button" onClick={() => void handleCopyImage()}` | **Button** |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 490 | `<button>` | `<button type="button" onClick={() => void handleCopyText()}` | **Button** |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 528 | `<button>` | `<button type="button" onClick={openEmail} title="Email" style={{ background: "tr` | **Button** |
| `features/platform/components/billing/InvoicesView.tsx` | 85 | `<button>` | `<button ref={triggerRef} type="button" className="eds-button eds-button--seconda` | **Button** |
| `features/platform/components/billing/InvoicesView.tsx` | 133 | `<button>` | `<button type="button" role="menuitem" onClick={onClick} style={{ display: "block` | **Button** |
| `features/platform/components/billing/InvoicesView.tsx` | 591 | `<button>` | `<button type="button" className={`app-filter-tab ${statusFilter === "All" ? "app` | **Button** |
| `features/platform/components/billing/InvoicesView.tsx` | 598 | `<button>` | `<button type="button" className={`app-filter-tab ${statusFilter === "DRAFT" ? "a` | **Button** |
| `features/platform/components/billing/InvoicesView.tsx` | 605 | `<button>` | `<button type="button" className={`app-filter-tab ${statusFilter === "APPROVED" ?` | **Button** |
| `features/platform/components/billing/InvoicesView.tsx` | 612 | `<button>` | `<button type="button" className={`app-filter-tab ${statusFilter === "SENT" ? "ap` | **Button** |
| `features/platform/components/billing/InvoicesView.tsx` | 619 | `<button>` | `<button type="button" className={`app-filter-tab ${statusFilter === "OVERDUE" ? ` | **Button** |
| `features/platform/components/billing/InvoicesView.tsx` | 626 | `<button>` | `<button type="button" className={`app-filter-tab ${statusFilter === "PAID" ? "ap` | **Button** |
| `features/platform/components/billing/InvoicesView.tsx` | 645 | `<select>` | `<select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>` | **SelectField** |
| `features/platform/components/entitlements/EntitlementEditor.tsx` | 138 | `<input>` | `<input type="checkbox" id={`ent-${m.moduleCode}`} checked={on} disabled={!!reaso` | **CheckboxField** |
| `features/platform/components/entitlements/EntitlementEditor.tsx` | 163 | `<input>` | `<input type="checkbox" id={`ent-${f.key}`} checked={fOn} disabled={!!fReason} ti` | **CheckboxField** |
| `features/platform/components/notifications/PlatformNotificationBell.tsx` | 98 | `<button>` | `<button ref={buttonRef} type="button" className="app-icon-button notif-bell__but` | **Button** |
| `features/platform/components/notifications/PlatformNotificationBell.tsx` | 118 | `<button>` | `{unreadCount > 0 && <button type="button" className="notif-bell__link" onClick={` | **Button** |
| `features/platform/components/notifications/PlatformNotificationBell.tsx` | 119 | `<button>` | `<button type="button" className="app-icon-button notif-bell__close" onClick={clo` | **Button** |
| `features/platform/components/notifications/PlatformNotificationBell.tsx` | 149 | `<button>` | `{unread && <button type="button" className="notif-bell__link" aria-label={`Mark ` | **Button** |
| `features/platform/components/observability/AuditLogView.tsx` | 164 | `<input>` | `<input className="app-input" aria-label="Tenant ID" value={draftFilters.tenantId` | **TextField** |
| `features/platform/components/observability/AuditLogView.tsx` | 168 | `<input>` | `<input className="app-input" aria-label="Actor user ID" value={draftFilters.acto` | **TextField** |
| `features/platform/components/observability/AuditLogView.tsx` | 172 | `<input>` | `<input className="app-input" aria-label="Action code" value={draftFilters.action` | **TextField** |
| `features/platform/components/observability/AuditLogView.tsx` | 176 | `<select>` | `<select className="app-input" aria-label="Outcome" value={draftFilters.outcome} ` | **SelectField** |
| `features/platform/components/observability/AuditLogView.tsx` | 185 | `<input>` | `<input className="app-input" aria-label="From" type="datetime-local" value={draf` | **DateTimeField** |
| `features/platform/components/observability/AuditLogView.tsx` | 189 | `<input>` | `<input className="app-input" aria-label="To" type="datetime-local" value={draftF` | **DateTimeField** |
| `features/platform/components/overview/ProductOwnerDashboard.tsx` | 546 | `<button>` | `<button type="button" className="po-chip" aria-pressed={!deniedOnly} onClick={()` | **Button** |
| `features/platform/components/overview/ProductOwnerDashboard.tsx` | 547 | `<button>` | `<button type="button" className="po-chip" aria-pressed={deniedOnly} onClick={() ` | **Button** |
| `features/platform/components/plans/CommercialCatalogModal.tsx` | 157 | `<input>` | `<input type="checkbox" checked={draft.available} onChange={(event) => setDraft((` | **CheckboxField** |
| `features/platform/components/plans/CreatePlanModal.tsx` | 255 | `<button>` | `<button type="button" onClick={() => setShowEstimateBreakdown(!showEstimateBreak` | **Button** |
| `features/platform/components/plans/CreatePlanModal.tsx` | 292 | `<button>` | `<button type="button" role="tab" aria-selected={activeTab === "all"} className={` | **Button** |
| `features/platform/components/plans/CreatePlanModal.tsx` | 301 | `<button>` | `<button type="button" role="tab" aria-selected={activeTab === "pricing"} classNa` | **Button** |
| `features/platform/components/plans/CreatePlanModal.tsx` | 310 | `<button>` | `<button type="button" role="tab" aria-selected={activeTab === "limits"} classNam` | **Button** |
| `features/platform/components/plans/CreatePlanModal.tsx` | 320 | `<button>` | `<button type="button" role="tab" aria-selected={activeTab === "components"} clas` | **Button** |
| `features/platform/components/plans/CreatePlanModal.tsx` | 330 | `<button>` | `<button type="button" role="tab" aria-selected={activeTab === "terms"} className` | **Button** |
| `features/platform/components/plans/CreatePlanModal.tsx` | 465 | `<input>` | `<input type="checkbox" checked={isChecked} onChange={() => toggleModule(mod.code` | **CheckboxField** |
| `features/platform/components/plans/CreatePlanModal.tsx` | 752 | `<input>` | `<input type="checkbox" checked={isEnabled} onChange={(event) => setComponents((c` | **CheckboxField** |
| `features/platform/components/plans/CreatePlanModal.tsx` | 817 | `<input>` | `<input type="checkbox" checked={createDraft.customDomainAllowed} onChange={(e) =` | **CheckboxField** |
| `features/platform/components/plans/CreatePlanModal.tsx` | 828 | `<input>` | `<input type="checkbox" checked={createDraft.brandingColorsAllowed} onChange={(e)` | **CheckboxField** |
| `features/platform/components/plans/PlansView.tsx` | 744 | `<button>` | `<button type="button" className={`app-filter-tab ${statusFilter === "ALL" ? "app` | **Button** |
| `features/platform/components/plans/PlansView.tsx` | 751 | `<button>` | `<button type="button" className={`app-filter-tab ${statusFilter === "ACTIVE" ? "` | **Button** |
| `features/platform/components/plans/PlansView.tsx` | 758 | `<button>` | `<button type="button" className={`app-filter-tab ${statusFilter === "INACTIVE" ?` | **Button** |
| `features/platform/components/plans/PlansView.tsx` | 1069 | `<input>` | `<input type="checkbox" checked={quoteNeeds.includeSetupFee} onChange={(event) =>` | **CheckboxField** |
| `features/platform/components/reports/ReportsView.tsx` | 226 | `<button>` | `<button key={tab.code} type="button" role="tab" aria-selected={isActive} onClick` | **Button** |
| `features/platform/components/reports/ReportsView.tsx` | 259 | `<select>` | `<select value={reportCode} onChange={(e) => setReportCode(e.target.value)}` | **SelectField** |
| `features/platform/components/reports/ReportsView.tsx` | 503 | `<button>` | `<button key={tab.key} type="button" onClick={() => setTenantFilter(tab.key)}` | **Button** |
| `features/platform/components/reports/ReportsView.tsx` | 523 | `<input>` | `<input type="search" placeholder="Search tenants..." value={tenantSearch} onChan` | **SearchBox** |
| `features/platform/components/reports/ReportsView.tsx` | 895 | `<button>` | `<button key={tab.key} type="button" onClick={() => setInvoiceFilter(tab.key)}` | **Button** |
| `features/platform/components/reports/ReportsView.tsx` | 1090 | `<button>` | `<button key={tab.key} type="button" onClick={() => setTicketFilter(tab.key)}` | **Button** |
| `features/platform/components/roles/PermissionPicker.tsx` | 57 | `<input>` | `<input type="checkbox" checked={isOn} disabled={readOnly \|\| !!lockedReason} on` | **CheckboxField** |
| `features/platform/components/roles/PermissionPicker.tsx` | 79 | `<select>` | `<select aria-label={`Who does ${item.description}`} value={selected[item.actionC` | **SelectField** |
| `features/platform/components/roles/RoleEditorModal.tsx` | 167 | `<select>` | `<select value={templateId} onChange={(e) => chooseTemplate(e.target.value)}>` | **SelectField** |
| `features/platform/components/roles/RoleEditorModal.tsx` | 178 | `<select>` | `<select value={base} onChange={(e) => chooseBase(e.target.value as PlatformRole)` | **SelectField** |
| `features/platform/components/roles/RoleEditorModal.tsx` | 195 | `<input>` | `<input type="checkbox" checked={updateTemplate} disabled={!templateId} onChange=` | **CheckboxField** |
| `features/platform/components/roles/TemplatesView.tsx` | 121 | `<select>` | `<select value={base} onChange={(e) => chooseBase(e.target.value as PlatformRole)` | **SelectField** |
| `features/platform/components/support/TicketsView.tsx` | 313 | `<button>` | `<button key={st} type="button" role="tab" aria-selected={statusFilter === st} cl` | **Button** |
| `features/platform/components/support/TicketsView.tsx` | 336 | `<select>` | `<select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>` | **SelectField** |
| `features/platform/components/support/TicketsView.tsx` | 348 | `<select>` | `<select value={categoryFilter} onChange={(e) => setCategoryFilter(e.target.value` | **SelectField** |
| `features/platform/components/tenants/TenantBrandingModal.tsx` | 143 | `<input>` | `<input id="branding-logo" type="file" accept="image/png,image/svg+xml" onChange=` | **FileDrop (or hidden native file input)** |
| `features/platform/components/tenants/TenantsView.tsx` | 106 | `<button>` | `<button ref={triggerRef} type="button" className="eds-button eds-button--seconda` | **Button** |
| `features/platform/components/tenants/TenantsView.tsx` | 148 | `<button>` | `<button role="menuitem" onClick={onClick} style={{ display: "block", width: "100` | **Button** |
| `features/platform/components/tenants/TenantsView.tsx` | 671 | `<button>` | `<button key={st} type="button" role="tab" aria-selected={status === st} classNam` | **Button** |
| `features/platform/components/tenants/TenantsView.tsx` | 689 | `<select>` | `<select value={status} onChange={(e) => setStatus(e.target.value)}>` | **SelectField** |
| `features/platform/components/tenants/TenantsView.tsx` | 844 | `<select>` | `<select id="tenant-plan-change-plan" value={planChangePlanId} onChange={(e) => {` | **SelectField** |
| `features/platform/components/tenants/TenantsView.tsx` | 864 | `<select>` | `<select id="tenant-plan-change-effective" value={planChangeEffective} onChange={` | **SelectField** |
| `features/platform/components/users/CreatePlatformUserModal.tsx` | 86 | `<select>` | `{/* Raw <select> kept because optgroup is not supported by DS SelectField */}` | **SelectField** |
| `features/platform/components/users/CreatePlatformUserModal.tsx` | 89 | `<select>` | `<select value={draft.roleChoice} onChange={(e) => setDraft({ ...draft, roleChoic` | **SelectField** |
| `features/platform/components/users/PlatformUsersView.tsx` | 94 | `<select>` | `<select value={choice} onChange={(e) => setChoice(e.target.value)}>` | **SelectField** |
| `features/platform/components/users/PlatformUsersView.tsx` | 321 | `<button>` | `<button key={st} type="button" role="tab" aria-selected={filter === st} classNam` | **Button** |
| `features/platform/components/users/PlatformUsersView.tsx` | 339 | `<select>` | `<select value={filter} onChange={(e) => setFilter(e.target.value)}>` | **SelectField** |
| `features/profile/components/ProfileAvatarSection.tsx` | 47 | `<input>` | `<input ref={fileInputRef} id="profile-photo-input" type="file" accept="image/jpe` | **FileDrop (or hidden native file input)** |
| `features/profile/components/ProfileDevicesTab.tsx` | 65 | `<button>` | `<button type="button" className="eds-link-button" onClick={retry}>Try again</but` | **Button** |
| `features/profile/components/ProfileHeader.tsx` | 46 | `<input>` | `<input ref={fileInputRef} id="profile-photo-input" type="file" accept="image/jpe` | **FileDrop (or hidden native file input)** |
| `features/profile/components/ProfileHeader.tsx` | 56 | `<button>` | `<button type="button" className="app-profile-hero__photo-btn" onClick={() => fil` | **Button** |
| `features/role-previews/components/RoleDirectoryTools.tsx` | 20 | `<input>` | `<input value={query} onChange={(e) => onQueryChange(e.target.value)}` | **TextField** |
| `features/role-previews/components/RoleDirectoryTools.tsx` | 26 | `<select>` | `<select value={level} onChange={(e) => onLevelChange(e.target.value)}` | **SelectField** |
| `features/self-service/pages/SelfServicePage.tsx` | 285 | `<button>` | `<button type="button" className="notif-bell__link" style={{ marginLeft: "var(--s` | **Button** |
| `features/staff/pages/StaffAppraisalPage.tsx` | 158 | `<input>` | `<input aria-label={`${item.label} score`} type="range" min="0" max="100" step="1` | **TextField** |
| `features/staff/pages/StaffAppraisalPage.tsx` | 161 | `<input>` | `<input aria-label={`${item.label} weight`} type="range" min="0" max="100" step="` | **TextField** |
| `features/staff/pages/StaffAppraisalPage.tsx` | 171 | `<select>` | `<label>Open cycle<select value={selectedCycle} onChange={(event) => setSelectedC` | **SelectField** |
| `features/staff/pages/StaffAppraisalPage.tsx` | 180 | `<select>` | `<label>Submission<select value={selectedSubmission} onChange={(event) => setSele` | **SelectField** |
| `features/staff/pages/StaffAppraisalPage.tsx` | 208 | `<select>` | `<label>Promotion readiness<select value={promotionReadiness} onChange={(event) =` | **SelectField** |
| `features/students/components/GuardianManagerModal.tsx` | 12 | `<select>` | `<label className="eds-field"><span>Relationship</span><select value={relationshi` | **SelectField** |
| `features/students/components/GuardianManagerModal.tsx` | 13 | `<input>` | `<label className="eds-field"><span><input type="checkbox" checked={primary} onCh` | **CheckboxField** |
| `features/students/pages/StudentDirectoryPage.tsx` | 34 | `<select>` | `<label className="eds-field"><span>Status</span><select value={filters.status} o` | **SelectField** |
| `features/students/pages/StudentDirectoryPage.tsx` | 60 | `<input>` | `<Panel title="Student master profile"><dl className="app-definition-grid">{Objec` | **TextField** |
| `features/students/pages/StudentDirectoryPage.tsx` | 67 | `<select>` | `function AddNoteForm({tenantId,studentId,onSaved}:{tenantId:string;studentId:str` | **SelectField** |
| `features/support/SupportSessionBanner.tsx` | 28 | `<button>` | `<button type="button" onClick={endSupportSession} style={{ border: "1px solid #f` | **Button** |
| `features/user-management/components/details/GuardianManagementPanel.tsx` | 301 | `<select>` | `<select value={relationship} onChange={(e) => setRelationship(e.target.value)}` | **SelectField** |
| `features/user-management/components/details/GuardianManagementPanel.tsx` | 320 | `<input>` | `<input type="checkbox" checked={isPrimary} onChange={(e) => setIsPrimary(e.targe` | **CheckboxField** |
| `features/user-management/components/details/GuardianManagementPanel.tsx` | 360 | `<textarea>` | `<textarea id="change-reason-input" required rows={3} value={changeReason} onChan` | **TextAreaField** |
| `features/user-management/components/details/GuardianManagementPanel.tsx` | 406 | `<input>` | `<input type="radio" name="decision" checked={decisionApproved} onChange={() => s` | **RadioField / design-system radio** |
| `features/user-management/components/details/GuardianManagementPanel.tsx` | 415 | `<input>` | `<input type="radio" name="decision" checked={!decisionApproved} onChange={() => ` | **RadioField / design-system radio** |
| `features/user-management/components/details/StudentStatusPanel.tsx` | 106 | `<select>` | `<select className="app-form__select" value={newStatus} onChange={(e) => setNewSt` | **SelectField** |
| `features/user-management/components/details/StudentStatusPanel.tsx` | 125 | `<textarea>` | `<textarea id="status-reason" required rows={3} className="app-form__textarea" va` | **TextAreaField** |
| `features/user-management/components/users/BulkUploadModal.tsx` | 158 | `<input>` | `<input id="csv-file-input" type="file" accept=".csv,text/csv" onChange={handleFi` | **FileDrop (or hidden native file input)** |
| `features/user-management/components/users/BulkUploadModal.tsx` | 181 | `<textarea>` | `<textarea id="csv-textarea" rows={4} value={csvContent} onChange={(e) => setCsvC` | **TextAreaField** |
| `features/user-management/components/users/CreatedCredentialsModal.tsx` | 87 | `<input>` | `<input id="created-temp-password" readOnly value={user.temporaryPassword} onFocu` | **TextField** |
| `features/user-management/pages/UserManagementPages.tsx` | 190 | `<button>` | `<button ref={triggerRef} type="button" className="eds-button eds-button--seconda` | **Button** |
| `features/user-management/pages/UserManagementPages.tsx` | 224 | `<button>` | `<button key={item.label} role="menuitem" onClick={item.onClick} style={{ display` | **Button** |
| `features/user-management/pages/UserManagementPages.tsx` | 954 | `<input>` | `<input id="temp-password-value" readOnly value={temporaryPassword ?? ""} onFocus` | **TextField** |
| `layout/AppLayout.tsx` | 442 | `<button>` | `? <button key={item.label} type="button" disabled className="app-nav__link is-di` | **Button** |
| `layout/AppLayout.tsx` | 450 | `<button>` | `<button type="button" className="app-sidebar__handle" onClick={toggleSidebar} ti` | **Button** |
| `layout/AppLayout.tsx` | 460 | `<button>` | `<button type="button" className="app-brand__logo-btn" onClick={toggleSidebar} ti` | **Button** |
| `layout/AppLayout.tsx` | 485 | `<button>` | `{navOverflow.above && <button type="button" className="app-nav__more app-nav__mo` | **Button** |
| `layout/AppLayout.tsx` | 486 | `<button>` | `{navOverflow.below && <button type="button" className="app-nav__more" tabIndex={` | **Button** |
| `layout/AppLayout.tsx` | 494 | `<button>` | `<button type="button" className="app-signout-icon" onClick={() => void signOut()` | **Button** |
| `layout/AppLayout.tsx` | 504 | `<button>` | `<button type="button" className="app-user-card__more" onClick={() => setUserMenu` | **Button** |
| `layout/AppLayout.tsx` | 512 | `<button>` | `<button type="button" className="app-user-card__item app-user-card__item--danger` | **Button** |
| `layout/AppLayout.tsx` | 529 | `<button>` | `<button ref={moreTriggerRef} type="button" className={`app-nav__link${isMoreActi` | **Button** |
| `layout/AppLayout.tsx` | 543 | `<button>` | `<header><h2>Menu</h2><button className="app-icon-button" onClick={closeMoreDrawe` | **Button** |

---

## 3. Hardcoded Colors

The following **181** color values bypass the design token palette defined in `app/src/design-system/tokens.css`:

| File | Line | Value Found | Context / Snippet | Suggested Design Token |
| :--- | :---: | :---: | :--- | :--- |
| `assets/Edu360Logo.tsx` | 212 | `#FFFFFF` | `fill="#FFFFFF"` | `--color-bg-surface / --p-neutral-0` |
| `assets/Edu360Logo.tsx` | 224 | `#FFFFFF` | `fill="#FFFFFF"` | `--color-bg-surface / --p-neutral-0` |
| `assets/Edu360Logo.tsx` | 234 | `#FFFFFF` | `stroke="#FFFFFF"` | `--color-bg-surface / --p-neutral-0` |
| `assets/Edu360Logo.tsx` | 245 | `#FFFFFF` | `fill="#FFFFFF"` | `--color-bg-surface / --p-neutral-0` |
| `features/institution-admin/components/academic/UnlockTermModal.tsx` | 60 | `#ccc` | `border: "1px solid var(--color-border, #ccc)",` | `Design token (--color-*)` |
| `features/institution-admin/components/institutions/BlockRequestModal.tsx` | 83 | `#ccc` | `border: "1px solid var(--color-border, #ccc)",` | `Design token (--color-*)` |
| `features/institution-admin/components/institutions/GovernanceDecisionModal.tsx` | 110 | `#ccc` | `border: "1px solid var(--color-border, #ccc)",` | `Design token (--color-*)` |
| `features/institution-admin/components/institutions/PrincipalLeaveApproverModal.tsx` | 88 | `#ccc` | `border: "1px solid var(--color-border, #ccc)",` | `Design token (--color-*)` |
| `features/institution-admin/components/institutions/ViceChancellorModal.tsx` | 85 | `#ccc` | `border: "1px solid var(--color-border, #ccc)",` | `Design token (--color-*)` |
| `features/institution-admin/components/roles/CreateCustomRoleModal.tsx` | 126 | `#ccc` | `border: "1px solid var(--color-border, #ccc)",` | `Design token (--color-*)` |
| `features/institution-admin/components/roles/CreateCustomRoleModal.tsx` | 146 | `#ccc` | `border: "1px solid var(--color-border, #ccc)",` | `Design token (--color-*)` |
| `features/institution-admin/components/roles/RolldownOverrideModal.tsx` | 133 | `#ccc` | `border: "1px solid var(--color-border, #ccc)",` | `Design token (--color-*)` |
| `features/institution-admin/components/roles/RolldownOverrideModal.tsx` | 151 | `#ccc` | `border: "1px solid var(--color-border, #ccc)",` | `Design token (--color-*)` |
| `features/institution-admin/components/roles/RolldownOverrideModal.tsx` | 192 | `#ccc` | `border: "1px solid var(--color-border, #ccc)",` | `Design token (--color-*)` |
| `features/institution-admin/pages/InstitutionSetupPages.tsx` | 2220 | `rgba(15, 23, 42, 0.55)` | `<div role="presentation" style={{ position: "fixed", inset: 0, zIndex: 1000, dis` | `Design token (--color-overlay / alpha token)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 288 | `#25D366` | `background: "#25D366",` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 289 | `#fff` | `color: "#fff",` | `--color-bg-surface / --p-neutral-0` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 293 | `rgba(37,211,102,0.35)` | `boxShadow: "0 2px 6px rgba(37,211,102,0.35)",` | `Design token (--color-overlay / alpha token)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 324 | `#0088cc` | `background: "#0088cc",` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 325 | `#fff` | `color: "#fff",` | `--color-bg-surface / --p-neutral-0` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 329 | `rgba(0,136,204,0.35)` | `boxShadow: "0 2px 6px rgba(0,136,204,0.35)",` | `Design token (--color-overlay / alpha token)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 362 | `#6366f1` | `background: "#6366f1",` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 363 | `#fff` | `color: "#fff",` | `--color-bg-surface / --p-neutral-0` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 367 | `rgba(99,102,241,0.35)` | `boxShadow: "0 2px 6px rgba(99,102,241,0.35)",` | `Design token (--color-overlay / alpha token)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 401 | `#e11d48` | `background: "#e11d48",` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 402 | `#fff` | `color: "#fff",` | `--color-bg-surface / --p-neutral-0` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 406 | `rgba(225,29,72,0.35)` | `boxShadow: "0 2px 6px rgba(225,29,72,0.35)",` | `Design token (--color-overlay / alpha token)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 438 | `#059669` | `background: "#059669",` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 439 | `#fff` | `color: "#fff",` | `--color-bg-surface / --p-neutral-0` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 443 | `rgba(5,150,105,0.35)` | `boxShadow: "0 2px 6px rgba(5,150,105,0.35)",` | `Design token (--color-overlay / alpha token)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 474 | `#0d9488` | `background: "#0d9488",` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 475 | `#fff` | `color: "#fff",` | `--color-bg-surface / --p-neutral-0` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 479 | `rgba(13,148,136,0.35)` | `boxShadow: "0 2px 6px rgba(13,148,136,0.35)",` | `Design token (--color-overlay / alpha token)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 510 | `#10b981` | `background: copied ? "#10b981" : "#475569",` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 510 | `#475569` | `background: copied ? "#10b981" : "#475569",` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 511 | `#fff` | `color: "#fff",` | `--color-bg-surface / --p-neutral-0` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 515 | `rgba(71,85,105,0.35)` | `boxShadow: "0 2px 6px rgba(71,85,105,0.35)",` | `Design token (--color-overlay / alpha token)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 548 | `#0284c7` | `background: "#0284c7",` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 549 | `#fff` | `color: "#fff",` | `--color-bg-surface / --p-neutral-0` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 553 | `rgba(2,132,199,0.35)` | `boxShadow: "0 2px 6px rgba(2,132,199,0.35)",` | `Design token (--color-overlay / alpha token)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 567 | `#ffffff` | `background: "#ffffff",` | `--color-bg-surface / --p-neutral-0` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 568 | `#0f172a` | `color: "#0f172a",` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 570 | `#cbd5e1` | `border: "1px solid #cbd5e1",` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 578 | `#0f172a` | `<div style={{ display: "flex", justifyContent: "space-between", alignItems: "cen` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 584 | `#0f172a` | `<strong style={{ fontSize: "16px", color: "#0f172a", display: "block", letterSpa` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 585 | `#059669` | `<span style={{ fontSize: "10.5px", fontWeight: "700", color: "#059669", letterSp` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 588 | `#64748b` | `<span style={{ fontSize: "10px", color: "#64748b" }}>Higher Education Academic C` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 592 | `#059669` | `<span style={{ fontSize: "11px", fontWeight: "bold", color: "#059669", textTrans` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 595 | `#475569` | `<div style={{ fontSize: "11px", color: "#475569" }}>#{currentEstimate.referenceN` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 603 | `#f0fdf4` | `background: "#f0fdf4",` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 604 | `#bbf7d0` | `border: "1px solid #bbf7d0",` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 613 | `#dcfce7` | `background: "#dcfce7",` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 615 | `#bbf7d0` | `borderBottom: "1px solid #bbf7d0",` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 621 | `#047857` | `<span style={{ color: "#047857", fontWeight: "700", fontSize: "10.5px" }}>` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 625 | `#059669` | `<span style={{ color: "#059669", fontWeight: "600", fontSize: "10px" }}>` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 633 | `#64748b` | `<span style={{ color: "#64748b", fontSize: "10px", display: "block" }}>LICENSED ` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 634 | `#0f172a` | `<strong style={{ color: "#0f172a", fontSize: "13px" }}>{currentEstimate.scope.pl` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 643 | `#dcfce7` | `borderTop: "1px solid #dcfce7",` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 648 | `#64748b` | `<span style={{ color: "#64748b", fontSize: "9.5px", display: "block" }}>STUDENTS` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 649 | `#047857` | `<strong style={{ color: "#047857", fontSize: "12px" }}>` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 656 | `#64748b` | `<span style={{ color: "#64748b", fontSize: "9.5px", display: "block" }}>COLLEGES` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 657 | `#047857` | `<strong style={{ color: "#047857", fontSize: "12px" }}>` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 664 | `#64748b` | `<span style={{ color: "#64748b", fontSize: "9.5px", display: "block" }}>SCHOOLS ` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 665 | `#047857` | `<strong style={{ color: "#047857", fontSize: "12px" }}>` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 672 | `#64748b` | `<span style={{ color: "#64748b", fontSize: "9.5px", display: "block" }}>FACULTY ` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 673 | `#047857` | `<strong style={{ color: "#047857", fontSize: "12px" }}>` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 684 | `#f8fafc` | `<div style={{ display: "flex", justifyContent: "space-between", marginBottom: "1` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 686 | `#64748b` | `<span style={{ fontSize: "10px", color: "#64748b", textTransform: "uppercase", d` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 687 | `#0f172a` | `<strong style={{ color: "#0f172a" }}>{currentEstimate.institutionName}</strong>` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 688 | `#475569` | `{currentEstimate.contactPerson && <div style={{ color: "#475569" }}>Attn: {curre` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 691 | `#64748b` | `<span style={{ fontSize: "10px", color: "#64748b", textTransform: "uppercase", d` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 692 | `#059669` | `<strong style={{ fontSize: "15px", color: "#059669" }}>` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 701 | `#f1f5f9` | `<div key={i} style={{ display: "flex", justifyContent: "space-between", padding:` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 702 | `#334155` | `<span style={{ color: "#334155" }}>{l.description}</span>` | `Design token (--color-*)` |
| `features/platform/components/billing/BillingEstimateShareModal.tsx` | 703 | `#0f172a` | `<strong style={{ color: "#0f172a" }}>` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 91 | `#047857` | `<stop offset="0%" stop-color="#047857"/>` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 92 | `#059669` | `<stop offset="55%" stop-color="#059669"/>` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 93 | `#10b981` | `<stop offset="100%" stop-color="#10b981"/>` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 95 | `#047857` | `<linearGradient id="p1" x1="0" y1="0" x2="1" y2="1"><stop offset="0%" stop-color` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 95 | `#064e3b` | `<linearGradient id="p1" x1="0" y1="0" x2="1" y2="1"><stop offset="0%" stop-color` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 96 | `#059669` | `<linearGradient id="p2" x1="0" y1="0" x2="1" y2="1"><stop offset="0%" stop-color` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 96 | `#047857` | `<linearGradient id="p2" x1="0" y1="0" x2="1" y2="1"><stop offset="0%" stop-color` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 97 | `#10b981` | `<linearGradient id="p3" x1="0" y1="0" x2="1" y2="1"><stop offset="0%" stop-color` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 97 | `#059669` | `<linearGradient id="p3" x1="0" y1="0" x2="1" y2="1"><stop offset="0%" stop-color` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 107 | `#FFFFFF` | `<g fill="#FFFFFF" clip-path="url(#cp1)">` | `--color-bg-surface / --p-neutral-0` |
| `features/platform/components/billing/estimateExportUtils.ts` | 115 | `#FFFFFF` | `<g fill="#FFFFFF" clip-path="url(#cp2)">` | `--color-bg-surface / --p-neutral-0` |
| `features/platform/components/billing/estimateExportUtils.ts` | 120 | `#FFFFFF` | `<g fill="none" stroke="#FFFFFF" stroke-width="4.2" stroke-linecap="round" stroke` | `--color-bg-surface / --p-neutral-0` |
| `features/platform/components/billing/estimateExportUtils.ts` | 124 | `#FFFFFF` | `<g fill="#FFFFFF" clip-path="url(#cp3)">` | `--color-bg-surface / --p-neutral-0` |
| `features/platform/components/billing/estimateExportUtils.ts` | 129 | `#047857` | `<path d="M151 292 C178 271 210 271 239 295 C249 303 257 314 263 324 C278 299 299` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 130 | `#065f46` | `<path d="M144 340 C182 320 218 323 263 351 C225 338 190 338 155 355 C145 360 137` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 131 | `#047857` | `<path d="M263 351 C301 322 343 320 394 324 C345 330 302 337 263 365 C226 337 190` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 132 | `#047857` | `<rect fill="#047857" x="374" y="81" width="25" height="25" rx="6" />` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 133 | `#047857` | `<rect fill="#047857" x="399" y="62" width="18" height="18" rx="5" />` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 134 | `#059669` | `<rect fill="#059669" x="417" y="87" width="22" height="22" rx="6" />` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 135 | `#10b981` | `<rect fill="#10b981" x="395" y="111" width="22" height="22" rx="6" />` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 283 | `#ffffff` | `ctx.fillStyle = "#ffffff";` | `--color-bg-surface / --p-neutral-0` |
| `features/platform/components/billing/estimateExportUtils.ts` | 288 | `#047857` | `grad.addColorStop(0, "#047857");` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 289 | `#10b981` | `grad.addColorStop(0.5, "#10b981");` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 290 | `#34d399` | `grad.addColorStop(1, "#34d399");` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 295 | `#e2e8f0` | `ctx.strokeStyle = "#e2e8f0";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 310 | `#059669` | `ctx.fillStyle = "#059669";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 314 | `#ffffff` | `ctx.fillStyle = "#ffffff";` | `--color-bg-surface / --p-neutral-0` |
| `features/platform/components/billing/estimateExportUtils.ts` | 321 | `#0f172a` | `ctx.fillStyle = "#0f172a";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 325 | `#059669` | `ctx.fillStyle = "#059669";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 329 | `#64748b` | `ctx.fillStyle = "#64748b";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 336 | `#ecfdf5` | `ctx.fillStyle = "#ecfdf5";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 337 | `#a7f3d0` | `ctx.strokeStyle = "#a7f3d0";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 344 | `#047857` | `ctx.fillStyle = "#047857";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 351 | `#0f172a` | `ctx.fillStyle = "#0f172a";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 355 | `#64748b` | `ctx.fillStyle = "#64748b";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 360 | `#059669` | `ctx.fillStyle = "#059669";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 367 | `#e2e8f0` | `ctx.strokeStyle = "#e2e8f0";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 380 | `#f8fafc` | `ctx.fillStyle = "#f8fafc";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 381 | `#e2e8f0` | `ctx.strokeStyle = "#e2e8f0";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 388 | `#059669` | `ctx.fillStyle = "#059669";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 392 | `#1e293b` | `ctx.fillStyle = "#1e293b";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 396 | `#64748b` | `ctx.fillStyle = "#64748b";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 404 | `#f0fdf4` | `ctx.fillStyle = "#f0fdf4";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 405 | `#bbf7d0` | `ctx.strokeStyle = "#bbf7d0";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 411 | `#059669` | `ctx.fillStyle = "#059669";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 415 | `#065f46` | `ctx.fillStyle = "#065f46";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 419 | `#334155` | `ctx.fillStyle = "#334155";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 424 | `#64748b` | `ctx.fillStyle = "#64748b";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 436 | `#ffffff` | `ctx.fillStyle = "#ffffff";` | `--color-bg-surface / --p-neutral-0` |
| `features/platform/components/billing/estimateExportUtils.ts` | 437 | `#10b981` | `ctx.strokeStyle = "#10b981";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 445 | `#f0fdf4` | `ctx.fillStyle = "#f0fdf4";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 450 | `#047857` | `ctx.fillStyle = "#047857";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 464 | `#059669` | `ctx.fillStyle = "#059669";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 470 | `#64748b` | `ctx.fillStyle = "#64748b";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 474 | `#0f172a` | `ctx.fillStyle = "#0f172a";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 479 | `#e2e8f0` | `ctx.strokeStyle = "#e2e8f0";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 497 | `#64748b` | `ctx.fillStyle = "#64748b";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 501 | `#047857` | `ctx.fillStyle = "#047857";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 506 | `#f1f5f9` | `ctx.strokeStyle = "#f1f5f9";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 519 | `#0f172a` | `ctx.fillStyle = "#0f172a";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 524 | `#ffffff` | `ctx.fillStyle = "#ffffff";` | `--color-bg-surface / --p-neutral-0` |
| `features/platform/components/billing/estimateExportUtils.ts` | 538 | `#f8fafc` | `ctx.fillStyle = idx % 2 === 1 ? "#f8fafc" : "#ffffff";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 538 | `#ffffff` | `ctx.fillStyle = idx % 2 === 1 ? "#f8fafc" : "#ffffff";` | `--color-bg-surface / --p-neutral-0` |
| `features/platform/components/billing/estimateExportUtils.ts` | 540 | `#e2e8f0` | `ctx.strokeStyle = "#e2e8f0";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 544 | `#64748b` | `ctx.fillStyle = "#64748b";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 548 | `#0f172a` | `ctx.fillStyle = "#0f172a";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 552 | `#64748b` | `ctx.fillStyle = "#64748b";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 558 | `#0f172a` | `ctx.fillStyle = "#0f172a";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 571 | `#f8fafc` | `ctx.fillStyle = "#f8fafc";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 572 | `#e2e8f0` | `ctx.strokeStyle = "#e2e8f0";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 578 | `#0369a1` | `ctx.fillStyle = "#0369a1";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 582 | `#334155` | `ctx.fillStyle = "#334155";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 587 | `#059669` | `ctx.fillStyle = "#059669";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 591 | `#f0fdf4` | `ctx.fillStyle = "#f0fdf4";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 592 | `#86efac` | `ctx.strokeStyle = "#86efac";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 598 | `#065f46` | `ctx.fillStyle = "#065f46";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 603 | `#dc2626` | `ctx.fillStyle = "#dc2626";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 608 | `#047857` | `ctx.fillStyle = "#047857";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 613 | `#ffffff` | `ctx.fillStyle = "#ffffff";` | `--color-bg-surface / --p-neutral-0` |
| `features/platform/components/billing/estimateExportUtils.ts` | 624 | `#f8fafc` | `ctx.fillStyle = "#f8fafc";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 625 | `#e2e8f0` | `ctx.strokeStyle = "#e2e8f0";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 631 | `#0f172a` | `ctx.fillStyle = "#0f172a";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 638 | `#64748b` | `ctx.fillStyle = "#64748b";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 648 | `#f0fdf4` | `ctx.fillStyle = "#f0fdf4";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 649 | `#059669` | `ctx.strokeStyle = "#059669";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 656 | `#059669` | `ctx.fillStyle = "#059669";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 660 | `#0f172a` | `ctx.fillStyle = "#0f172a";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 667 | `#e2e8f0` | `ctx.strokeStyle = "#e2e8f0";` | `Design token (--color-*)` |
| `features/platform/components/billing/estimateExportUtils.ts` | 673 | `#94a3b8` | `ctx.fillStyle = "#94a3b8";` | `Design token (--color-*)` |
| `features/platform/components/reports/ReportsView.tsx` | 587 | `#fee2e2` | `: "var(--color-bg-danger-subtle, #fee2e2)",` | `Design token (--color-*)` |
| `features/platform/components/reports/ReportsView.tsx` | 593 | `#b91c1c` | `: "var(--color-text-danger, #b91c1c)",` | `Design token (--color-*)` |
| `features/platform/components/reports/ReportsView.tsx` | 956 | `#fee2e2` | `? "var(--color-bg-danger-subtle, #fee2e2)"` | `Design token (--color-*)` |
| `features/platform/components/reports/ReportsView.tsx` | 962 | `#b91c1c` | `? "var(--color-text-danger, #b91c1c)"` | `Design token (--color-*)` |
| `features/platform/components/reports/ReportsView.tsx` | 1141 | `#fee2e2` | `? "var(--color-bg-danger-subtle, #fee2e2)"` | `Design token (--color-*)` |
| `features/platform/components/reports/ReportsView.tsx` | 1147 | `#b91c1c` | `? "var(--color-text-danger, #b91c1c)"` | `Design token (--color-*)` |
| `features/platform/components/tenants/TenantBrandingModal.tsx` | 73 | `#1A2B3C` | `const colorError = (value: string) => (value && !HEX.test(value) ? "Use a colour` | `Design token (--color-*)` |
| `features/platform/components/tenants/TenantBrandingModal.tsx` | 148 | `#1A2B3C` | `<TextField id="branding-primary" label="Primary colour" value={primary} placehol` | `Design token (--color-*)` |
| `features/platform/components/tenants/TenantBrandingModal.tsx` | 152 | `#D4A017` | `<TextField id="branding-accent" label="Accent colour" value={accent} placeholder` | `Design token (--color-*)` |
| `features/support/SupportSessionBanner.tsx` | 16 | `#991b1b` | `background: "#991b1b",` | `Design token (--color-*)` |
| `features/support/SupportSessionBanner.tsx` | 17 | `#ffffff` | `color: "#ffffff",` | `--color-bg-surface / --p-neutral-0` |
| `features/user-management/components/details/GuardianManagementPanel.tsx` | 307 | `#ccc` | `border: "1px solid var(--color-border, #ccc)",` | `Design token (--color-*)` |
| `features/user-management/components/details/GuardianManagementPanel.tsx` | 349 | `#666` | `<p style={{ fontSize: "0.875rem", color: "var(--color-text-secondary, #666)", ma` | `Design token (--color-*)` |
| `features/user-management/components/details/GuardianManagementPanel.tsx` | 370 | `#ccc` | `border: "1px solid var(--color-border, #ccc)",` | `Design token (--color-*)` |
| `features/user-management/components/details/GuardianManagementPanel.tsx` | 396 | `#666` | `<p style={{ fontSize: "0.875rem", color: "var(--color-text-secondary, #666)", ma` | `Design token (--color-*)` |
| `features/user-management/components/users/BulkUploadModal.tsx` | 279 | `#64748b` | `<td style={{ color: "var(--color-text-secondary, #64748b)" }}>{err.errorMessage}` | `Design token (--color-*)` |
| `features/user-management/components/users/CreateUserModal.tsx` | 151 | `#64748b` | `color: "var(--color-text-secondary, #64748b)",` | `Design token (--color-*)` |

---

## 4. Hardcoded Fonts

The following **16** locations declare custom `font-family` or inline `fontFamily` bypassing typography tokens (`--font-display`, `--font-body`, `--font-mono`):

| File | Line | Declaration | Recommended Token Fix |
| :--- | :---: | :--- | :--- |
| `assets/Edu360Logo.tsx` | 125 | `font-family: Inter, Montserrat, Arial, sans-serif;` | `var(--font-display)`, `var(--font-body)`, or `var(--font-mono)` |
| `assets/Edu360Logo.tsx` | 130 | `font-family: Inter, Montserrat, Arial, sans-serif;` | `var(--font-display)`, `var(--font-body)`, or `var(--font-mono)` |
| `features/institution-admin/components/academic/AllocateSubjectModal.tsx` | 75 | `style={{ fontFamily: "monospace" }}` | `var(--font-display)`, `var(--font-body)`, or `var(--font-mono)` |
| `features/institution-admin/components/academic/AllocateSubjectModal.tsx` | 86 | `style={{ fontFamily: "monospace" }}` | `var(--font-display)`, `var(--font-body)`, or `var(--font-mono)` |
| `features/institution-admin/components/academic/AllocateSubjectModal.tsx` | 96 | `style={{ fontFamily: "monospace" }}` | `var(--font-display)`, `var(--font-body)`, or `var(--font-mono)` |
| `features/institution-admin/components/academic/AllocateSubjectModal.tsx` | 107 | `style={{ fontFamily: "monospace" }}` | `var(--font-display)`, `var(--font-body)`, or `var(--font-mono)` |
| `features/institution-admin/components/academic/PromotionRunModal.tsx` | 72 | `style={{ fontFamily: "monospace" }}` | `var(--font-display)`, `var(--font-body)`, or `var(--font-mono)` |
| `features/institution-admin/components/academic/PromotionRunModal.tsx` | 83 | `style={{ fontFamily: "monospace" }}` | `var(--font-display)`, `var(--font-body)`, or `var(--font-mono)` |
| `features/institution-admin/components/academic/PromotionRunModal.tsx` | 94 | `style={{ fontFamily: "monospace" }}` | `var(--font-display)`, `var(--font-body)`, or `var(--font-mono)` |
| `features/institution-admin/components/academic/SetProgramRulesModal.tsx` | 62 | `style={{ fontFamily: "monospace" }}` | `var(--font-display)`, `var(--font-body)`, or `var(--font-mono)` |
| `features/institution-admin/components/academic/UnlockTermModal.tsx` | 61 | `fontFamily: "inherit",` | `var(--font-display)`, `var(--font-body)`, or `var(--font-mono)` |
| `features/institution-admin/pages/InstitutionSetupPages.tsx` | 3063 | `style={{ fontFamily: "monospace" }}` | `var(--font-display)`, `var(--font-body)`, or `var(--font-mono)` |
| `features/institution-admin/pages/InstitutionSetupPages.tsx` | 3124 | `style={{ fontFamily: "monospace", fontSize: "0.875rem" }}` | `var(--font-display)`, `var(--font-body)`, or `var(--font-mono)` |
| `features/user-management/components/users/BulkUploadModal.tsx` | 190 | `fontFamily: "monospace",` | `var(--font-display)`, `var(--font-body)`, or `var(--font-mono)` |
| `features/user-management/components/users/CreatedCredentialsModal.tsx` | 93 | `fontFamily: "monospace",` | `var(--font-display)`, `var(--font-body)`, or `var(--font-mono)` |
| `features/user-management/pages/UserManagementPages.tsx` | 960 | `fontFamily: "monospace",` | `var(--font-display)`, `var(--font-body)`, or `var(--font-mono)` |

---

## 5. Hardcoded Spacing and Sizing

A total of **490** inline `style={{ ... }}` declarations inject hardcoded dimensions, padding, margins, or gaps rather than leveraging token-based utility classes or design-system container spacing (`--space-1` through `--space-16`).

### Key Clusters:
1. **Institution Setup Pages (`features/institution-admin/pages/InstitutionSetupPages.tsx`)**: 38 inline styles with magic px margins/paddings.
2. **Guardian Management Panel (`features/user-management/components/details/GuardianManagementPanel.tsx`)**: 27 inline styles controlling label, input, and button spacing.
3. **Invoices View (`features/platform/components/billing/InvoicesView.tsx`)**: 21 inline styles setting flex layouts, margins, and min-widths.
4. **Institution Admin Modals (`PrincipalLeaveApproverModal`, `ViceChancellorModal`, `CreateCustomRoleModal`, `RolldownOverrideModal`, `AddDepartmentModal`, `AddAcademicSessionModal`)**: Hand-rolled form field wrappers with `style={{ marginBottom: "var(--space-3)" }}` and custom select styles.
5. **Billing Estimate Share Modal (`BillingEstimateShareModal.tsx`)**: 15 inline styles setting dimensions, paddings, and absolute positioning.

*Recommendation:* Replace inline `style={{ marginBottom: ... }}` and margin wrappers with standard CSS utility classes (`gap-*`, `mb-*`, `space-y-*`) and let design system form fields (`TextField`, `SelectField`) handle their own label/error/margin spacing internally.

---

## 6. Duplicated Custom Components & Patterns

| Pattern | Files Implementing Pattern | Similarity / Description | Coverage & Recommendation |
| :--- | :--- | :--- | :--- |
| **EmptyState Duplication** | `app/src/components/feedback/EmptyState.tsx` vs `app/src/design-system/components/EmptyState.tsx` | `components/feedback/EmptyState.tsx` is an older, duplicate implementation using `h3` instead of `h2` and lacking icon support | **Replace with existing component**: Delete `components/feedback/EmptyState.tsx` and re-export or alias to `design-system/components/EmptyState.tsx`. |
| **Notification Popover Panel** | `features/notifications/components/NotificationBell.tsx`, `features/platform/components/notifications/PlatformNotificationBell.tsx` | Both files copy-paste identical portal popover markup (`.notif-bell__backdrop`, `.notif-bell__panel`, header, "Mark all read" button, close icon, scrollable list) | **Candidate for new / shared component**: Refactor into a single shared `NotificationPopover` or generic `Popover` in `design-system/components/`. |
| **Custom Modal Wrapper** | `features/students/components/GuardianManagerModal.tsx` | Implements its own backdrop and dialog shell using `<div className="fixed inset-0 ...">` instead of the design system `Modal` | **Replace with existing component**: Migrate to `Modal` (`app/src/design-system/components/Modal.tsx`). |
| **Hand-Rolled Form Field & Label Pattern** | 7+ institution admin & user modals (`PrincipalLeaveApproverModal`, `ViceChancellorModal`, `CreateCustomRoleModal`, `RolldownOverrideModal`, `BulkUploadModal`, etc.) | Repeated pattern of `<div style={{ marginBottom }}> <label style={{ display: 'block', fontWeight: 600 }}> ... </label> <select style={{ border: '1px solid #ccc' }}>` | **Replace with existing component**: Replace with `SelectField` and `TextAreaField`, which encapsulate label, error, and tokenized border. |
| **Temporary Credential Display with Copy Button** | `features/user-management/components/users/CreatedCredentialsModal.tsx` | Read-only monospace input with copy button, label, and security note | **Candidate for new component**: Create a reusable `CopyableCredential` / `TokenField` component in design system. |
| **Shell Navigation / Drawer Buttons** | `layout/AppLayout.tsx` | Uses 10 raw `<button>` tags for sidebar toggles, logo button, coming-soon items, user menu trigger, sign-out button, and bottom mobile navigation bar | **Replace / Keep as Shell Primitives**: Standardize interactive states (hover/focus-visible) or utilize `Button` with `variant="ghost"`. |

---

## 7. Missing Interaction States

A total of **154** interactive elements lack standard accessible interaction states:

### Group 1: Raw `<select>` elements missing custom focus rings and keyboard states (48 instances)
- Found across: `PersonPicker.tsx`, `SectionPicker.tsx`, `StatusToggleRoster.tsx`, `SubjectAllocationPicker.tsx`, `RoleSwitcher.tsx`, `ApplicantPortalPage.tsx`, `InstitutionAdminPages.tsx`, all institution modals, `PlatformUsersView.tsx`, `StudentDirectoryPage.tsx`, `GuardianManagementPanel.tsx`, `StudentStatusPanel.tsx`.
- **Fix:** Replacing with `SelectField` automatically provides `:focus-visible`, hover border transition, and disabled state styling.

### Group 2: Raw `<button>` elements missing hover / focus-visible / disabled states (56 instances)
- Found across: `WeekGrid.tsx`, `NotificationBell.tsx`, `AcademicSessionDetailPage.tsx`, `StaffRecordsPage.tsx`, `PlatformNotificationBell.tsx`, `InvoicesView.tsx`, `TicketsView.tsx`, `TenantsView.tsx`, `SelfServicePage.tsx`, `SupportSessionBanner.tsx`, `AppLayout.tsx`.
- **Fix:** Replacing with design system `Button` (or `LinkButton`) provides full variant styling, focus ring, hover brightness, and loading spinner state.

### Group 3: Raw `<input>` elements missing focus ring / border transition (50 instances)
- Found across: `ApplicantPortalPage.tsx`, `BillingEstimateShareModal.tsx`, `AuditLogView.tsx`, `RoleDirectoryTools.tsx`, `StaffAppraisalPage.tsx`, `StudentDirectoryPage.tsx`, `CreatedCredentialsModal.tsx`.
- **Fix:** Replacing with `TextField` / `SearchBox` / `CheckboxField`.

---

## 8. Prioritized Fix Plan for Phase 2

We propose breaking Phase 2 into **4 sequential, testable PR-sized increments**:

### **Phase 2.1 — Modal & Form Field Normalization (Highest Impact, Low Blast Radius)**
- **Scope:** Replace all raw `<select>`, `<input>`, and `<textarea>` elements inside modals and detail forms with `SelectField`, `TextField`, `TextAreaField`, and `CheckboxField`.
- **Target Files:**
  - `features/institution-admin/components/institutions/PrincipalLeaveApproverModal.tsx`
  - `features/institution-admin/components/institutions/ViceChancellorModal.tsx`
  - `features/institution-admin/components/roles/CreateCustomRoleModal.tsx`
  - `features/institution-admin/components/roles/RolldownOverrideModal.tsx`
  - `features/user-management/components/users/BulkUploadModal.tsx`
  - `features/user-management/components/users/CreatedCredentialsModal.tsx`
  - `features/user-management/components/details/GuardianManagementPanel.tsx`
  - `features/user-management/components/details/StudentStatusPanel.tsx`
  - `features/students/components/GuardianManagerModal.tsx` (migrate to DS `Modal`)
- **Risk:** Low. Form state, validation logic, and submit handlers remain untouched; only JSX markup and props change.

### **Phase 2.2 — Notification Bell & Feedback Component Unification**
- **Scope:** 
  1. Remove legacy duplicate `components/feedback/EmptyState.tsx` in favor of `design-system/components/EmptyState.tsx`.
  2. Unify `NotificationBell.tsx` and `PlatformNotificationBell.tsx` to share a common design system popover structure and replace raw buttons with `Button` / `Icon` buttons.
- **Risk:** Low to Moderate. Ensure notification click and mark-as-read events continue to fire as expected.

### **Phase 2.3 — Common Pickers & Table Action Controls**
- **Scope:**
  - Standardize raw `<select>` controls in common pickers: `PersonPicker.tsx`, `SectionPicker.tsx`, `StatusToggleRoster.tsx`, `SubjectAllocationPicker.tsx`, `RoleSwitcher.tsx`, and `StudentDirectoryPage.tsx`.
  - Replace raw table action `<button>` tags in `InvoicesView.tsx`, `TenantsView.tsx`, `TicketsView.tsx`, and `StaffRecordsPage.tsx` with design system `Button` (`variant="secondary"` / `variant="ghost"`).
- **Risk:** Low.

### **Phase 2.4 — Color Token Clean-Up & Inline Style Refactoring**
- **Scope:**
  - Replace hardcoded hex colors (`#25D366`, `#ccc`, `#64748b`, `#991b1b`) and arbitrary Tailwind color utilities with CSS tokens (`var(--color-status-*)`, `var(--color-border-divider)`, etc.).
  - Clean up inline fonts in `BulkUploadModal.tsx` and `CreatedCredentialsModal.tsx` to use `var(--font-mono)`.
  - Strip redundant inline `style={{ marginBottom: ... }}` and migrate to Tailwind token spacing classes.
- **Risk:** Very low (visual polish).

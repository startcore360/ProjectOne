import { chromium } from "playwright";
import fs from "node:fs";
import path from "node:path";

const SCREENSHOTS_DIR = path.resolve("./docs/screenshots");
const OUTPUT_PDF = path.resolve("./docs/EDU360_Complete_Platform_Manual.pdf");

// Helper to get base64 image
function getBase64Image(filename) {
  const fullPath = path.join(SCREENSHOTS_DIR, filename);
  if (fs.existsSync(fullPath)) {
    const data = fs.readFileSync(fullPath).toString("base64");
    return `data:image/png;base64,${data}`;
  }
  return "";
}

console.log("Loading screenshots...");
const images = {
  login: getBase64Image("login-light-1440x900.png"),
  overview: getBase64Image("platform-light-1440x900.png"),
  tenants: getBase64Image("platform_tenants-light-1440x900.png"),
  plans: getBase64Image("platform_plans-light-1440x900.png"),
  invoices: getBase64Image("platform_invoices-light-1440x900.png"),
  tickets: getBase64Image("platform_tickets-light-1440x900.png"),
  support: getBase64Image("platform_support-light-1440x900.png"),
  users: getBase64Image("platform_users-light-1440x900.png"),
  roles: getBase64Image("platform_roles-light-1440x900.png"),
  templates: getBase64Image("platform_role-templates-light-1440x900.png"),
  reports: getBase64Image("platform_reports-light-1440x900.png"),
  releases: getBase64Image("platform_releases-light-1440x900.png"),
  security: getBase64Image("platform_security-policy-light-1440x900.png"),
  audit: getBase64Image("platform_audit-light-1440x900.png"),
  usage: getBase64Image("platform_usage-counts-light-1440x900.png"),
  backup: getBase64Image("platform_backup-status-light-1440x900.png"),
  integrations: getBase64Image("platform_integration-types-light-1440x900.png"),
  dataProtection: getBase64Image("platform_data-protection-light-1440x900.png"),
};

console.log("Generating HTML document...");

const htmlContent = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>EDU360 Complete Platform Manual</title>
<style>
  @page {
    size: A4;
    margin: 18mm 14mm 20mm 14mm;
  }
  body {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
    color: #1e293b;
    line-height: 1.55;
    font-size: 11pt;
    margin: 0;
    padding: 0;
  }
  .page-break {
    page-break-before: always;
  }
  .avoid-break {
    page-break-inside: avoid;
  }
  
  /* Cover Page */
  .cover {
    height: 100vh;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    text-align: center;
    padding: 40px;
    box-sizing: border-box;
  }
  .cover-badge {
    background: #0284c7;
    color: white;
    font-weight: 750;
    font-size: 11pt;
    padding: 6px 18px;
    border-radius: 20px;
    text-transform: uppercase;
    letter-spacing: 1.5px;
    margin-bottom: 24px;
  }
  .cover-title {
    font-size: 32pt;
    font-weight: 800;
    color: #0f172a;
    margin: 0 0 12px 0;
    line-height: 1.15;
  }
  .cover-subtitle {
    font-size: 16pt;
    color: #475569;
    max-width: 650px;
    margin: 0 0 40px 0;
    line-height: 1.4;
  }
  .cover-meta {
    font-size: 10pt;
    color: #64748b;
    border-top: 1px solid #cbd5e1;
    padding-top: 20px;
    width: 80%;
    display: flex;
    justify-content: space-around;
  }

  /* Typography */
  h1 {
    font-size: 22pt;
    color: #0f172a;
    border-bottom: 2px solid #0284c7;
    padding-bottom: 8px;
    margin-top: 0;
    margin-bottom: 16px;
    page-break-after: avoid;
  }
  h2 {
    font-size: 16pt;
    color: #0369a1;
    margin-top: 24px;
    margin-bottom: 12px;
    border-bottom: 1px solid #e2e8f0;
    padding-bottom: 4px;
    page-break-after: avoid;
  }
  h3 {
    font-size: 13pt;
    color: #1e293b;
    margin-top: 18px;
    margin-bottom: 8px;
    page-break-after: avoid;
  }
  p {
    margin: 0 0 10px 0;
  }
  
  /* Tables */
  table {
    width: 100%;
    border-collapse: collapse;
    margin: 12px 0 18px 0;
    font-size: 9.5pt;
    page-break-inside: avoid;
  }
  th {
    background: #f1f5f9;
    color: #0f172a;
    text-align: left;
    padding: 8px 10px;
    font-weight: 700;
    border: 1px solid #cbd5e1;
  }
  td {
    padding: 7px 10px;
    border: 1px solid #e2e8f0;
    vertical-align: top;
  }
  tr:nth-child(even) td {
    background: #f8fafc;
  }

  /* Boxes & Alerts */
  .callout {
    background: #eff6ff;
    border-left: 4px solid #3b82f6;
    padding: 10px 14px;
    border-radius: 4px;
    margin: 12px 0;
    font-size: 10pt;
  }
  .callout-title {
    font-weight: 700;
    color: #1d4ed8;
    margin-bottom: 4px;
  }
  .callout-warning {
    background: #fffbeb;
    border-left-color: #f59e0b;
  }
  .callout-warning .callout-title {
    color: #b45309;
  }
  .callout-danger {
    background: #fef2f2;
    border-left-color: #ef4444;
  }
  .callout-danger .callout-title {
    color: #b91c1c;
  }
  
  /* Screenshots */
  .screenshot-container {
    margin: 14px 0 18px 0;
    text-align: center;
    page-break-inside: avoid;
  }
  .screenshot-img {
    width: 100%;
    max-height: 480px;
    object-fit: contain;
    border-radius: 6px;
    border: 1px solid #cbd5e1;
    box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
  }
  .screenshot-caption {
    font-size: 8.5pt;
    color: #64748b;
    margin-top: 5px;
    font-style: italic;
  }

  /* Mockup Cards for University Screens */
  .ui-mockup {
    border: 1px solid #cbd5e1;
    border-radius: 6px;
    background: #ffffff;
    padding: 12px;
    margin: 12px 0 16px 0;
    box-shadow: 0 2px 4px rgba(0,0,0,0.05);
    page-break-inside: avoid;
  }
  .ui-header {
    background: #0f172a;
    color: white;
    padding: 6px 12px;
    border-radius: 4px;
    font-size: 9pt;
    font-weight: 600;
    display: flex;
    justify-content: space-between;
    margin-bottom: 10px;
  }
  .ui-toolbar {
    display: flex;
    gap: 8px;
    margin-bottom: 8px;
  }
  .ui-badge {
    display: inline-block;
    padding: 2px 8px;
    border-radius: 12px;
    font-size: 8pt;
    font-weight: 600;
  }
  .badge-success { background: #dcfce7; color: #15803d; }
  .badge-warning { background: #fef3c7; color: #b45309; }
  .badge-danger { background: #fee2e2; color: #b91c1c; }
  .badge-info { background: #e0f2fe; color: #0369a1; }

  /* Numbered Steps */
  .steps-list {
    margin: 10px 0;
    padding-left: 20px;
  }
  .steps-list li {
    margin-bottom: 8px;
  }
  .steps-list strong {
    color: #0f172a;
  }
</style>
</head>
<body>

<!-- ================= COVER PAGE ================= -->
<div class="cover">
  <div class="cover-badge">Official Documentation</div>
  <div class="cover-title">EDU360 Complete Platform User Manual</div>
  <div class="cover-subtitle">
    End-to-End Operational Manual for SaaS Operators, University Administrators, Academic Leadership, Faculty, and Support Teams.
  </div>
  <div class="cover-meta">
    <div><strong>Edition:</strong> Comprehensive 6.0</div>
    <div><strong>System Version:</strong> EDU360 Enterprise</div>
    <div><strong>Classification:</strong> Operations & User Reference</div>
  </div>
</div>

<div class="page-break"></div>

<!-- ================= TABLE OF CONTENTS ================= -->
<h1>Table of Contents</h1>
<table style="font-size: 10.5pt; line-height: 1.8;">
  <tr><th>Section</th><th>Topics Covered</th></tr>
  <tr><td><strong>1. Executive Architecture</strong></td><td>Multi-Tenancy Explained, Platform Console vs. University Portal</td></tr>
  <tr><td><strong>2. Platform Operations Console (Part I)</strong></td><td>Tenant Lifecycle, Plans, Invoicing, Governed Support Access, Reports</td></tr>
  <tr><td><strong>3. Deep Modal Operations</strong></td><td>Entitlement Editor, Compliance & Retention Waiver, Branding, Usage</td></tr>
  <tr><td><strong>4. Team & System Governance</strong></td><td>Staff Directory, Step-In Simulator, Role Templates, Audit Trail, Backup Health</td></tr>
  <tr><td><strong>5. University & Institution Portal (Part II)</strong></td><td>Campuses, Staff Directory, User Accounts, Missing Roles Audit</td></tr>
  <tr><td><strong>6. Academic Setup & Operations</strong></td><td>Academic Structure, HODs, Mentors, Class Teachers, Student Portal Lock</td></tr>
  <tr><td><strong>7. Calendar & Exam Governance</strong></td><td>Academic Calendar, Weekly Off, Term Lock, Delegations, Academic Decisions</td></tr>
  <tr><td><strong>8. Customer Support & Self-Service</strong></td><td>Raising Support Tickets, Granting 24-hr Support Sessions, Profile Security</td></tr>
  <tr><td><strong>9. Master Business Playbooks</strong></td><td>Contract to Live, Pro-Rata Upgrades, Safe Diagnostics, Emergency Term Unlock</td></tr>
</table>

<div class="page-break"></div>

<!-- ================= SECTION 1 ================= -->
<h1>1. Executive Architecture & Core Concepts</h1>

<div class="callout">
  <div class="callout-title">The "Apartment Building" Analogy (Multi-Tenancy Explained)</div>
  <p>To understand EDU360, imagine an apartment high-rise:</p>
  <ul>
    <li><strong>The Building is EDU360:</strong> All servers, code, and security belong to your software provider.</li>
    <li><strong>Each Apartment is a "Tenant" (a School):</strong> Greenwood High lives in Apartment 101. St. Mary's College lives in Apartment 102. Each has its own private key and database. Neither can ever see inside the other's apartment.</li>
    <li><strong>The Management Office is the Platform Console:</strong> Where SaaS operators onboard schools, issue subscription bills, and perform maintenance.</li>
    <li><strong>The Residents are the University Portal:</strong> Where principals, teachers, and students manage courses, attendance, and grades.</li>
  </ul>
</div>

<h2>The Dual-World Ecosystem</h2>
<table>
  <tr>
    <th style="width: 50%;">Platform Operations Console (/platform/*)</th>
    <th style="width: 50%;">University / Customer Portal (/university/*)</th>
  </tr>
  <tr>
    <td>
      <strong>Who works here:</strong> Product Owners, Platform Admins, Billing Managers, Support Engineers, Compliance Officers.<br><br>
      <strong>Core Purpose:</strong> Onboarding new school tenants, configuring commercial subscription plans, collecting SaaS billing, monitoring backups, and providing governed read-only technical support.
    </td>
    <td>
      <strong>Who works here:</strong> University Admins, Principals, Deans, HODs, Teachers, Staff, Students, and Guardians.<br><br>
      <strong>Core Purpose:</strong> Managing academic departments, scheduling classes, recording attendance, calculating exam marks, locking terms, and issuing student transcripts.
    </td>
  </tr>
</table>

<div class="page-break"></div>

<!-- ================= SECTION 2 ================= -->
<h1>2. Platform Operations Console (SaaS Administration)</h1>

<h2>2.1 Sign-In & Authentication (/login)</h2>
<div class="screenshot-container">
  <img src="${images.login}" class="screenshot-img" alt="Login Screen">
  <div class="screenshot-caption">Figure 2.1: Keycloak-backed Single Sign-On Authentication Portal</div>
</div>
<p><strong>What is this screen?</strong> The front door to the entire platform.</p>
<ul class="steps-list">
  <li><strong>Step 1:</strong> Enter your work email address and password.</li>
  <li><strong>Step 2:</strong> Click <strong>Sign In</strong>.</li>
  <li><strong>Step 3:</strong> If MFA is enforced, enter the 6-digit code from your authenticator app.</li>
  <li><strong>What happens immediately:</strong> The system checks your role and automatically routes Platform Staff to <code>/platform</code> and School Staff to <code>/university/institutions</code> or <code>/dashboard</code>.</li>
</ul>

<h2>2.2 Executive Platform Overview (/platform)</h2>
<div class="screenshot-container">
  <img src="${images.overview}" class="screenshot-img" alt="Overview Screen">
  <div class="screenshot-caption">Figure 2.2: Platform Executive Overview Cockpit</div>
</div>
<p><strong>What is this screen?</strong> Your daily executive cockpit summarizing live customer adoption, revenue, and system health.</p>
<ul>
  <li><strong>KPI Cards:</strong> Displays All Tenants, Active Customers, and Accounts in 30-Day Evaluation Trials.</li>
  <li><strong>Role-Adaptive Dashboard:</strong> Adjusts dynamically: Product Owners see revenue summaries and ticket escalations; Billing Managers see overdue invoices; Admins see system health.</li>
</ul>

<div class="page-break"></div>

<h2>2.3 Tenant Management & School Directory (/platform/tenants)</h2>
<div class="screenshot-container">
  <img src="${images.tenants}" class="screenshot-img" alt="Tenants Screen">
  <div class="screenshot-caption">Figure 2.3: Tenant Directory and Lifecycle Command Center</div>
</div>
<p><strong>What is this screen for?</strong> The master registry of every educational customer.</p>
<ul class="steps-list">
  <li><strong>How to Onboard a Trial School:</strong> Click <strong>"Create trial tenant"</strong>. Enter the School Name, Subdomain Slug (e.g., <code>greenwood</code> &rarr; <code>greenwood.edu360.com</code>), and Principal Email. Click <strong>Provision Tenant</strong>. The system spins up an isolated database and displays temporary credentials.</li>
  <li><strong>How to Activate to Live:</strong> When the school signs their contract, click <strong>Activate</strong> on their row, select their paid plan, and set the start date.</li>
  <li><strong>How to Upgrade Plans:</strong> Click the row menu (<code>...</code>) &rarr; <strong>Change plan</strong> &rarr; select the higher plan &rarr; select <strong>NOW</strong>. The system instantly expands student quotas and creates a pro-rata draft invoice for the remaining days!</li>
  <li><strong>How to Suspend for Non-Payment:</strong> Click <strong>Suspend</strong> &rarr; enter reason &rarr; confirm. The school's users are blocked with a polite payment notice, but <strong>zero student data is ever deleted</strong>.</li>
</ul>

<h2>2.4 Commercial Subscription Plans (/platform/plans)</h2>
<div class="screenshot-container">
  <img src="${images.plans}" class="screenshot-img" alt="Plans Screen">
  <div class="screenshot-caption">Figure 2.4: Subscription Packaging and Hard Capacity Quotas</div>
</div>
<p><strong>What is this screen for?</strong> Packaging your SaaS offerings (Starter, Standard, Enterprise).</p>
<ul class="steps-list">
  <li>Click <strong>+ Create New Plan</strong>.</li>
  <li>Set the <strong>Student Hard-Cap</strong> (e.g., 2,000 students), <strong>Staff Cap</strong> (e.g., 150 staff), and <strong>Storage Quota</strong> (e.g., 100 GB).</li>
  <li>Set annual/monthly pricing and save. The plan becomes immediately selectable during tenant creation.</li>
</ul>

<div class="page-break"></div>

<h2>2.5 Invoices & Payment Operations (/platform/invoices)</h2>
<div class="screenshot-container">
  <img src="${images.invoices}" class="screenshot-img" alt="Invoices Screen">
  <div class="screenshot-caption">Figure 2.5: Invoices and Accounts Receivable Desk</div>
</div>
<p><strong>What is this screen for?</strong> Managing bills, payments, and overdue accounts.</p>
<ul class="steps-list">
  <li><strong>Creating a Bill:</strong> Click <strong>+ Create Invoice</strong> &rarr; select school &rarr; add line item &rarr; click <strong>Create Draft</strong>.</li>
  <li><strong>Dispatching to Customer:</strong> Click <strong>Finalize & Send</strong>. The invoice moves to <strong>SENT</strong> with an automatic due date of <strong>15 days</strong> from dispatch, and an official PDF invoice is emailed to the school.</li>
  <li><strong>Recording Payments:</strong> When the school wires money, click <strong>Record Pay</strong> &rarr; select Bank Transfer / Card / Cheque &rarr; enter the bank transaction reference number &rarr; click <strong>Confirm Payment</strong>. The invoice flips to green: <strong>PAID</strong>.</li>
  <li><strong>Overdue Automation:</strong> Invoices unpaid after 15 days switch to <strong>OVERDUE</strong>. Click <strong>Flag Review</strong> to escalate to the Product Owner for grace extension or account suspension.</li>
</ul>

<h2>2.6 Support Tickets Desk & Triage (/platform/tickets)</h2>
<div class="screenshot-container">
  <img src="${images.tickets}" class="screenshot-img" alt="Tickets Screen">
  <div class="screenshot-caption">Figure 2.6: Customer Support Desk and Escalations</div>
</div>
<ul class="steps-list">
  <li><strong>Triaging Incoming Tickets:</strong> Click <strong>Triage</strong> on any new ticket. Set Priority (<code>LOW</code>, <code>MEDIUM</code>, <code>HIGH</code>, <code>CRITICAL</code>) and Category (<code>ACADEMIC</code>, <code>BILLING</code>, <code>BUG</code>).</li>
  <li><strong>Escalating Defects:</strong> If it's a technical code bug, click <strong>Escalate</strong> &rarr; choose <code>ENGINEERING</code> or <code>RELEASE_MANAGER</code> &rarr; provide reproduction notes &rarr; confirm.</li>
  <li><strong>Resolving:</strong> Click <strong>Resolve</strong> &rarr; type a helpful explanation to the school &rarr; click <strong>Mark Resolved</strong>. The customer is automatically notified.</li>
</ul>

<div class="page-break"></div>

<h2>2.7 Governed Read-Only Support Access (/platform/support)</h2>
<div class="screenshot-container">
  <img src="${images.support}" class="screenshot-img" alt="Support Access Screen">
  <div class="screenshot-caption">Figure 2.7: Governed Read-Only Support Access Portal</div>
</div>
<div class="callout callout-warning">
  <div class="callout-title">The Privacy & Security Rules</div>
  <p>To comply with student privacy laws, platform staff <strong>cannot view customer data at will</strong>. The school admin must actively generate a time-bound visitor pass tied to a ticket number.</p>
</div>
<ul class="steps-list">
  <li><strong>Step 1:</strong> School admin clicks "Grant Support Access" on ticket <code>SUP-1042</code> from their portal.</li>
  <li><strong>Step 2:</strong> The grant appears in <code>/platform/support</code> with an expiration timer (1 to 24 hours).</li>
  <li><strong>Step 3:</strong> Click <strong>Open read-only session</strong>.</li>
  <li><strong>Step 4:</strong> The school's app opens in a new tab with a prominent <strong>RED BANNER</strong>:
    <div style="background: #991b1b; color: white; padding: 6px 12px; border-radius: 4px; font-weight: bold; margin: 6px 0; font-size: 9pt;">
      [!] READ-ONLY SUPPORT SESSION | Ticket SUP-1042 | Ends in 58 mins [End Session]
    </div>
  </li>
  <li><strong>Step 5:</strong> All Save, Edit, and Delete controls are automatically hidden and disabled. Inspect and diagnose the issue with zero risk of altering student marks.</li>
  <li><strong>Step 6:</strong> Click <strong>End Session</strong> in the red banner to destroy the temporary token immediately.</li>
</ul>

<div class="page-break"></div>

<!-- ================= SECTION 3 ================= -->
<h1>3. Deep-Dive on Tenant Modals & Sub-Dialogs</h1>

<h2>3.1 Feature Entitlement Editor (Plan vs. Tenant Overrides)</h2>
<p>Accessed via <code>/platform/tenants</code> &rarr; row menu &rarr; <strong>Features</strong>.</p>
<ul>
  <li><strong>Module Matrix:</strong> Toggle modules like <em>Online Fee Collection</em>, <em>Hostels</em>, <em>Transport Tracking</em>, and <em>Biometrics</em>.</li>
  <li><strong>The Product Owner Rule:</strong> Turning on features beyond what the customer's plan includes is <strong>strictly restricted to the Product Owner</strong>. Non-PO operators are blocked with: <em>"Beyond the plan — Product Owner only."</em></li>
</ul>

<h2>3.2 Tenant Compliance & Data Protection Modal</h2>
<p>Accessed via <code>/platform/tenants</code> &rarr; row menu &rarr; <strong>Compliance</strong>.</p>
<ul>
  <li><strong>Export Tab:</strong> Generate and download encrypted zip archives of all tenant data.</li>
  <li><strong>Retention Tab:</strong> Inspect statutory retention months for <code>STRUCTURE</code>, <code>ATTENDANCE</code>, <code>GRADES</code>, and <code>FEES</code>.</li>
  <li><strong>Safe Deletion Safeguard:</strong> When processing legal account deletions, the operator <strong>must type the school's exact name</strong> into a confirmation box before the delete button activates.</li>
  <li><strong>PD-7 Retention Waiver:</strong> The Product Owner can check <em>"Waive statutory retention wait"</em> with a legal waiver reason to expedite permanent deletion.</li>
</ul>

<h2>3.3 Additional Tenant Modals</h2>
<table>
  <tr><th>Modal</th><th>Where to Open</th><th>What You Configure</th></tr>
  <tr>
    <td><strong>Branding Modal</strong></td>
    <td>Row menu &rarr; Branding</td>
    <td>Upload school logo crest (PNG/SVG) and configure primary and accent brand hex colors.</td>
  </tr>
  <tr>
    <td><strong>Settings Modal</strong></td>
    <td>Row menu &rarr; Settings</td>
    <td>Time zone (e.g. <code>Asia/Kolkata</code>), Data storage region (<code>IN</code>, <code>US</code>, <code>EU</code>), and Custom Domain (<code>portal.school.edu</code>).</td>
  </tr>
  <tr>
    <td><strong>Usage Modal</strong></td>
    <td>Row menu &rarr; Usage</td>
    <td>Live diagnostic of enrolled students, staff count, and storage GB used vs. plan limits.</td>
  </tr>
</table>

<div class="page-break"></div>

<!-- ================= SECTION 4 ================= -->
<h1>4. Team Governance & Access Control</h1>

<h2>4.1 Staff Directory (/platform/users)</h2>
<div class="screenshot-container">
  <img src="${images.users}" class="screenshot-img" alt="Users Screen">
  <div class="screenshot-caption">Figure 4.1: Internal Platform Operator Directory</div>
</div>
<p>Click <strong>+ Invite Team Member</strong> &rarr; enter email and assign business role (Product Owner, Billing Manager, Support Manager) &rarr; user receives email invite to set up credentials and MFA.</p>

<h2>4.2 Roles & The "Step-In" Simulator (/platform/roles)</h2>
<div class="screenshot-container">
  <img src="${images.roles}" class="screenshot-img" alt="Roles Screen">
  <div class="screenshot-caption">Figure 4.2: Live Permissions and Role Step-In Simulator</div>
</div>
<div class="callout">
  <div class="callout-title">The Step-In Simulator</div>
  <p>To verify what a junior operator can see: Click <strong>Step In</strong> next to their role. Your view instantly constrains to that role's exact permissions (billing and deletion buttons disappear). Click <strong>Exit Step-In</strong> at the bottom to return to your normal view.</p>
</div>

<h2>4.3 Role Templates Engine (/platform/role-templates)</h2>
<div class="screenshot-container">
  <img src="${images.templates}" class="screenshot-img" alt="Role Templates Screen">
  <div class="screenshot-caption">Figure 4.3: Standard Role Templates Engine</div>
</div>
<p>Standardized blueprints for creating new roles. Notice the <strong>Data Protection Officer</strong> template is permanently <strong>LOCKED</strong> to prevent tampering with statutory GDPR duties.</p>

<div class="page-break"></div>

<!-- ================= SECTION 5 ================= -->
<h1>5. Analytics, Maintenance & System Health</h1>

<h2>5.1 Executive Platform Reports (/platform/reports)</h2>
<div class="screenshot-container">
  <img src="${images.reports}" class="screenshot-img" alt="Reports Screen">
  <div class="screenshot-caption">Figure 5.1: Business Intelligence & Revenue Run-Rate</div>
</div>
<ul>
  <li><strong>Billing & Revenue:</strong> Track Monthly Recurring Revenue (MRR), ARR, and view <strong>Contracts Due for Renewal in 30, 60, or 90 days</strong>.</li>
  <li><strong>Support & SLA:</strong> Average response times and open ticket volume breakdown.</li>
</ul>

<h2>5.2 Disaster Recovery & Backup Health (/platform/backup-status)</h2>
<div class="screenshot-container">
  <img src="${images.backup}" class="screenshot-img" alt="Backup Status Screen">
  <div class="screenshot-caption">Figure 5.2: Automated Database Snapshots & Point-in-Time Restore Health</div>
</div>
<p><strong>What is this screen for?</strong> Verifying automated daily disaster recovery backups.</p>
<ul class="steps-list">
  <li><strong>Last Backup Outcome:</strong> Displays green <code>SUCCESS</code> when nightly snapshots complete.</li>
  <li><strong>Restorable Window:</strong> Point-in-time recovery range (e.g. 30-day continuous archive).</li>
  <li><strong>Restore Test Outcome:</strong> Results of weekly automated recovery drills to confirm backup viability.</li>
</ul>

<div class="page-break"></div>

<h2>5.3 Platform Usage Analytics (/platform/usage-counts)</h2>
<div class="screenshot-container">
  <img src="${images.usage}" class="screenshot-img" alt="Usage Counts Screen">
  <div class="screenshot-caption">Figure 5.3: Anonymous Aggregate Platform Usage Analytics</div>
</div>
<p><strong>What is this screen for?</strong> Combined platform metrics computed during the nightly platform rollup.</p>
<ul class="steps-list">
  <li>Displays combined tenant count, enrolled students, faculty records, and classes scheduled across all customer schools.</li>
  <li>Anonymized: No private customer names or student records are exposed here.</li>
</ul>

<h2>5.4 Third-Party Integration Types (/platform/integration-types)</h2>
<div class="screenshot-container">
  <img src="${images.integrations}" class="screenshot-img" alt="Integration Types Screen">
  <div class="screenshot-caption">Figure 5.4: Third-Party Integration Providers Registry</div>
</div>
<p><strong>What is this screen for?</strong> Registering approved third-party vendors (Stripe, Razorpay, Twilio, Biometric attendance devices).</p>
<ul class="steps-list">
  <li>Click <strong>+ Create Integration Type</strong> to add a new category.</li>
  <li>Click <strong>Edit</strong> to update allowed providers or change status (<code>ALLOWED</code>, <code>DEPRECATED</code>, <code>DISABLED</code>).</li>
</ul>

<div class="page-break"></div>

<h2>5.5 Maintenance Windows & Release Announcements (/platform/releases)</h2>
<div class="screenshot-container">
  <img src="${images.releases}" class="screenshot-img" alt="Releases Screen">
  <div class="screenshot-caption">Figure 5.5: Maintenance Scheduling and Release Broadcasts</div>
</div>
<p><strong>What is this screen for?</strong> Informing schools in advance about planned downtime.</p>
<ul class="steps-list">
  <li>Click <strong>+ Schedule Downtime</strong> &rarr; pick date, time, and expected duration.</li>
  <li>Type customer message &rarr; click <strong>Publish Announcement</strong>. A broadcast banner displays across all school portals.</li>
</ul>

<h2>5.6 Security Policy & Multi-Factor Authentication (/platform/security-policy)</h2>
<div class="screenshot-container">
  <img src="${images.security}" class="screenshot-img" alt="Security Policy Screen">
  <div class="screenshot-caption">Figure 5.6: Cybersecurity and Authentication Policies</div>
</div>
<p><strong>What is this screen for?</strong> Enforcing platform-wide cybersecurity standards.</p>
<ul class="steps-list">
  <li>Toggle mandatory Two-Factor Authentication (MFA) across operators and school administrators.</li>
  <li>Configure session inactivity auto-lockout timers (15, 30, or 60 minutes).</li>
</ul>

<div class="page-break"></div>

<h2>5.7 Immutable Platform Audit Trail (/platform/audit)</h2>
<div class="screenshot-container">
  <img src="${images.audit}" class="screenshot-img" alt="Audit Log Screen">
  <div class="screenshot-caption">Figure 5.7: Permanent Immutable Audit Log</div>
</div>
<p><strong>What is this screen for?</strong> The permanent black-box flight recorder of the platform.</p>
<ul class="steps-list">
  <li>Every plan change, invoice payment, user invitation, and support view is permanently recorded.</li>
  <li>Filter by Action, Actor, or Date Range, or click <strong>Export Audit CSV</strong> for compliance audits.</li>
</ul>

<h2>5.8 GDPR Data Protection Workspace (/platform/data-protection)</h2>
<div class="screenshot-container">
  <img src="${images.dataProtection}" class="screenshot-img" alt="Data Protection Screen">
  <div class="screenshot-caption">Figure 5.8: GDPR Compliance and Data Protection Workspace</div>
</div>
<p><strong>What is this screen for?</strong> Managed by the Data Protection Officer (DPO) for legal privacy workflows.</p>
<ul class="steps-list">
  <li><strong>Export Requests:</strong> Review and approve full encrypted data archive packages.</li>
  <li><strong>Deletion Requests:</strong> Review permanent erasure requests (Right to Be Forgotten).</li>
  <li><strong>Retention Changes:</strong> Adjust legal data retention timeframes per data classification.</li>
  <li><strong>RBAC Mapping Check:</strong> Verify tenant role isolation rules across all institutions and export certified CSV reports.</li>
</ul>

<div class="page-break"></div>

<!-- ================= SECTION 6 ================= -->
<h1>6. University & Customer Portal (Part II)</h1>

<p>This volume covers the customer-facing educational management software used by school leadership, faculty, and administrative staff.</p>

<h2>6.1 Institutions & Campuses (/university/institutions)</h2>
<div class="ui-mockup">
  <div class="ui-header">
    <span>GREENWOOD INTERNATIONAL - INSTITUTION DIRECTORY</span>
    <span>Role: University Admin</span>
  </div>
  <div class="ui-toolbar">
    <button style="padding: 4px 10px; background: #0284c7; color: white; border: none; border-radius: 4px; font-size: 8.5pt;">+ Add Campus / College</button>
  </div>
  <table style="margin: 6px 0;">
    <tr><th>Campus Code</th><th>Institution Name</th><th>Principal</th><th>Status</th><th>Actions</th></tr>
    <tr><td>MAIN-01</td><td>Greenwood High - Main Campus</td><td>Dr. Robert Vance</td><td><span class="ui-badge badge-success">ACTIVE</span></td><td>[ Edit ] [ Settings ]</td></tr>
    <tr><td>SOUTH-02</td><td>Greenwood South Branch</td><td>Ms. Clara Miller</td><td><span class="ui-badge badge-success">ACTIVE</span></td><td>[ Edit ] [ Settings ]</td></tr>
  </table>
</div>
<ul class="steps-list">
  <li><strong>What it is for:</strong> Managing multi-campus networks under a single university tenant.</li>
  <li><strong>How to use it:</strong> Click <strong>+ Add Campus</strong> &rarr; enter Campus Name, Institutional Code, Address, and assign the Campus Principal &rarr; click save.</li>
</ul>

<h2>6.2 Faculty & Staff Master Directory (/university/staff)</h2>
<div class="ui-mockup">
  <div class="ui-header">
    <span>FACULTY & STAFF DIRECTORY</span>
    <span>HR Administration</span>
  </div>
  <table style="margin: 6px 0;">
    <tr><th>Employee ID</th><th>Full Name</th><th>Department</th><th>Designation</th><th>User Account</th></tr>
    <tr><td>EMP-104</td><td>Prof. Alan Turing</td><td>Computer Science</td><td>Senior Professor</td><td><span class="ui-badge badge-success">Linked</span></td></tr>
    <tr><td>EMP-105</td><td>Dr. Ada Lovelace</td><td>Mathematics</td><td>Associate Dean</td><td><span class="ui-badge badge-success">Linked</span></td></tr>
  </table>
</div>
<ul class="steps-list">
  <li><strong>What it is for:</strong> The HR record of all academic faculty and non-teaching staff.</li>
  <li><strong>How to use it:</strong> Click <strong>Add Staff</strong> &rarr; fill in Employee ID, Legal Name, Department, Designation, and Joining Date. Click <strong>Link to User Account</strong> to connect their HR record to their login email.</li>
</ul>

<div class="page-break"></div>

<h2>6.3 User Accounts & Role Permissions (/university/users, /university/roles)</h2>
<p>Manage who can log into the school portal and what they are allowed to do.</p>
<ul class="steps-list">
  <li><strong>Provisioning Users (/university/users):</strong> Create logins for teachers, administrators, and students. Issue password reset links or unlock frozen accounts.</li>
  <li><strong>Role Permissions Matrix (/university/roles):</strong> View permissions across Academics, Grading, Attendance, and Fees. Use <strong>Rolldown Overrides</strong> to customize permissions for specific department heads.</li>
  <li><strong>Missing Roles Audit (/university/missing-roles):</strong> An automated audit tool that identifies staff members who have been created in HR but have not yet been assigned operational roles in the software.</li>
</ul>

<h2>6.4 Academic Structure Setup (/university/setup/structure)</h2>
<div class="ui-mockup">
  <div class="ui-header">
    <span>ACADEMIC STRUCTURE MANAGEMENT</span>
    <span>Curriculum & Program Setup</span>
  </div>
  <div style="font-size: 9pt; padding: 6px 0;">
    <strong>Hierarchical Tree:</strong> Faculty of Engineering &rarr; Computer Science Dept &rarr; B.Tech Program &rarr; 2026 Batch &rarr; Section A & B
  </div>
</div>
<ul class="steps-list">
  <li><strong>Step 1 - Academic Sessions:</strong> Click <strong>Add Academic Session</strong> (e.g., <code>2026-2027 Academic Year</code>).</li>
  <li><strong>Step 2 - Departments:</strong> Click <strong>Add Department</strong> (e.g., <code>Department of Physics</code>).</li>
  <li><strong>Step 3 - Programs:</strong> Click <strong>Add Program</strong> (e.g., <code>Bachelor of Science in Physics</code>), define credit requirements, semesters, and course catalogs.</li>
  <li><strong>Step 4 - Sections & Batches:</strong> Create individual class cohorts (e.g., <code>Class 10 - Section B</code>) to receive student rosters.</li>
</ul>

<div class="page-break"></div>

<!-- ================= SECTION 7 ================= -->
<h1>7. Academic Operations, Assignments & Exam Governance</h1>

<h2>7.1 Staff Assignments (HOD, Mentor, Class Teacher)</h2>
<table>
  <tr><th>Assignment Route</th><th>Who is Assigned</th><th>Operational Impact</th></tr>
  <tr>
    <td><strong>/university/setup/hod</strong></td>
    <td>Head of Department</td>
    <td>Grants approval authority over syllabus plans, teacher leave requests, and department budget approvals.</td>
  </tr>
  <tr>
    <td><strong>/university/setup/mentor</strong></td>
    <td>Faculty Mentor</td>
    <td>Pairs a faculty member with a cohort of 15–20 students to monitor attendance and pastoral wellbeing.</td>
  </tr>
  <tr>
    <td><strong>/university/setup/class-teacher</strong></td>
    <td>Class Teacher</td>
    <td>Authorizes daily homeroom attendance recording, report card remarks, and parent-teacher communications.</td>
  </tr>
</table>

<h2>7.2 Student Login Switch (/university/setup/student-login)</h2>
<div class="callout callout-warning">
  <div class="callout-title">Emergency Access Control</div>
  <p>During final grade tabulations or administrative review, the University Admin can flip the <strong>Student Portal Switch</strong> to OFF. Students attempting to log in see a custom notice (e.g., <em>"Student portal is undergoing scheduled maintenance for semester grading."</em>). Teachers and staff continue working normally.</p>
</div>

<h2>7.3 Academic Calendar & Weekly Off (/university/setup/calendar-events, /weekly-off)</h2>
<ul class="steps-list">
  <li><strong>Calendar Events:</strong> Click <strong>Add Master Event</strong> to schedule Examination Weeks, Sports Days, Parent-Teacher Conferences, and Statutory Holidays.</li>
  <li><strong>Weekly Off Rules:</strong> Configure weekend schedules (e.g., 5-day school week vs. 6-day week, or alternating 2nd and 4th Saturday holidays). The timetable engine automatically adjusts working day counts based on these rules.</li>
</ul>

<div class="page-break"></div>

<h2>7.4 Term Lock & Grade Integrity (/university/setup/term-lock)</h2>
<div class="ui-mockup">
  <div class="ui-header">
    <span>TERM LOCK & GRADEBOOK INTEGRITY</span>
    <span>Registrar & Academic Dean</span>
  </div>
  <table style="margin: 6px 0;">
    <tr><th>Term</th><th>Dates</th><th>Status</th><th>Action</th></tr>
    <tr><td>Fall Semester 2026</td><td>01 Aug - 15 Dec 2026</td><td><span class="ui-badge badge-danger">LOCKED & FROZEN</span></td><td>[ Request Unlock ]</td></tr>
    <tr><td>Spring Semester 2027</td><td>05 Jan - 20 May 2027</td><td><span class="ui-badge badge-success">ACTIVE (OPEN)</span></td><td>[ Lock Term ]</td></tr>
  </table>
</div>
<ul class="steps-list">
  <li><strong>What Term Lock Does:</strong> Once a semester ends and final grades are published, the Registrar clicks <strong>Lock Term</strong>. This permanently freezes past attendance, exam marks, and transcripts from any modification.</li>
  <li><strong>How to Unlock in Emergencies:</strong> If a teacher made a genuine marking error: Click <strong>Request Unlock</strong> &rarr; enter reason &rarr; requires dual sign-off from the Principal and Academic Dean before unlocking for a 24-hour correction window.</li>
  <li><strong>Promotion Runs:</strong> After term locking, click <strong>Promotion Run</strong> to batch-promote passing students into the next academic year.</li>
</table>

<h2>7.5 Customer Support & Raising Tickets (/support)</h2>
<p>Located in the school portal navigation for customer administrators.</p>
<ul class="steps-list">
  <li><strong>Submitting a Ticket:</strong> Type Subject, select Priority (<code>MEDIUM</code>, <code>HIGH</code>), enter issue description, and click <strong>Submit Support Ticket</strong>. A ticket number (e.g., <code>SUP-1042</code>) is immediately created.</li>
  <li><strong>Granting Support Access:</strong> On the open ticket, click <strong>Grant Support Access</strong> &rarr; select duration (1 to 24 hours) &rarr; click confirm. This creates the secure visitor pass that allows the platform technician to enter the school's workspace in read-only mode.</li>
  <li><strong>Withdrawing Access:</strong> You can click <strong>Withdraw</strong> at any time to instantly revoke the technician's access.</li>
</ul>

<div class="page-break"></div>

<!-- ================= SECTION 8 ================= -->
<h1>8. Master End-to-End Business Playbooks</h1>

<h2>Playbook A: Complete School Onboarding (Contract to Live)</h2>
<ol class="steps-list">
  <li><strong>Contract Signed:</strong> Greenwood High signs an annual agreement for <em>Standard Campus</em>.</li>
  <li><strong>Create Tenant:</strong> Go to <code>/platform/tenants</code> &rarr; click <strong>Create trial tenant</strong> &rarr; enter School Name, Slug: <code>greenwood</code>, and Principal Email.</li>
  <li><strong>Customer Configures Campus:</strong> Principal logs into <code>greenwood.edu360.com</code> &rarr; sets up campuses, programs, and staff records.</li>
  <li><strong>Activate Tenant:</strong> In <code>/platform/tenants</code>, click <strong>Activate</strong> on Greenwood High &rarr; select <em>Standard Campus</em> &rarr; set start date.</li>
  <li><strong>Invoice Dispatched:</strong> In <code>/platform/invoices</code>, create and finalize the annual invoice &rarr; email sent.</li>
  <li><strong>Payment Recorded:</strong> When wire transfer clears, click <strong>Record Pay</strong> &rarr; enter UTR reference ID &rarr; invoice marked <strong>PAID</strong>.</li>
</ol>

<h2>Playbook B: Troubleshooting a Customer Issue Safely</h2>
<ol class="steps-list">
  <li><strong>Customer Reports Issue:</strong> Greenwood High principal submits ticket <code>SUP-1042</code> reporting exam gradebook export timeouts.</li>
  <li><strong>Customer Grants Access:</strong> Principal opens their <code>/support</code> page &rarr; clicks <strong>Grant 24-hr Access</strong>.</li>
  <li><strong>Support Operator Enters:</strong> Operator opens <code>/platform/support</code> &rarr; clicks <strong>Open read-only session</strong>.</li>
  <li><strong>Diagnose Safely:</strong> The school portal opens with the <strong>Red Read-Only Banner</strong>. Operator inspects the gradebook settings with zero risk of modifying marks.</li>
  <li><strong>End Session:</strong> Operator clicks <strong>End Session</strong> on the banner.</li>
  <li><strong>Resolve Ticket:</strong> In <code>/platform/tickets</code>, operator clicks <strong>Resolve</strong> &rarr; types resolution note explaining how the teacher can re-export &rarr; customer notified.</li>
</ol>

<hr style="border: 0; border-top: 1px solid #cbd5e1; margin: 30px 0;">
<div style="text-align: center; color: #64748b; font-size: 9pt;">
  EDU360 Platform Master User Manual &bull; Maintained by Product & Engineering Operations &bull; Certified for Production Use
</div>

</body>
</html>`;

fs.writeFileSync("./docs/temp_manual.html", htmlContent);
console.log("HTML written to ./docs/temp_manual.html");

(async () => {
  console.log("Launching Chromium via Playwright...");
  const browser = await chromium.launch({ channel: "chrome", headless: true });
  const page = await browser.newPage();
  
  console.log("Loading document into browser...");
  await page.setContent(htmlContent, { waitUntil: "networkidle" });
  
  console.log("Waiting for all images to decode...");
  await page.evaluate(async () => {
    const imgs = Array.from(document.querySelectorAll("img"));
    await Promise.all(
      imgs.map((img) => {
        if (img.complete && img.naturalHeight !== 0) return Promise.resolve();
        return new Promise((resolve) => {
          img.onload = resolve;
          img.onerror = resolve;
        });
      })
    );
  });
  
  console.log("Rendering publication-quality PDF...");
  await page.pdf({
    path: OUTPUT_PDF,
    format: "A4",
    printBackground: true,
    margin: {
      top: "16mm",
      bottom: "16mm",
      left: "12mm",
      right: "12mm"
    },
    displayHeaderFooter: true,
    headerTemplate: '<div style="font-size: 8pt; width: 100%; text-align: right; padding-right: 12mm; color: #94a3b8; font-family: sans-serif;">EDU360 Complete Platform Manual &bull; Confidential & Proprietary</div>',
    footerTemplate: '<div style="font-size: 8pt; width: 100%; text-align: center; color: #94a3b8; font-family: sans-serif;">Page <span class="pageNumber"></span> of <span class="totalPages"></span></div>'
  });

  await browser.close();
  console.log("SUCCESS! PDF generated at: " + OUTPUT_PDF);
  const stats = fs.statSync(OUTPUT_PDF);
  console.log("File Size: " + (stats.size / (1024 * 1024)).toFixed(2) + " MB");
  
  // Cleanup temp html
  if (fs.existsSync("./docs/temp_manual.html")) {
    fs.unlinkSync("./docs/temp_manual.html");
  }
})();

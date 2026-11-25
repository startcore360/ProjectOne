import { type FormEvent, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import Edu360Logo from "../../assets/Edu360Logo";
import platformLightImg from "../../assets/platform-previews/platform-light.png";
import platformDarkImg from "../../assets/platform-previews/platform-dark.png";
import loginLightImg from "../../assets/platform-previews/login-light.png";
import loginDarkImg from "../../assets/platform-previews/login-dark.png";
import { ThemeToggle } from "../../components/ui";
import { Icon, type IconName } from "../../components/ui/Icon";
import {
  Button,
  Kicker,
  Modal,
  Notice,
  SelectField,
  TextAreaField,
  TextField,
  toast,
} from "../../design-system/components";
import {
  buildTargetLoginUrl,
  clearRememberedInstitution,
  getRememberedInstitution,
  isBackForwardNavigation,
} from "../../lib/tenantHost";
import {
  landingApi,
  publicMediaUrl,
  type LandingTestimonialItem,
  type PublicCatalogModuleItem,
  type PublicLandingData,
} from "./api/landingApi";
import { InstitutionSelectorModal } from "./components/InstitutionSelectorModal";
import "./LandingPage.css";

export interface LandingPageProps {
  onNavigate?: (url: string) => void;
}

interface WhyChooseItem {
  icon?: string;
  title: string;
  description: string;
}

interface RoleBenefitItem {
  roleKey: string;
  label: string;
  headline: string;
  points: string[];
}

interface SolutionItem {
  title: string;
  description: string;
}

interface StepItem {
  stepNumber: string;
  title: string;
  description: string;
}

const DEFAULT_WHY_CHOOSE: WhyChooseItem[] = [
  {
    icon: "grid",
    title: "Academic management",
    description:
      "Organise campuses, departments, programmes, timetables, attendance, examinations and results in one system.",
  },
  {
    icon: "wallet",
    title: "Fees & finance",
    description:
      "Manage fee structures, concessions, receipts, instalments, payment tracking and collection reports.",
  },
  {
    icon: "bell",
    title: "Parent & family updates",
    description:
      "Keep families informed with notices, attendance alerts, fee reminders and real-time student progress.",
  },
  {
    icon: "users",
    title: "People & operations",
    description:
      "Coordinate staff records, leave workflows, delegations, approvals and everyday institutional processes.",
  },
  {
    icon: "chart",
    title: "Reports & insights",
    description:
      "Access operational reports with role-scoped visibility and PDF or Excel exports.",
  },
  {
    icon: "shield",
    title: "Governance & isolation",
    description:
      "Dedicated tenant realms, role-based access control, two-step login and immutable audit trails.",
  },
];

const DEFAULT_ROLE_BENEFITS: RoleBenefitItem[] = [
  {
    roleKey: "leadership",
    label: "Institutional Leadership",
    headline: "Complete institutional oversight without chasing spreadsheets.",
    points: [
      "Use a role-scoped workspace with an at-a-glance view of institutional priorities.",
      "Open the quick-navigation actions assigned to your leadership role.",
      "Review approval items assigned to you; pending approval counts appear when available.",
    ],
  },
  {
    roleKey: "faculty",
    label: "Teachers & Faculty",
    headline: "Spend less time on paperwork and more time teaching.",
    points: [
      "Open the teaching workspace and the quick actions assigned to your faculty role.",
      "Keep teaching-related tasks and follow-ups organised in one role-scoped area.",
      "See live counts only when the connected institutional services provide them.",
    ],
  },
  {
    roleKey: "students",
    label: "Students",
    headline: "Every academic update, timetable and fee receipt in one portal.",
    points: [
      "See your attendance, CGPA, leave balance and fee-status areas in one dashboard.",
      "Check timetable, assignment, campus-notice and published-result sections.",
      "Only account data returned by connected services is shown; unavailable values stay blank.",
    ],
  },
  {
    roleKey: "parents",
    label: "Parents & Guardians",
    headline: "Stay connected to your child's progress and school updates.",
    points: [
      "Choose a child linked to your guardian account.",
      "View the linked student code and status currently exposed in the parent portal.",
      "Additional attendance, fee and notification views should appear only when implemented and enabled.",
    ],
  },
];

const DEFAULT_SOLUTIONS: SolutionItem[] = [
  {
    title: "Schools",
    description:
      "Connect teachers, students, parents and school administration with attendance, fees, timetables and circulars.",
  },
  {
    title: "Colleges",
    description:
      "Coordinate programmes, semesters, faculty allocations, examinations, concessions and departmental approvals.",
  },
  {
    title: "Universities",
    description:
      "Support multi-institution hierarchies, central governance, delegated authority and consolidated reporting.",
  },
];

const DEFAULT_STEPS: StepItem[] = [
  {
    stepNumber: "01",
    title: "Share your institution profile",
    description: "Tell us your institution type, student scale, priority modules and onboarding needs.",
  },
  {
    stepNumber: "02",
    title: "Explore a tailored walkthrough",
    description: "See the actual EDU 360 workflows mapped to your leadership, faculty, student and parent roles.",
  },
  {
    stepNumber: "03",
    title: "Receive your scoped proposal",
    description: "Get a structured implementation and commercial quotation aligned to EDU 360's approved catalog.",
  },
];

const FALLBACK_MODULES: PublicCatalogModuleItem[] = [
  { moduleCode: "ACADEMICS", itemName: "Academic management", description: null },
  { moduleCode: "ATTENDANCE_EXAMS", itemName: "Attendance & exams", description: null },
  { moduleCode: "FEES_FINANCE", itemName: "Fees & finance", description: null },
  { moduleCode: "PARENT_COMMS", itemName: "Parent communication", description: null },
  { moduleCode: "HR_LEAVE", itemName: "HR & leave workflows", description: null },
  { moduleCode: "GOVERNANCE", itemName: "Reports & governance", description: null },
];

const FALLBACK_TESTIMONIALS: LandingTestimonialItem[] = [
  { testimonialId: "workflow-academics", kind: "TEXT", authorName: "Academic operations", authorRole: "Workflow spotlight", institutionName: "EDU 360 capability", quoteText: "Bring academic administration, attendance, examinations and schedules into a coordinated institutional workflow.", videoUrl: null, videoMediaId: null, posterMediaId: null, displayOrder: 0, published: true, rowVersion: 1 },
  { testimonialId: "workflow-governance", kind: "TEXT", authorName: "Institutional governance", authorRole: "Workflow spotlight", institutionName: "EDU 360 capability", quoteText: "Give leadership structured oversight with role-scoped access, approvals and institution-wide reporting.", videoUrl: null, videoMediaId: null, posterMediaId: null, displayOrder: 1, published: true, rowVersion: 1 },
  { testimonialId: "workflow-finance", kind: "TEXT", authorName: "Finance & fees", authorRole: "Workflow spotlight", institutionName: "EDU 360 capability", quoteText: "Connect fee administration and finance workflows with the wider operational picture of your institution.", videoUrl: null, videoMediaId: null, posterMediaId: null, displayOrder: 2, published: true, rowVersion: 1 },
  { testimonialId: "workflow-families", kind: "TEXT", authorName: "Family communication", authorRole: "Workflow spotlight", institutionName: "EDU 360 capability", quoteText: "Keep families and stakeholders informed with more coordinated communication and access to important updates.", videoUrl: null, videoMediaId: null, posterMediaId: null, displayOrder: 3, published: true, rowVersion: 1 },
];

const VALID_ICONS = new Set<IconName>([
  "grid",
  "wallet",
  "bell",
  "users",
  "chart",
  "shield",
  "book",
  "building",
  "lock",
  "layers",
]);

function parseJsonList<T>(raw: string | undefined, fallback: T[]): T[] {
  if (!raw) return fallback;
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) && parsed.length > 0 ? (parsed as T[]) : fallback;
  } catch {
    return fallback;
  }
}

function useIsDarkTheme(): boolean {
  const [isDark, setIsDark] = useState<boolean>(() => {
    if (typeof document === "undefined") return false;
    return document.documentElement.getAttribute("data-theme") === "dark";
  });

  useEffect(() => {
    if (typeof document === "undefined" || typeof MutationObserver === "undefined") return;
    const observer = new MutationObserver(() => {
      setIsDark(document.documentElement.getAttribute("data-theme") === "dark");
    });
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });
    return () => observer.disconnect();
  }, []);

  return isDark;
}

const defaultNavigate = (url: string) => {
  window.location.assign(url);
};

export default function LandingPage({ onNavigate = defaultNavigate }: LandingPageProps) {
  const [searchParams, setSearchParams] = useSearchParams();
  const isSwitching = searchParams.get("switch") === "1";
  const isDark = useIsDarkTheme();

  const [redirecting] = useState<boolean>(() => {
    if (isSwitching || isBackForwardNavigation()) return false;
    return Boolean(getRememberedInstitution());
  });
  const [selectorOpen, setSelectorOpen] = useState<boolean>(() => isSwitching);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  const [landingData, setLandingData] = useState<PublicLandingData | null>(null);
  const [activeRoleKey, setActiveRoleKey] = useState<string>("leadership");

  // Marquee controls
  const [marqueePaused, setMarqueePaused] = useState(false);
  const [marqueeReverse, setMarqueeReverse] = useState(false);
  const [activeVideoTestimonial, setActiveVideoTestimonial] = useState<LandingTestimonialItem | null>(null);

  // Demo Modal
  const [demoModalOpen, setDemoModalOpen] = useState(false);
  const [demoName, setDemoName] = useState("");
  const [demoEmail, setDemoEmail] = useState("");
  const [demoPhone, setDemoPhone] = useState("");
  const [demoInstitution, setDemoInstitution] = useState("");
  const [demoNotes, setDemoNotes] = useState("");
  const [demoBusy, setDemoBusy] = useState(false);
  const [demoError, setDemoError] = useState("");
  const [demoSuccess, setDemoSuccess] = useState("");

  // Quotation Form
  const [instType, setInstType] = useState("");
  const [studentRange, setStudentRange] = useState("");
  const [selectedModules, setSelectedModules] = useState<string[]>([]);
  const [migrationSupport, setMigrationSupport] = useState("No / not sure");
  const [trainingMode, setTrainingMode] = useState("Discuss with me");
  const [qName, setQName] = useState("");
  const [qEmail, setQEmail] = useState("");
  const [qPhone, setQPhone] = useState("");
  const [qInstitution, setQInstitution] = useState("");
  const [qNotes, setQNotes] = useState("");
  const [qBusy, setQBusy] = useState(false);
  const [qError, setQError] = useState("");
  const [qSuccess, setQSuccess] = useState("");

  useEffect(() => {
    if (isSwitching) {
      clearRememberedInstitution();
      const nextParams = new URLSearchParams(searchParams);
      nextParams.delete("switch");
      setSearchParams(nextParams, { replace: true });
      return;
    }

    if (isBackForwardNavigation()) {
      return;
    }

    const remembered = getRememberedInstitution();
    if (remembered) {
      onNavigate(buildTargetLoginUrl(remembered, window.location));
    }
  }, [isSwitching, onNavigate, searchParams, setSearchParams]);

  useEffect(() => {
    if (redirecting) return;
    let active = true;
    landingApi
      .getPublicLanding()
      .then((data) => {
        if (active) setLandingData(data);
      })
      .catch(() => {
        // Fallback defaults render seamlessly if backend is offline
      });
    return () => {
      active = false;
    };
  }, [redirecting]);

  const whyChoose = useMemo(
    () => parseJsonList<WhyChooseItem>(landingData?.whyChooseJson, DEFAULT_WHY_CHOOSE),
    [landingData?.whyChooseJson],
  );
  const roleBenefits = useMemo(
    () => parseJsonList<RoleBenefitItem>(landingData?.roleBenefitsJson, DEFAULT_ROLE_BENEFITS),
    [landingData?.roleBenefitsJson],
  );
  const solutions = useMemo(
    () => parseJsonList<SolutionItem>(landingData?.solutionsJson, DEFAULT_SOLUTIONS),
    [landingData?.solutionsJson],
  );
  const steps = useMemo(
    () => parseJsonList<StepItem>(landingData?.stepsJson, DEFAULT_STEPS),
    [landingData?.stepsJson],
  );
  const modules =
    landingData?.catalogModules && landingData.catalogModules.length > 0
      ? landingData.catalogModules
      : FALLBACK_MODULES;
  const testimonials =
    landingData?.testimonials && landingData.testimonials.length > 0
      ? landingData.testimonials
      : FALLBACK_TESTIMONIALS;

  const activeRole =
    roleBenefits.find((r) => r.roleKey === activeRoleKey) ?? roleBenefits[0];

  const heroImageUrl = isDark
    ? publicMediaUrl(landingData?.heroDarkMediaId) || platformDarkImg
    : publicMediaUrl(landingData?.heroLightMediaId) || platformLightImg;

  const showcaseImageUrl = isDark
    ? publicMediaUrl(landingData?.showcaseDarkMediaId) || loginDarkImg
    : publicMediaUrl(landingData?.showcaseLightMediaId) || loginLightImg;

  if (redirecting) {
    return (
      <main className="landing-page" aria-busy="true">
        <div className="landing-wrap" style={{ paddingBlock: "var(--space-12)" }}>
          <p className="landing-section__desc">Redirecting to your institution portal…</p>
        </div>
      </main>
    );
  }

  const toggleModule = (code: string) => {
    setSelectedModules((prev) =>
      prev.includes(code) ? prev.filter((item) => item !== code) : [...prev, code],
    );
  };

  const handleQuotationSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setQError("");
    setQSuccess("");
    if (!instType || !studentRange || !qName.trim() || !qEmail.trim() || !qInstitution.trim()) {
      setQError("Please complete institution type, student range, and your contact details.");
      return;
    }
    setQBusy(true);
    try {
      const receipt = await landingApi.submitPublicEnquiry({
        enquiryType: "QUOTATION",
        institutionType: instType,
        studentRange,
        selectedModules,
        migrationSupport,
        trainingMode,
        contactName: qName.trim(),
        contactEmail: qEmail.trim(),
        contactPhone: qPhone.trim() || undefined,
        institutionName: qInstitution.trim(),
        notes: qNotes.trim() || undefined,
      });
      setQSuccess(receipt.message);
      toast.success(receipt.message);
      setQName("");
      setQEmail("");
      setQPhone("");
      setQInstitution("");
      setQNotes("");
    } catch (error: unknown) {
      // The API returns a safe, structured error contract (code/message/fieldErrors).
      // Surface that response instead of hiding validation, rate-limit, or database failures
      // behind one generic message. Never render stack traces or raw exception details.
      const response = (error as {
        response?: {
          status?: number;
          data?: {
            code?: string;
            message?: string;
            error?: string;
            correlationId?: string;
            fieldErrors?: Record<string, string>;
          };
        };
      })?.response;
      const body = response?.data;
      const fieldMessages = body?.fieldErrors
        ? Object.entries(body.fieldErrors).map(([field, message]) => `${field}: ${message}`)
        : [];
      const details = fieldMessages.length > 0
        ? fieldMessages.join(" · ")
        : body?.message || body?.error;
      const reference = body?.correlationId ? ` Reference: ${body.correlationId}` : "";
      const status = response?.status ? ` (HTTP ${response.status}${body?.code ? ` · ${body.code}` : ""})` : "";
      setQError(details
        ? `${details}${status}.${reference}`
        : "Unable to reach the quotation service. Check your connection and try again.");
    } finally {
      setQBusy(false);
    }
  };

  const handleDemoSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setDemoError("");
    setDemoSuccess("");
    if (!demoName.trim() || !demoEmail.trim() || !demoInstitution.trim()) {
      setDemoError("Please enter your name, work email, and institution name.");
      return;
    }
    setDemoBusy(true);
    try {
      const receipt = await landingApi.submitPublicEnquiry({
        enquiryType: "DEMO",
        collegesCount: 0,
        schoolsCount: 0,
        studentsEstimate: 0,
        staffEstimate: 0,
        selectedModules: [],
        contactName: demoName.trim(),
        contactEmail: demoEmail.trim(),
        contactPhone: demoPhone.trim() || undefined,
        institutionName: demoInstitution.trim(),
        notes: demoNotes.trim() || undefined,
      });
      setDemoSuccess(receipt.message);
      toast.success(receipt.message);
      setDemoName("");
      setDemoEmail("");
      setDemoPhone("");
      setDemoInstitution("");
      setDemoNotes("");
    } catch {
      setDemoError("Unable to submit demo request right now. Please try again shortly.");
    } finally {
      setDemoBusy(false);
    }
  };

  const heroEyebrow = landingData?.heroEyebrow || "A connected education management platform";
  const heroTitle = landingData?.heroTitle || "Bring your entire institution into sync.";
  const heroSubtitle =
    landingData?.heroSubtitle ||
    "From academic administration, governance, and fee management to parent communication and student workflows—EDU 360 connects the work that keeps education moving.";
  const heroNote = landingData?.heroNote || "Designed for Schools · Colleges · Universities";
  const trustLabel =
    landingData?.trustLabel || "One connected platform for every stakeholder in your institution";
  const contactEmail = landingData?.contactEmail || "hello@edu360.example";
  const footerTagline =
    landingData?.footerTagline || "One Platform. Every Institution. One Connected Ecosystem.";

  const marqueeItems = [...testimonials, ...testimonials];
  const quoteProgress = [
    Boolean(instType),
    Boolean(studentRange),
    selectedModules.length > 0,
    Boolean(qName.trim()),
    Boolean(qEmail.trim()),
    Boolean(qInstitution.trim()),
  ].filter(Boolean).length;
  const quoteProgressPercent = Math.round((quoteProgress / 6) * 100);

  return (
    <div className="landing-page">
      <header className="landing-nav-bar">
        <div className="landing-wrap landing-nav">
          <a href="#top" className="landing-brand" aria-label="EDU 360 Home">
            <Edu360Logo className="landing-brand__logo" />
            <span className="landing-brand__descriptor">Connected education, clearly managed</span>
            <span className="landing-brand__tagline">Education Driven by Unity</span>
          </a>
          <nav className="landing-navlinks" aria-label="Primary navigation">
            <a href="#platform">Platform</a>
            <a href="#roles">By Role</a>
            <a href="#solutions">Solutions</a>
            <a href="#stories">Stories</a>
            <a href="#quotation">Quotation</a>
          </nav>
          <div className="landing-nav__actions">
            <ThemeToggle variant="inline" />
            <Button
              variant="secondary"
              size="sm"
              className="landing-nav__demo-link"
              onClick={() => {
                setDemoSuccess("");
                setDemoError("");
                setDemoModalOpen(true);
              }}
            >
              Request a demo
            </Button>
            <Button variant="primary" size="sm" onClick={() => setSelectorOpen(true)}>
              Sign in ↗
            </Button>
            <button
              type="button"
              className="landing-menu-btn"
              aria-label={mobileMenuOpen ? "Close menu" : "Open menu"}
              aria-expanded={mobileMenuOpen}
              onClick={() => setMobileMenuOpen((v) => !v)}
            >
              <Icon name={mobileMenuOpen ? "close" : "more"} size={20} />
            </button>
          </div>
        </div>
        <div className={`landing-mobile-drawer${mobileMenuOpen ? " open" : ""}`}>
          <a href="#platform" onClick={() => setMobileMenuOpen(false)}>Platform</a>
          <a href="#roles" onClick={() => setMobileMenuOpen(false)}>By Role</a>
          <a href="#solutions" onClick={() => setMobileMenuOpen(false)}>Solutions</a>
          <a href="#stories" onClick={() => setMobileMenuOpen(false)}>Stories</a>
          <a href="#quotation" onClick={() => setMobileMenuOpen(false)}>Get a quotation</a>
          <Button
            variant="secondary"
            size="sm"
            block
            onClick={() => {
              setMobileMenuOpen(false);
              setDemoModalOpen(true);
            }}
          >
            Request a demo
          </Button>
        </div>
      </header>

      <main id="top">
        {/* Hero */}
        <section className="landing-wrap landing-hero-section">
          <div className="landing-hero">
            <div>
              <div className="landing-hero__eyebrow">{heroEyebrow}</div>
              <h1 className="landing-hero__title">{heroTitle}</h1>
              <p className="landing-hero__subtitle">{heroSubtitle}</p>
              <div className="landing-hero__actions">
                <Button
                  variant="secondary"
                  size="lg"
                  onClick={() => {
                    setDemoSuccess("");
                    setDemoError("");
                    setDemoModalOpen(true);
                  }}
                >
                  Book a personalised demo ↗
                </Button>
                <a className="landing-hero__ghost-btn" href="#platform">
                  Explore the platform ↓
                </a>
              </div>
              <p className="landing-hero__note">{heroNote}</p>
              <div className="landing-hero__proof" aria-label="Platform capabilities">
                <span><i aria-hidden="true">✓</i> One connected workspace</span>
                <span><i aria-hidden="true">✓</i> Role-based access</span>
                <span><i aria-hidden="true">✓</i> Multi-campus ready</span>
              </div>
            </div>

            <div className="landing-hero__visual">
              <div className="landing-screenshot-frame">
                <div className="landing-screenshot-frame__bar" aria-hidden="true">
                  <span className="landing-screenshot-frame__dot" />
                  <span className="landing-screenshot-frame__dot" />
                  <span className="landing-screenshot-frame__dot" />
                  <span>EDU 360 · Live Workspace</span>
                </div>
                <img
                  src={heroImageUrl}
                  alt="EDU 360 workspace preview showing institutional governance and role management"
                />
              </div>
              <div className="landing-hero__badge">
                ✦ Isolated tenant realms · Role-scoped workflows
              </div>
            </div>
          </div>
        </section>

        {/* Trust strip */}
        <section className="landing-wrap landing-trust">
          <p className="landing-trust__label">{trustLabel}</p>
          <div className="landing-trust__row">
            <span>LEADERSHIP</span>
            <span>ACADEMICS</span>
            <span>FINANCE</span>
            <span>FACULTY</span>
            <span>FAMILIES</span>
          </div>
        </section>

        {/* Why choose EDU 360 / Capabilities */}
        <section className="landing-section landing-section--soft" id="platform">
          <div className="landing-wrap">
            <div className="landing-section__head landing-section__head--center">
              <Kicker>THE EDU 360 DIFFERENCE</Kicker>
              <h2 className="landing-section__title">
                Less fragmentation. More connected progress.
              </h2>
              <p className="landing-section__desc">
                Replace disconnected spreadsheets and siloed tools with one coordinated platform for
                academic, administrative, financial, and institutional governance work.
              </p>
            </div>
            <div className="landing-features">
              {whyChoose.map((item) => {
                const iconName: IconName =
                  item.icon && VALID_ICONS.has(item.icon as IconName)
                    ? (item.icon as IconName)
                    : "grid";
                return (
                  <article className="landing-feature-card" key={item.title}>
                    <div className="landing-feature-card__icon" aria-hidden="true">
                      <Icon name={iconName} size={22} />
                    </div>
                    <h3>{item.title}</h3>
                    <p>{item.description}</p>
                  </article>
                );
              })}
            </div>
          </div>
        </section>

        {/* Product capabilities overview */}
        <section className="landing-section landing-capabilities-section" id="capabilities">
          <div className="landing-wrap">
            <div className="landing-section__head landing-section__head--center">
              <Kicker>ONE PLATFORM · CONNECTED CAPABILITIES</Kicker>
              <h2 className="landing-section__title">The tools your institution relies on, working as one.</h2>
              <p className="landing-section__desc">
                Explore the core workflows EDU 360 brings together. Start with the areas that matter
                most and build a coordinated operating model across your institution.
              </p>
            </div>
            <div className="landing-capabilities-grid">
              <article className="landing-capability-card"><span>01 / ACADEMICS</span><h3>Academic management</h3><p>Organise the academic administration behind programmes, classes, schedules and day-to-day institutional delivery.</p><a href="#quotation">Explore this module <b>↗</b></a></article>
              <article className="landing-capability-card"><span>02 / STUDENT LIFE</span><h3>Attendance &amp; examinations</h3><p>Bring attendance activity and examination workflows into a clearer, more connected view for staff and academic teams.</p><a href="#quotation">Explore this module <b>↗</b></a></article>
              <article className="landing-capability-card"><span>03 / FINANCE</span><h3>Fees &amp; finance</h3><p>Coordinate fee administration and finance-related processes with the wider operational context of your institution.</p><a href="#quotation">Explore this module <b>↗</b></a></article>
              <article className="landing-capability-card"><span>04 / COMMUNITY</span><h3>Parent communication</h3><p>Make important institutional updates and communication easier to coordinate with families and guardians.</p><a href="#quotation">Explore this module <b>↗</b></a></article>
              <article className="landing-capability-card"><span>05 / PEOPLE</span><h3>HR &amp; leave workflows</h3><p>Support staff administration and leave-related workflows with more consistent processes and visibility.</p><a href="#quotation">Explore this module <b>↗</b></a></article>
              <article className="landing-capability-card"><span>06 / OVERSIGHT</span><h3>Reports &amp; governance</h3><p>Help institutional teams work with structured reporting, role-scoped access and clearer governance processes.</p><a href="#quotation">Explore this module <b>↗</b></a></article>
            </div>
          </div>
        </section>

        {/* Product experience: genuine app previews plus clearly labelled concepts */}
        <section className="landing-section landing-product-tour" id="experience">
          <div className="landing-wrap">
            <div className="landing-section__head landing-section__head--center">
              <Kicker>SEE EDU 360 IN ACTION</Kicker>
              <h2 className="landing-section__title">A clearer day for every part of your institution.</h2>
              <p className="landing-section__desc">
                Explore real EDU 360 interface previews, then see how connected workflows can support
                administrators, educators, students and families.
              </p>
            </div>
            <div className="landing-product-showcase">
              <article className="landing-product-shot landing-product-shot--wide">
                <div className="landing-product-shot__copy">
                  <span>01 · INSTITUTIONAL OPERATIONS</span>
                  <h3>One workspace for the people running your institution.</h3>
                  <p>Review platform administration, role templates and institutional controls in the actual EDU 360 interface.</p>
                  <a href="#quotation">Explore an institutional walkthrough ↗</a>
                </div>
                <div className="landing-product-shot__image landing-ops-preview" aria-label="EDU 360 institutional operations areas">
                  <div className="landing-ops-preview__head"><span>EDU 360 · INSTITUTIONAL WORKSPACE</span><b>Platform areas</b></div>
                  <div className="landing-ops-preview__item"><i>01</i><span><b>Role templates</b><small>Configure role starting points</small></span><em>Access</em></div>
                  <div className="landing-ops-preview__item"><i>02</i><span><b>Permissions</b><small>Scope actions to assigned roles</small></span><em>Controls</em></div>
                  <div className="landing-ops-preview__item"><i>03</i><span><b>Tenant workspace</b><small>Keep institutional work separated</small></span><em>Manage</em></div>
                  <small className="landing-ops-preview__caption">Illustrative navigation based on EDU 360 platform areas</small>
                </div>
              </article>
              <article className="landing-product-shot">
                <div className="landing-product-shot__image landing-product-shot__image--login">
                  <img src={showcaseImageUrl} alt="EDU 360 institution access and sign-in interface preview" loading="lazy" />
                  <small>Actual EDU 360 access interface</small>
                </div>
                <div className="landing-product-shot__copy">
                  <span>02 · CONNECTED ACCESS</span>
                  <h3>A clear entry point for your institution.</h3>
                  <p>Give authorised users a consistent route into their institution workspace, with role-scoped access and tenant separation.</p>
                  <a href="#quotation">See how access is structured ↗</a>
                </div>
              </article>
            </div>
            <div className="landing-experience-grid">
              <article className="landing-experience-card">
                <div className="landing-experience-card__top"><span>STUDENT EXPERIENCE</span><span className="landing-concept-tag">Concept preview</span></div>
                <h3>Make progress visible. Build good learning habits.</h3>
                <p>The student workspace is designed around a weekly learning rhythm, with space for timetable, notices and progress. Streak tracking is a product concept—not a live, verified feature yet.</p>
                <div className="landing-student-concept">
                  <div><small>YOUR LEARNING RHYTHM</small><strong>Build a steady study habit</strong></div>
                  <div className="landing-week-dots" aria-label="Illustrative weekly activity"><i> M </i><i> T </i><i> W </i><i> T </i><i> F </i><i> S </i><i> S </i></div>
                  <small className="landing-concept-caption">Illustrative design · no sample progress data</small>
                </div>
              </article>
              <article className="landing-experience-card">
                <div className="landing-experience-card__top"><span>PARENT &amp; FAMILY EXPERIENCE</span><span className="landing-concept-tag">Workflow preview</span></div>
                <h3>Keep families closer to the school day.</h3>
                <p>EDU 360 includes a tenant notification experience and self-service notices. Parent-specific fee alerts and payment visibility should be demonstrated against your enabled finance and parent workflows.</p>
                <div className="landing-parent-concept">
                  <div className="landing-parent-notice"><span>NOTICE</span><strong>Campus update</strong><small>Important information from your institution</small></div>
                  <div className="landing-parent-notice"><span>FINANCE · CONCEPT</span><strong>Fee due reminder</strong><small>Illustrative reminder layout—not a live payment status</small></div>
                </div>
              </article>
            </div>
            <p className="landing-product-disclaimer">Product screenshots show existing EDU 360 interfaces. Concept panels are illustrative and are not connected to live student or parent account data.</p>
          </div>
        </section>

        {/* Interactive Role-Based Benefits */}
        <section className="landing-section" id="roles">
          <div className="landing-wrap">
            <div className="landing-section__head">
              <Kicker>BUILT FOR EVERY STAKEHOLDER</Kicker>
              <h2 className="landing-section__title">
                How EDU 360 helps each role every day.
              </h2>
              <p className="landing-section__desc">
                Select a role below to see how EDU 360 streamlines daily responsibilities without
                giving up security or oversight.
              </p>
            </div>

            <div className="landing-roles-tabs" role="tablist" aria-label="EDU 360 role benefits">
              {roleBenefits.map((role) => {
                const selected = role.roleKey === activeRole.roleKey;
                return (
                  <button
                    key={role.roleKey}
                    type="button"
                    role="tab"
                    aria-selected={selected}
                    className={`landing-role-tab${selected ? " landing-role-tab--active" : ""}`}
                    onClick={() => setActiveRoleKey(role.roleKey)}
                  >
                    {role.label}
                  </button>
                );
              })}
            </div>

            {activeRole && (
              <div className="landing-role-panel" role="tabpanel" aria-label={activeRole.label}>
                <div>
                  <Kicker>{activeRole.label.toUpperCase()}</Kicker>
                  <h3>{activeRole.headline}</h3>
                  <ul className="landing-check-list">
                    {activeRole.points.map((pt) => (
                      <li key={pt}>
                        <span aria-hidden="true">✓</span>
                        <span>{pt}</span>
                      </li>
                    ))}
                  </ul>
                </div>
                <div className={`landing-role-visual landing-role-visual--${activeRole.roleKey}`} aria-label={`${activeRole.label} illustrative dashboard preview`}>
                  <div className="landing-role-visual__topbar">
                    <span className="landing-role-visual__brand"><Edu360Logo className="landing-role-visual__logo" /> EDU 360</span>
                    <span>ROLE WORKSPACE <i /> ILLUSTRATIVE PREVIEW</span>
                  </div>
                  {(activeRole.roleKey === "leadership" || activeRole.roleKey === "faculty") && (
                    <div className="landing-role-ui">
                      <div className="landing-role-ui__heading">
                        <div>
                          <small>{activeRole.roleKey === "leadership" ? "ROLE WORKSPACE · AT A GLANCE" : "TEACHING WORKSPACE · AT A GLANCE"}</small>
                          <strong>{activeRole.roleKey === "leadership" ? "Your institutional overview" : "Your teaching workspace"}</strong>
                        </div>
                        <span className="landing-role-ui__period">EDU 360</span>
                      </div>
                      <div className="landing-role-ui__stats">
                        {(activeRole.roleKey === "leadership"
                          ? ["Institution overview", "Pending approvals", "Priority follow-ups"]
                          : ["Assigned classes", "Assessment tasks", "Learner follow-ups"]
                        ).map((label) => (
                          <div key={label}>
                            <small>{label}</small>
                            <b>—</b>
                            <em>Awaiting connected live data</em>
                          </div>
                        ))}
                      </div>
                      <div className="landing-role-ui__panel">
                        <small>QUICK NAVIGATION · ROLE PERMISSIONS APPLY</small>
                        {(activeRole.roleKey === "leadership"
                          ? ["Institution overview", "Approval desk", "Priority follow-ups"]
                          : ["Teaching workspace", "Attendance & classes", "Assignments & marks"]
                        ).map((label, index) => (
                          <div className="landing-role-ui__line" key={label}>
                            <i /><span>{label}</span><b>{String(index + 1).padStart(2, "0")}</b>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                  {activeRole.roleKey === "students" && (
                    <div className="landing-role-ui">
                      <div className="landing-role-ui__heading">
                        <div><small>STUDENT DASHBOARD</small><strong>Your progress</strong></div>
                        <span className="landing-role-ui__period">This term</span>
                      </div>
                      <div className="landing-role-ui__stats landing-role-ui__stats--four">
                        {["Attendance", "Current CGPA", "Leave balance", "Fee status"].map((label) => (
                          <div key={label}><small>{label}</small><b>—</b><em>Not available yet</em></div>
                        ))}
                      </div>
                      <div className="landing-role-ui__columns">
                        <div className="landing-role-ui__panel">
                          <small>YOUR DASHBOARD SECTIONS</small>
                          {["Your timetable", "Next small steps", "From your campus", "Recent results"].map((label,index) => (
                            <div className="landing-role-ui__line" key={label}><i/><span>{label}</span><b>{index===2 ? "Notices" : "View"}</b></div>
                          ))}
                        </div>
                        <div className="landing-role-ui__panel landing-role-ui__insight">
                          <Icon name="calendar" size={28}/>
                          <b>Your learning rhythm</b>
                          <span>Study streaks are not a live feature in the current student dashboard.</span>
                        </div>
                      </div>
                    </div>
                  )}
                  {activeRole.roleKey === "parents" && (
                    <div className="landing-role-ui">
                      <div className="landing-role-ui__heading">
                        <div><small>PARENT DASHBOARD</small><strong>Linked students</strong></div>
                        <span className="landing-role-ui__period">Guardian access</span>
                      </div>
                      <div className="landing-role-ui__parent-notice">
                        <span><Icon name="users" size={17}/></span>
                        <div>
                          <small>CHILD ACCOUNT SELECTOR</small>
                          <b>Choose a linked child</b>
                          <p>The parent portal displays children associated with your guardian account.</p>
                        </div>
                      </div>
                      <div className="landing-role-ui__panel">
                        <small>AVAILABLE PROFILE DETAILS</small>
                        <div className="landing-role-ui__line"><i/><span>Linked student code</span><b>Available</b></div>
                        <div className="landing-role-ui__line"><i/><span>Student status</span><b>Available</b></div>
                        <div className="landing-role-ui__line"><i/><span>Attendance, fees &amp; notices</span><b>Not yet shown</b></div>
                      </div>
                    </div>
                  )}
                  <div className="landing-role-visual__foot">EDU 360 · EDUCATION DRIVEN BY UNITY <span>Illustrative role experience</span></div>
                </div>
              </div>
            )}
          </div>
        </section>

        {/* Connected operations overview */}
        <section className="landing-section landing-connected-section">
          <div className="landing-wrap">
            <div className="landing-section__head landing-section__head--center">
              <Kicker>ONE CONNECTED OPERATING PICTURE</Kicker>
              <h2 className="landing-section__title">The everyday work of education, working together.</h2>
              <p className="landing-section__desc">EDU 360 brings the people, processes and information behind an institution into a more coordinated digital workspace.</p>
            </div>
            <div className="landing-connected-grid">
              <article className="landing-connected-card"><span className="landing-connected-icon">01</span><h3>Academic operations</h3><p>Coordinate academic administration, attendance, examinations and the information teams need to keep learning operations organised.</p><small>ACADEMICS · ATTENDANCE · EXAMS</small></article>
              <article className="landing-connected-card"><span className="landing-connected-icon">02</span><h3>Institutional administration</h3><p>Bring administrative workflows, people operations, approvals and institutional oversight into a structured working environment.</p><small>ADMINISTRATION · HR · GOVERNANCE</small></article>
              <article className="landing-connected-card"><span className="landing-connected-icon">03</span><h3>Finance &amp; communication</h3><p>Support fee and finance workflows while making it easier to keep families and stakeholders informed through connected communication.</p><small>FEES · FINANCE · FAMILIES</small></article>
            </div>
          </div>
        </section>

        {/* Why institutions choose EDU 360 */}
        <section className="landing-section landing-why-section" id="why-edu360">
          <div className="landing-wrap">
            <div className="landing-why-layout">
              <div className="landing-why-intro">
                <Kicker>WHY EDU 360</Kicker>
                <h2 className="landing-section__title">Technology should bring your institution together—not add another silo.</h2>
                <p className="landing-section__desc">Choose a connected operating platform that helps teams spend less time chasing information and more time supporting education.</p>
                <a className="landing-why-cta" href="#quotation">Plan your institution's rollout ↗</a>
              </div>
              <div className="landing-why-points">
                <article><span>01</span><div><h3>One coordinated view</h3><p>Bring academic, administrative, finance and governance workflows into a shared institutional environment.</p></div></article>
                <article><span>02</span><div><h3>Clear responsibility</h3><p>Role-scoped access and structured workflows help teams understand what they can see, manage and action.</p></div></article>
                <article><span>03</span><div><h3>Better communication</h3><p>Keep institutional updates and notices easier to coordinate for staff, students and families through enabled communication workflows.</p></div></article>
                <article><span>04</span><div><h3>Room to grow</h3><p>Shape the module mix and rollout around your institution's size, operating model and campus requirements.</p></div></article>
              </div>
            </div>
          </div>
        </section>

        {/* Institution Solutions & How it works */}
        <section className="landing-section landing-section--soft" id="solutions">
          <div className="landing-wrap landing-split">
            <div>
              <Kicker>BUILT AROUND YOUR INSTITUTION</Kicker>
              <h2 className="landing-section__title">One platform. Different ways to lead.</h2>
              <p className="landing-section__desc">
                EDU 360 adapts to single schools, standalone colleges, and multi-campus universities
                with configurable modules and strict tenant isolation.
              </p>
              <ul className="landing-check-list">
                {solutions.map((sol) => (
                  <li key={sol.title}>
                    <span aria-hidden="true">✓</span>
                    <span>
                      <strong>{sol.title}:</strong> {sol.description}
                    </span>
                  </li>
                ))}
              </ul>
            </div>

            <div className="landing-steps" style={{ gridTemplateColumns: "1fr" }}>
              {steps.map((st) => (
                <article className="landing-step" key={st.stepNumber}>
                  <div className="landing-step__num">{st.stepNumber}</div>
                  <h3>{st.title}</h3>
                  <p>{st.description}</p>
                </article>
              ))}
            </div>
          </div>
        </section>

        {/* Published testimonials only: never display invented social proof. */}
        <section className="landing-section" id="stories">
          <div className="landing-wrap">
            <div className="landing-section__head">
              <Kicker>{landingData?.testimonials?.length ? "VOICES FROM THE COMMUNITY" : "EXPLORE THE WORKFLOWS"}</Kicker>
              <h2 className="landing-section__title">{landingData?.testimonials?.length ? "Real experiences. Shared in their own words." : "Connected workflows, from one institutional workspace."}</h2>
              <p className="landing-section__desc">
                {landingData?.testimonials?.length
                  ? "Hear from institution leaders, educators, and families. Platform administrators can publish video stories and written feedback directly from the Platform Console."
                  : "Explore how EDU 360 connects the everyday work across academics, governance, finance and family communication. Published community stories appear here when available."}
              </p>
            </div>

            <div className="landing-marquee-viewport">
              <div
                className={`landing-marquee-track${marqueePaused ? " landing-marquee-track--paused" : ""}${marqueeReverse ? " landing-marquee-track--reverse" : ""}`}
              >
                {marqueeItems.map((item, index) => {
                  const initials = item.authorName
                    .split(/\s+/)
                    .filter(Boolean)
                    .slice(0, 2)
                    .map((w) => w[0]?.toUpperCase())
                    .join("");
                  const posterSrc = publicMediaUrl(item.posterMediaId);
                  return (
                    <article
                      className="landing-quote-card"
                      key={`${item.testimonialId}-${index}`}
                    >
                      <div>
                        {item.kind === "VIDEO" && (
                          <div className="landing-video-thumb">
                            {posterSrc && index < testimonials.length ? (
                              <img src={posterSrc} alt={`Video thumbnail for ${item.authorName}`} />
                            ) : (
                              <span className="landing-video-thumb__fallback" aria-hidden="true">{initials || "EDU"}</span>
                            )}
                            <button
                              type="button"
                              className="landing-play-btn"
                              aria-label={`Play video testimonial from ${item.authorName}`}
                              onClick={() => {
                                setMarqueePaused(true);
                                setActiveVideoTestimonial(item);
                              }}
                            >
                              ▶
                            </button>
                          </div>
                        )}
                        <p className="landing-quote-card__text">{testimonials.length > 0 ? "“" + item.quoteText + "”" : item.quoteText}</p>
                      </div>
                      <div className="landing-quote-card__foot">
                        <div className="landing-quote-card__avatar" aria-hidden="true">
                          {initials || "E"}
                        </div>
                        <div>
                          <strong>{item.authorName}</strong>
                          <small>
                            {item.authorRole} · {item.institutionName}
                          </small>
                        </div>
                      </div>
                    </article>
                  );
                })}
              </div>
            </div>

            <div className="landing-marquee-controls">
              <button
                type="button"
                className="landing-icon-btn"
                aria-label={marqueePaused ? "Resume feedback carousel" : "Pause feedback carousel"}
                onClick={() => setMarqueePaused((p) => !p)}
              >
                {marqueePaused ? "▶" : "Ⅱ"}
              </button>
              <button
                type="button"
                className="landing-icon-btn"
                aria-label="Move feedback backward"
                onClick={() => {
                  setMarqueeReverse(true);
                  setMarqueePaused(false);
                }}
              >
                ←
              </button>
              <button
                type="button"
                className="landing-icon-btn"
                aria-label="Move feedback forward"
                onClick={() => {
                  setMarqueeReverse(false);
                  setMarqueePaused(false);
                }}
              >
                →
              </button>
              <span className="eds-field__hint" style={{ margin: 0 }}>
                {marqueePaused
                  ? "Paused · Select ▶ on a video card or resume movement"
                  : "Auto-moving · Hover or pause to explore"}
              </span>
            </div>
          </div>
        </section>

        {/* Quotation Estimation Request */}
        <section className="landing-section landing-section--soft" id="quotation">
          <div className="landing-wrap">
            <div className="landing-section__head">
              <Kicker>PLAN YOUR EDU 360 INVESTMENT</Kicker>
              <h2 className="landing-section__title">Get an estimate built around your institution.</h2>
              <p className="landing-section__desc">
                Share your institution profile and module priorities. Our team evaluates your needs
                against EDU 360’s official commercial catalog—never arbitrary instant pricing.
              </p>
            </div>

            <div className="landing-quote-layout">
              <aside className="landing-quote-intro">
                <Kicker>SCOPED TO YOUR NEEDS</Kicker>
                <h3>A proposal tailored to how you operate.</h3>
                <p>
                  Build a clear starting brief for a solution shaped around your institution,
                  your teams and the way your campuses operate.
                </p>
                <div className="landing-quote-benefits">
                  <div><span>01</span><p><strong>Right-sized scope</strong><small>Institution type, student capacity and campus structure</small></p></div>
                  <div><span>02</span><p><strong>Relevant modules</strong><small>Prioritise the workflows your teams need first</small></p></div>
                  <div><span>03</span><p><strong>Practical rollout</strong><small>Migration, onboarding and training preferences</small></p></div>
                </div>
                <div className="landing-quote-next">
                  <strong>What happens next?</strong>
                  <p>Your details are sent to the EDU 360 team for a requirements and commercial review. No instant or placeholder pricing is generated.</p>
                </div>
              </aside>

              <form className="landing-quote-form" onSubmit={handleQuotationSubmit} noValidate>
                <div className="landing-form-heading">
                  <div><span className="landing-form-kicker">YOUR PROJECT BRIEF</span><h3>Tell us what your institution needs.</h3><p>Complete the essentials below. You can add detail as you go.</p></div>
                  <div className="landing-form-progress"><strong>{quoteProgressPercent}%</strong><span>complete</span></div>
                </div>
                <div className="landing-progress-track" role="progressbar" aria-label="Quotation form completion" aria-valuemin={0} aria-valuemax={100} aria-valuenow={quoteProgressPercent}><span style={{ width: `${quoteProgressPercent}%` }} /></div>
                {qError && <Notice tone="danger">{qError}</Notice>}
                {qSuccess && <Notice tone="success">{qSuccess}</Notice>}
                <div className="landing-form-section-heading"><span>01</span><div><strong>Institution profile</strong><small>Help us understand your scale and structure</small></div></div>

                <div className="landing-form-row">
                  <SelectField
                    id="quote-inst-type"
                    label="Institution type"
                    required
                    value={instType}
                    onChange={(e) => setInstType(e.target.value)}
                    options={[
                      { value: "", label: "Select type" },
                      { value: "School", label: "School" },
                      { value: "Standalone college", label: "Standalone college" },
                      { value: "University", label: "University" },
                      { value: "Multi-campus group", label: "Multi-campus group" },
                    ]}
                  />
                  <SelectField
                    id="quote-student-range"
                    label="Approx. student strength"
                    required
                    value={studentRange}
                    onChange={(e) => setStudentRange(e.target.value)}
                    options={[
                      { value: "", label: "Select range" },
                      { value: "Below 500", label: "Below 500" },
                      { value: "500–1,500", label: "500–1,500" },
                      { value: "1,501–3,000", label: "1,501–3,000" },
                      { value: "3,001–5,000", label: "3,001–5,000" },
                      { value: "5,001–10,000", label: "5,001–10,000" },
                      { value: "Above 10,000", label: "Above 10,000" },
                    ]}
                  />
                </div>

                <div className="landing-form-section-heading"><span>02</span><div><strong>Choose your priorities</strong><small>Select the modules you would like to explore</small></div></div>
                <div className="landing-form-module-group">
                  <span className="landing-form-field-title">Modules you want to explore</span>
                  <div className="landing-module-grid">
                    {modules.map((mod) => (
                      <label className="landing-module-item" key={mod.moduleCode}>
                        <input
                          type="checkbox"
                          checked={selectedModules.includes(mod.moduleCode)}
                          onChange={() => toggleModule(mod.moduleCode)}
                        />
                        <span>{mod.itemName}</span>
                      </label>
                    ))}
                  </div>
                </div>

                <div className="landing-form-section-heading"><span>03</span><div><strong>Plan your rollout</strong><small>Tell us what kind of implementation support may help</small></div></div>
                <div className="landing-form-row">
                  <SelectField
                    id="quote-migration"
                    label="Data migration support"
                    value={migrationSupport}
                    onChange={(e) => setMigrationSupport(e.target.value)}
                    options={[
                      { value: "No / not sure", label: "No / not sure" },
                      { value: "Yes, from spreadsheets", label: "Yes, from spreadsheets" },
                      { value: "Yes, from an existing system", label: "Yes, from an existing system" },
                    ]}
                  />
                  <SelectField
                    id="quote-training"
                    label="Training & onboarding"
                    value={trainingMode}
                    onChange={(e) => setTrainingMode(e.target.value)}
                    options={[
                      { value: "Discuss with me", label: "Discuss with me" },
                      { value: "Remote training", label: "Remote training" },
                      { value: "On-site training", label: "On-site training" },
                    ]}
                  />
                </div>

                <div className="landing-summary-box">
                  <small className="eds-field__hint" style={{ margin: 0 }}>
                    YOUR QUOTATION SUMMARY
                  </small>
                  <strong>
                    {instType || studentRange
                      ? [instType || "Institution type pending", studentRange ? `${studentRange} students` : "Student range pending"].join(" · ")
                      : "Select your institution details to build your request."}
                  </strong>
                  <small className="eds-field__hint" style={{ margin: 0 }}>
                    {selectedModules.length > 0
                      ? `${selectedModules.length} module(s) selected · Final pricing is prepared after commercial review.`
                      : "Select the modules you want scoped. Final pricing is prepared after requirements review."}
                  </small>
                </div>

                <div className="landing-form-section-heading"><span>04</span><div><strong>Your contact details</strong><small>Who should our team contact about this request?</small></div></div>
                <div className="landing-form-row">
                  <TextField
                    id="quote-contact-name"
                    label="Your name"
                    required
                    placeholder="Full name"
                    value={qName}
                    onChange={(e) => setQName(e.target.value)}
                  />
                  <TextField
                    id="quote-contact-email"
                    label="Work email"
                    type="email"
                    required
                    placeholder="name@institution.edu"
                    value={qEmail}
                    onChange={(e) => setQEmail(e.target.value)}
                  />
                </div>

                <div className="landing-form-row">
                  <TextField
                    id="quote-contact-phone"
                    label="Phone number"
                    type="tel"
                    placeholder="+91 98765 43210"
                    value={qPhone}
                    onChange={(e) => setQPhone(e.target.value)}
                  />
                  <TextField
                    id="quote-institution-name"
                    label="Institution name"
                    required
                    placeholder="Institution name"
                    value={qInstitution}
                    onChange={(e) => setQInstitution(e.target.value)}
                  />
                </div>

                <TextAreaField
                  id="quote-notes"
                  label="Anything else we should know? (optional)"
                  rows={3}
                  placeholder="Share your priorities, current systems, or timeline..."
                  value={qNotes}
                  onChange={(e) => setQNotes(e.target.value)}
                />

                <div className="landing-form-submit">
                  <Button type="submit" size="lg" busy={qBusy}>
                    {qBusy ? "Submitting request…" : "Send my project brief ↗"}
                  </Button>
                  <small>Submitting this form does not commit your institution to a purchase.</small>
                </div>
              </form>
            </div>
          </div>
        </section>

        {/* Demo CTA */}
        <section className="landing-section" id="demo">
          <div className="landing-wrap">
            <div className="landing-cta">
              <div>
                <Kicker>TAKE THE NEXT STEP</Kicker>
                <h2>See what connected education looks like in action.</h2>
                <p>
                  Tell us about your campus priorities. We will schedule a live walkthrough tailored
                  to your leadership, faculty, and administrative teams.
                </p>
              </div>
              <Button
                variant="secondary"
                size="lg"
                onClick={() => {
                  setDemoSuccess("");
                  setDemoError("");
                  setDemoModalOpen(true);
                }}
              >
                Request a demo ↗
              </Button>
            </div>
          </div>
        </section>
      </main>

      <footer className="landing-footer">
        <div className="landing-wrap">
          <div className="landing-footer__grid">
            <div>
              <a href="#top" className="landing-brand">
                <Edu360Logo className="landing-brand__logo" />
              </a>
              <p className="landing-card__desc" style={{ marginTop: "var(--space-3)" }}>
                Education Driven by Unity
              </p>
              <p className="landing-card__desc" style={{ marginTop: "var(--space-2)" }}>
                {footerTagline}
              </p>
            </div>
            <div className="landing-footer__links">
              <a href="#platform">Platform</a>
              <a href="#roles">By Role</a>
              <a href="#solutions">Solutions</a>
              <a href="#stories">Stories</a>
              <a href="#quotation">Quotation</a>
              <a href={`mailto:${contactEmail}`}>{contactEmail}</a>
            </div>
          </div>
          <div className="landing-footer__bottom">
            <span>© {new Date().getFullYear()} EDU 360. Higher Education Administration &amp; Governance.</span>
            <button
              type="button"
              className="app-text-link"
              style={{ background: "none", border: 0, cursor: "pointer", padding: 0 }}
              onClick={() => setSelectorOpen(true)}
            >
              Sign in to your institution ↗
            </button>
          </div>
        </div>
      </footer>

      <InstitutionSelectorModal
        isOpen={selectorOpen}
        onClose={() => setSelectorOpen(false)}
        onNavigate={onNavigate}
      />

      {/* Video Testimonial Modal */}
      <Modal
        isOpen={Boolean(activeVideoTestimonial)}
        onClose={() => setActiveVideoTestimonial(null)}
        kicker="VIDEO TESTIMONIAL"
        title={
          activeVideoTestimonial
            ? `${activeVideoTestimonial.authorName} · ${activeVideoTestimonial.institutionName}`
            : "Video Testimonial"
        }
        size="lg"
      >
        {activeVideoTestimonial && (
          <div style={{ display: "grid", gap: "var(--space-4)" }}>
            <div className="landing-video-player">
              {activeVideoTestimonial.videoMediaId ? (
                <video
                  controls
                  autoPlay
                  poster={publicMediaUrl(activeVideoTestimonial.posterMediaId) ?? undefined}
                  src={publicMediaUrl(activeVideoTestimonial.videoMediaId) ?? undefined}
                />
              ) : activeVideoTestimonial.videoUrl ? (
                activeVideoTestimonial.videoUrl.endsWith(".mp4") ||
                activeVideoTestimonial.videoUrl.endsWith(".webm") ? (
                  <video controls autoPlay src={activeVideoTestimonial.videoUrl} />
                ) : (
                  <iframe
                    src={activeVideoTestimonial.videoUrl}
                    title={`Video testimonial from ${activeVideoTestimonial.authorName}`}
                    allow="autoplay; fullscreen"
                    allowFullScreen
                  />
                )
              ) : (
                <div style={{ padding: "var(--space-6)", textAlign: "center" }}>
                  <p className="landing-section__desc" style={{ margin: 0 }}>
                    No video asset attached to this testimonial yet. Platform admins can upload an
                    MP4/WebM video or link an approved video URL from the Platform Console.
                  </p>
                </div>
              )}
            </div>
            <p className="landing-quote-card__text">“{activeVideoTestimonial.quoteText}”</p>
          </div>
        )}
      </Modal>

      {/* Request a Demo Modal */}
      <Modal
        isOpen={demoModalOpen}
        onClose={() => setDemoModalOpen(false)}
        kicker="PERSONALISED WALKTHROUGH"
        title="Request an EDU 360 Demo"
        description="Share your contact details and our team will arrange a tailored platform walkthrough."
        size="md"
      >
        <form className="selector-form" onSubmit={handleDemoSubmit} noValidate>
          {demoError && <Notice tone="danger">{demoError}</Notice>}
          {demoSuccess && <Notice tone="success">{demoSuccess}</Notice>}
          <TextField
            id="demo-name"
            label="Your name"
            required
            placeholder="Full name"
            value={demoName}
            onChange={(e) => setDemoName(e.target.value)}
          />
          <TextField
            id="demo-email"
            label="Work email"
            type="email"
            required
            placeholder="name@institution.edu"
            value={demoEmail}
            onChange={(e) => setDemoEmail(e.target.value)}
          />
          <TextField
            id="demo-phone"
            label="Phone number"
            type="tel"
            placeholder="+91 98765 43210"
            value={demoPhone}
            onChange={(e) => setDemoPhone(e.target.value)}
          />
          <TextField
            id="demo-institution"
            label="Institution name"
            required
            placeholder="School, college, or university name"
            value={demoInstitution}
            onChange={(e) => setDemoInstitution(e.target.value)}
          />
          <TextAreaField
            id="demo-notes"
            label="Areas you want to see (optional)"
            rows={3}
            placeholder="e.g. Admissions, academic governance, fees, parent communication..."
            value={demoNotes}
            onChange={(e) => setDemoNotes(e.target.value)}
          />
          <Button type="submit" size="lg" block busy={demoBusy}>
            {demoBusy ? "Sending request…" : "Submit demo request"}
          </Button>
        </form>
      </Modal>
    </div>
  );
}

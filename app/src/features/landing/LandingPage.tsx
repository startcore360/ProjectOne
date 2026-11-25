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
      "Monitor admissions, attendance, fee collections and pending approvals across campuses.",
      "Enforce term locks, role delegations and multi-level approval chains.",
      "Review compliance-ready audit logs and executive analytics in real time.",
    ],
  },
  {
    roleKey: "faculty",
    label: "Teachers & Faculty",
    headline: "Spend less time on paperwork and more time teaching.",
    points: [
      "Mark daily or period attendance and manage class schedules cleanly.",
      "Publish assignments, record marks and track student progress.",
      "Submit leave requests and view department notices from any device.",
    ],
  },
  {
    roleKey: "students",
    label: "Students",
    headline: "Every academic update, timetable and fee receipt in one portal.",
    points: [
      "Check daily timetables, attendance records and upcoming exam schedules.",
      "Submit assignments, view published results and track academic standing.",
      "Access institutional circulars, self-service requests and active sessions.",
    ],
  },
  {
    roleKey: "parents",
    label: "Parents & Guardians",
    headline: "Stay connected to your child's progress and school updates.",
    points: [
      "Receive timely attendance alerts, exam updates and official notices.",
      "Review fee schedules, download receipts and track due dates easily.",
      "Submit guardian requests and communicate with the institution securely.",
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

const FALLBACK_TESTIMONIALS: LandingTestimonialItem[] = [];

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
    } catch {
      setQError("Unable to submit quotation request right now. Please try again shortly.");
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
                <div className="landing-screenshot-frame">
                  <div className="landing-screenshot-frame__bar" aria-hidden="true">
                    <span className="landing-screenshot-frame__dot" />
                    <span className="landing-screenshot-frame__dot" />
                    <span className="landing-screenshot-frame__dot" />
                    <span>EDU 360 · Portal Sign-in &amp; Access</span>
                  </div>
                  <img
                    src={showcaseImageUrl}
                    alt="EDU 360 real portal sign-in and workspace interface"
                  />
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
        {testimonials.length > 0 && (
        <section className="landing-section" id="stories">
          <div className="landing-wrap">
            <div className="landing-section__head">
              <Kicker>VOICES FROM THE COMMUNITY</Kicker>
              <h2 className="landing-section__title">Real experiences. Shared in their own words.</h2>
              <p className="landing-section__desc">
                Hear from institution leaders, educators, and families. Platform administrators can
                publish video stories and written feedback directly from the Platform Console.
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
                            {posterSrc && (
                              <img src={posterSrc} alt={`Video thumbnail for ${item.authorName}`} />
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
                        <p className="landing-quote-card__text">“{item.quoteText}”</p>
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
        )}

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

import { type FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import {
  Badge,
  Button,
  DataTable,
  MetricCard,
  Modal,
  Notice,
  PageHeader,
  Panel,
  SearchBox,
  SelectField,
  StatusBadge,
  Tabs,
  TextAreaField,
  TextField,
  toast,
} from "../../../../design-system/components";
import {
  landingApi,
  publicMediaUrl,
  type DirectoryTenantItem,
  type LandingTestimonialItem,
  type PlatformLandingCmsData,
  type PlatformLandingEnquiryItem,
} from "../../../landing/api/landingApi";

type CmsTab = "content" | "testimonials" | "directory" | "enquiries";

const EMPTY_TESTIMONIAL = {
  kind: "TEXT" as "TEXT" | "VIDEO",
  authorName: "",
  authorRole: "",
  institutionName: "",
  quoteText: "",
  videoUrl: "",
  videoMediaId: "" as string,
  posterMediaId: "" as string,
  displayOrder: 0,
  published: true,
};

const MODULE_NAMES: Record<string, string> = {
  ACADEMICS: "Academic Management",
  ATTENDANCE_EXAMS: "Attendance & Exams",
  FEES_FINANCE: "Fees & Finance",
  PARENT_COMMS: "Parent Communication",
  HR_LEAVE: "HR & Leave Workflows",
  GOVERNANCE: "Reports & Governance",
};

function formatModuleCode(code: string): string {
  if (MODULE_NAMES[code]) return MODULE_NAMES[code];
  return code
    .split("_")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
    .join(" ");
}

function parseModules(raw: string | undefined): string[] {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) return parsed.map(String);
  } catch {
    if (raw.startsWith("[") && raw.endsWith("]")) {
      return raw
        .slice(1, -1)
        .split(",")
        .map((s) => s.trim().replace(/^["']|["']$/g, ""))
        .filter(Boolean);
    }
  }
  return [raw];
}

interface QuoteLineItem {
  code?: string;
  description?: string;
  quantity?: number;
  unitAmount?: number;
  amount?: number;
}

interface PlanQuoteItem {
  planId?: string;
  planName?: string;
  pricingType?: string;
  eligible?: boolean;
  recommendation?: string;
  fitReasons?: string[];
  unmetRequirements?: string[];
  subtotal?: number;
  discount?: number;
  total?: number;
  tax?: number;
  lines?: QuoteLineItem[];
}

interface CommercialQuoteSnapshot {
  currencyCode?: string;
  billingCycleMonths?: number;
  plans?: PlanQuoteItem[];
  lowestCostPlanId?: string;
  bestFitPlanId?: string;
  note?: string;
}

/**
 * Modern, theme-aware file upload field replacing ugly browser-native buttons
 */
function FileUploadField({
  id,
  label,
  accept,
  hint,
  disabled,
  fileName,
  onChange,
  onClear,
}: {
  id: string;
  label: string;
  accept: string;
  hint?: string;
  disabled?: boolean;
  fileName?: string | null;
  onChange: (file: File) => void;
  onClear?: () => void;
}) {
  return (
    <div style={{ display: "grid", gap: "var(--space-2)" }}>
      <span style={{ fontWeight: "var(--fw-medium)", fontSize: "var(--fs-body-sm)" }}>
        {label}
      </span>
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          gap: "var(--space-3)",
          padding: "var(--space-2) var(--space-3)",
          border: "1px dashed var(--color-border-divider)",
          borderRadius: "var(--radius-md)",
          background: "var(--color-bg-subtle)",
          flexWrap: "wrap",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)", minWidth: 0, flex: 1 }}>
          <span style={{ fontSize: "16px", color: "var(--color-text-brand)" }} aria-hidden="true">
            📁
          </span>
          <span
            style={{
              fontSize: "var(--fs-caption)",
              color: fileName ? "var(--color-text)" : "var(--color-text-muted)",
              overflow: "hidden",
              textOverflow: "ellipsis",
              whiteSpace: "nowrap",
            }}
          >
            {fileName || "No file selected"}
          </span>
        </div>

        <div style={{ display: "flex", gap: "var(--space-2)", alignItems: "center" }}>
          <input
            id={id}
            type="file"
            accept={accept}
            disabled={disabled}
            style={{ display: "none" }}
            onChange={(e) => {
              const f = e.target.files?.[0];
              if (f) onChange(f);
              e.target.value = "";
            }}
          />
          <label
            htmlFor={id}
            style={{
              display: "inline-flex",
              alignItems: "center",
              justifyContent: "center",
              gap: "6px",
              padding: "6px 14px",
              borderRadius: "var(--radius-md)",
              fontSize: "var(--fs-body-sm)",
              fontWeight: "var(--fw-medium)",
              background: "var(--color-bg-surface)",
              color: "var(--color-text)",
              border: "1px solid var(--color-border-divider)",
              boxShadow: "var(--elevation-1)",
              cursor: disabled ? "not-allowed" : "pointer",
              transition: "all 140ms ease",
              margin: 0,
              userSelect: "none",
            }}
            onMouseEnter={(e) => {
              if (!disabled) {
                e.currentTarget.style.borderColor = "var(--color-text-brand)";
                e.currentTarget.style.background = "var(--color-bg-brand-soft, var(--color-bg-subtle))";
              }
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.borderColor = "var(--color-border-divider)";
              e.currentTarget.style.background = "var(--color-bg-surface)";
            }}
          >
            <span>{disabled ? "Uploading…" : fileName ? "Replace file ↗" : "Choose file ↗"}</span>
          </label>
          {fileName && onClear && (
            <Button size="sm" variant="secondary" onClick={onClear} type="button">
              Remove
            </Button>
          )}
        </div>
      </div>
      {hint && <small className="app-muted" style={{ fontSize: "11px" }}>{hint}</small>}
    </div>
  );
}

export function PlatformLandingCmsPage() {
  const [tab, setTab] = useState<CmsTab>("content");
  const [cms, setCms] = useState<PlatformLandingCmsData | null>(null);
  const [enquiries, setEnquiries] = useState<PlatformLandingEnquiryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // Content form state
  const [heroEyebrow, setHeroEyebrow] = useState("");
  const [heroTitle, setHeroTitle] = useState("");
  const [heroSubtitle, setHeroSubtitle] = useState("");
  const [heroNote, setHeroNote] = useState("");
  const [trustLabel, setTrustLabel] = useState("");
  const [contactEmail, setContactEmail] = useState("");
  const [footerTagline, setFooterTagline] = useState("");
  const [heroLightMediaId, setHeroLightMediaId] = useState<string | null>(null);
  const [heroDarkMediaId, setHeroDarkMediaId] = useState<string | null>(null);
  const [showcaseLightMediaId, setShowcaseLightMediaId] = useState<string | null>(null);
  const [showcaseDarkMediaId, setShowcaseDarkMediaId] = useState<string | null>(null);
  const [savingConfig, setSavingConfig] = useState(false);
  const [uploadingSlot, setUploadingSlot] = useState<string | null>(null);

  // Testimonial modal state
  const [testimonialModalOpen, setTestimonialModalOpen] = useState(false);
  const [editingTestimonial, setEditingTestimonial] = useState<LandingTestimonialItem | null>(null);
  const [tForm, setTForm] = useState(EMPTY_TESTIMONIAL);
  const [savingTestimonial, setSavingTestimonial] = useState(false);
  const [uploadingTestimonialMedia, setUploadingTestimonialMedia] = useState(false);
  const [testimonialFilter, setTestimonialFilter] = useState<"ALL" | "PUBLISHED" | "DRAFT" | "VIDEO">("ALL");

  // Directory filter state
  const [directorySearch, setDirectorySearch] = useState("");
  const [directoryFilter, setDirectoryFilter] = useState<"ALL" | "LISTED" | "HIDDEN">("ALL");

  // Enquiry filter & detail modal state
  const [enquirySearch, setEnquirySearch] = useState("");
  const [enquiryTypeFilter, setEnquiryTypeFilter] = useState<"ALL" | "QUOTATION" | "DEMO">("ALL");
  const [enquiryStatusFilter, setEnquiryStatusFilter] = useState<"ALL" | "NEW" | "CONTACTED" | "QUALIFIED" | "CLOSED">("ALL");
  const [selectedEnquiry, setSelectedEnquiry] = useState<PlatformLandingEnquiryItem | null>(null);
  const [enquiryStatusInput, setEnquiryStatusInput] = useState<"NEW" | "CONTACTED" | "QUALIFIED" | "CLOSED">("NEW");
  const [enquiryNotesInput, setEnquiryNotesInput] = useState("");
  const [savingEnquiry, setSavingEnquiry] = useState(false);

  const loadAll = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [cmsData, enquiryData] = await Promise.all([
        landingApi.getCmsOverview(),
        landingApi.listEnquiries(),
      ]);
      setCms(cmsData);
      setEnquiries(enquiryData);

      const c = cmsData.config;
      setHeroEyebrow(c.heroEyebrow);
      setHeroTitle(c.heroTitle);
      setHeroSubtitle(c.heroSubtitle);
      setHeroNote(c.heroNote);
      setTrustLabel(c.trustLabel);
      setContactEmail(c.contactEmail);
      setFooterTagline(c.footerTagline);
      setHeroLightMediaId(c.heroLightMediaId);
      setHeroDarkMediaId(c.heroDarkMediaId);
      setShowcaseLightMediaId(c.showcaseLightMediaId);
      setShowcaseDarkMediaId(c.showcaseDarkMediaId);
    } catch {
      setError("Failed to load landing CMS configuration.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadAll();
  }, [loadAll]);

  const handleSaveConfig = async (e: FormEvent) => {
    e.preventDefault();
    if (!cms) return;
    setSavingConfig(true);
    try {
      const updated = await landingApi.updateConfig(
        {
          heroEyebrow,
          heroTitle,
          heroSubtitle,
          heroNote,
          trustLabel,
          whyChooseJson: cms.config.whyChooseJson,
          roleBenefitsJson: cms.config.roleBenefitsJson,
          solutionsJson: cms.config.solutionsJson,
          stepsJson: cms.config.stepsJson,
          contactEmail,
          footerTagline,
          heroLightMediaId,
          heroDarkMediaId,
          showcaseLightMediaId,
          showcaseDarkMediaId,
        },
        cms.config.rowVersion,
      );
      setCms((prev) => (prev ? { ...prev, config: updated } : prev));
      toast.success("Landing page configuration saved.");
    } catch {
      toast.error("Could not save landing page configuration.");
    } finally {
      setSavingConfig(false);
    }
  };

  const handleSlotImageUpload = async (
    slot: "heroLight" | "heroDark" | "showcaseLight" | "showcaseDark",
    file: File,
  ) => {
    setUploadingSlot(slot);
    try {
      const res = await landingApi.uploadMedia(file, "IMAGE");
      if (slot === "heroLight") setHeroLightMediaId(res.mediaId);
      if (slot === "heroDark") setHeroDarkMediaId(res.mediaId);
      if (slot === "showcaseLight") setShowcaseLightMediaId(res.mediaId);
      if (slot === "showcaseDark") setShowcaseDarkMediaId(res.mediaId);
      toast.success(`Uploaded & scanned ${res.fileName}. Click "Save page content" to publish.`);
    } catch {
      toast.error("Upload rejected or failed security scan.");
    } finally {
      setUploadingSlot(null);
    }
  };

  const openNewTestimonial = () => {
    setEditingTestimonial(null);
    setTForm({
      ...EMPTY_TESTIMONIAL,
      displayOrder: cms?.testimonials.length ?? 0,
    });
    setTestimonialModalOpen(true);
  };

  const openEditTestimonial = (item: LandingTestimonialItem) => {
    setEditingTestimonial(item);
    setTForm({
      kind: item.kind,
      authorName: item.authorName,
      authorRole: item.authorRole,
      institutionName: item.institutionName,
      quoteText: item.quoteText,
      videoUrl: item.videoUrl ?? "",
      videoMediaId: item.videoMediaId ?? "",
      posterMediaId: item.posterMediaId ?? "",
      displayOrder: item.displayOrder,
      published: item.published,
    });
    setTestimonialModalOpen(true);
  };

  const handleUploadTestimonialAsset = async (kind: "VIDEO" | "IMAGE", file: File) => {
    setUploadingTestimonialMedia(true);
    try {
      const res = await landingApi.uploadMedia(file, kind);
      if (kind === "VIDEO") {
        setTForm((p) => ({ ...p, videoMediaId: res.mediaId }));
      } else {
        setTForm((p) => ({ ...p, posterMediaId: res.mediaId }));
      }
      toast.success(`Uploaded & scanned ${res.fileName}.`);
    } catch {
      toast.error("Media upload failed or was rejected by the security scanner.");
    } finally {
      setUploadingTestimonialMedia(false);
    }
  };

  const handleSaveTestimonial = async (e: FormEvent) => {
    e.preventDefault();
    setSavingTestimonial(true);
    try {
      const payload = {
        kind: tForm.kind,
        authorName: tForm.authorName,
        authorRole: tForm.authorRole,
        institutionName: tForm.institutionName,
        quoteText: tForm.quoteText,
        videoUrl: tForm.videoUrl.trim() || null,
        videoMediaId: tForm.videoMediaId.trim() || null,
        posterMediaId: tForm.posterMediaId.trim() || null,
        displayOrder: Number(tForm.displayOrder) || 0,
        published: tForm.published,
      };
      if (editingTestimonial) {
        await landingApi.updateTestimonial(
          editingTestimonial.testimonialId,
          payload,
          editingTestimonial.rowVersion,
        );
        toast.success("Testimonial updated.");
      } else {
        await landingApi.createTestimonial(payload);
        toast.success("Testimonial created.");
      }
      setTestimonialModalOpen(false);
      await loadAll();
    } catch {
      toast.error("Could not save testimonial.");
    } finally {
      setSavingTestimonial(false);
    }
  };

  const handleDeleteTestimonial = async (id: string) => {
    try {
      await landingApi.deleteTestimonial(id);
      toast.success("Testimonial deleted.");
      await loadAll();
    } catch {
      toast.error("Could not delete testimonial.");
    }
  };

  const handleMoveTestimonial = async (index: number, delta: -1 | 1) => {
    if (!cms) return;
    const list = [...cms.testimonials];
    const target = index + delta;
    if (target < 0 || target >= list.length) return;
    const [moved] = list.splice(index, 1);
    list.splice(target, 0, moved);
    try {
      const updated = await landingApi.reorderTestimonials(list.map((t) => t.testimonialId));
      setCms((prev) => (prev ? { ...prev, testimonials: updated } : prev));
    } catch {
      toast.error("Could not reorder testimonials.");
    }
  };

  const handleDirectoryToggle = async (tenant: DirectoryTenantItem) => {
    try {
      const updated = await landingApi.toggleDirectory(tenant.tenantId, !tenant.listedInDirectory);
      setCms((prev) =>
        prev
          ? {
              ...prev,
              directoryTenants: prev.directoryTenants.map((t) =>
                t.tenantId === updated.tenantId ? updated : t,
              ),
            }
          : prev,
      );
      toast.success(
        updated.listedInDirectory
          ? `${updated.tenantName} added to public sign-in dropdown.`
          : `${updated.tenantName} removed from public sign-in dropdown.`,
      );
    } catch {
      toast.error("Failed to update directory visibility.");
    }
  };

  const handleSaveEnquiryStatus = async (e: FormEvent) => {
    e.preventDefault();
    if (!selectedEnquiry) return;
    setSavingEnquiry(true);
    try {
      const updated = await landingApi.updateEnquiryStatus(
        selectedEnquiry.enquiryId,
        enquiryStatusInput,
        enquiryNotesInput,
        selectedEnquiry.rowVersion,
      );
      setEnquiries((prev) =>
        prev.map((item) => (item.enquiryId === updated.enquiryId ? updated : item)),
      );
      setSelectedEnquiry(updated);
      toast.success("Enquiry status updated.");
    } catch {
      toast.error("Could not update enquiry status.");
    } finally {
      setSavingEnquiry(false);
    }
  };

  // Filtered directory tenants
  const filteredDirectoryTenants = useMemo(() => {
    if (!cms) return [];
    let list = cms.directoryTenants;
    if (directoryFilter === "LISTED") list = list.filter((t) => t.listedInDirectory);
    if (directoryFilter === "HIDDEN") list = list.filter((t) => !t.listedInDirectory);
    const q = directorySearch.trim().toLowerCase();
    if (!q) return list;
    return list.filter(
      (t) =>
        t.tenantName.toLowerCase().includes(q) ||
        t.subdomain.toLowerCase().includes(q) ||
        t.tenantType.toLowerCase().includes(q),
    );
  }, [cms, directoryFilter, directorySearch]);

  // Filtered testimonials
  const filteredTestimonials = useMemo(() => {
    if (!cms) return [];
    let list = cms.testimonials;
    if (testimonialFilter === "PUBLISHED") list = list.filter((t) => t.published);
    if (testimonialFilter === "DRAFT") list = list.filter((t) => !t.published);
    if (testimonialFilter === "VIDEO") list = list.filter((t) => t.kind === "VIDEO");
    return list;
  }, [cms, testimonialFilter]);

  // Filtered enquiries
  const filteredEnquiries = useMemo(() => {
    let list = enquiries;
    if (enquiryTypeFilter !== "ALL") list = list.filter((e) => e.enquiryType === enquiryTypeFilter);
    if (enquiryStatusFilter !== "ALL") list = list.filter((e) => e.status === enquiryStatusFilter);
    const q = enquirySearch.trim().toLowerCase();
    if (!q) return list;
    return list.filter(
      (e) =>
        e.institutionName.toLowerCase().includes(q) ||
        e.contactName.toLowerCase().includes(q) ||
        e.contactEmail.toLowerCase().includes(q) ||
        (e.contactPhone && e.contactPhone.toLowerCase().includes(q)) ||
        (e.notes && e.notes.toLowerCase().includes(q)),
    );
  }, [enquiries, enquirySearch, enquiryStatusFilter, enquiryTypeFilter]);

  // Parsed quote snapshot for the active modal
  const quoteSnapshotData = useMemo<{
    parsed: CommercialQuoteSnapshot | null;
    rawFormatted: string;
    isStructured: boolean;
  }>(() => {
    if (!selectedEnquiry?.quoteSnapshotJson) {
      return { parsed: null, rawFormatted: "", isStructured: false };
    }
    try {
      const parsed = JSON.parse(selectedEnquiry.quoteSnapshotJson);
      const isStructured = Boolean(parsed && Array.isArray(parsed.plans) && parsed.plans.length > 0);
      return {
        parsed,
        rawFormatted: JSON.stringify(parsed, null, 2),
        isStructured,
      };
    } catch {
      return {
        parsed: null,
        rawFormatted: selectedEnquiry.quoteSnapshotJson,
        isStructured: false,
      };
    }
  }, [selectedEnquiry?.quoteSnapshotJson]);

  const parsedEnquiryModules = useMemo(
    () => parseModules(selectedEnquiry?.selectedModules),
    [selectedEnquiry?.selectedModules],
  );

  return (
    <div className="app-page">
      <PageHeader
        title="Landing Page & Enquiries"
        description="Customize public marketing copy, manage text & video testimonials, opt institutions into the sign-in dropdown, and review quotation & demo leads."
        actions={
          <Button variant="secondary" onClick={() => void loadAll()} disabled={loading}>
            Refresh
          </Button>
        }
      />

      {error && <Notice tone="danger">{error}</Notice>}

      {/* Primary Navigation Tabs */}
      <Tabs
        activeId={tab}
        onChange={(id) => setTab(id as CmsTab)}
        items={[
          { id: "content", label: "Page Content & Photos" },
          { id: "testimonials", label: `Testimonials & Videos (${cms?.testimonials.length ?? 0})` },
          { id: "directory", label: `Sign-in Directory (${cms?.directoryTenants.length ?? 0})` },
          { id: "enquiries", label: `Enquiries & Quotes (${enquiries.length})` },
        ]}
      />

      {/* ────────────────────────────────────────────────────────────────────── */}
      {/* TAB 1: PAGE CONTENT & PHOTOS                                           */}
      {/* ────────────────────────────────────────────────────────────────────── */}
      {tab === "content" && cms && (
        <Panel
          title="Public Marketing Copy & Platform Media Assets"
          description="Update hero copy, taglines, trust labels, and upload official platform screenshots with automatic malware scanning."
        >
          <form onSubmit={handleSaveConfig} style={{ display: "grid", gap: "var(--space-5)" }}>
            {/* Core Text Content */}
            <div style={{ display: "grid", gap: "var(--space-4)" }}>
              <div className="app-form-grid">
                <TextField
                  id="cms-hero-eyebrow"
                  label="Hero eyebrow"
                  value={heroEyebrow}
                  onChange={(e) => setHeroEyebrow(e.target.value)}
                  required
                />
                <TextField
                  id="cms-hero-note"
                  label="Hero audience note"
                  value={heroNote}
                  onChange={(e) => setHeroNote(e.target.value)}
                  required
                />
              </div>
              <TextField
                id="cms-hero-title"
                label="Hero headline"
                value={heroTitle}
                onChange={(e) => setHeroTitle(e.target.value)}
                required
              />
              <TextAreaField
                id="cms-hero-subtitle"
                label="Hero subtitle"
                rows={3}
                value={heroSubtitle}
                onChange={(e) => setHeroSubtitle(e.target.value)}
                required
              />
              <div className="app-form-grid">
                <TextField
                  id="cms-trust-label"
                  label="Trust strip label"
                  value={trustLabel}
                  onChange={(e) => setTrustLabel(e.target.value)}
                  required
                />
                <TextField
                  id="cms-contact-email"
                  label="Public contact email"
                  type="email"
                  value={contactEmail}
                  onChange={(e) => setContactEmail(e.target.value)}
                  required
                />
              </div>
              <TextField
                id="cms-footer-tagline"
                label="Footer tagline"
                value={footerTagline}
                onChange={(e) => setFooterTagline(e.target.value)}
                required
              />
            </div>

            {/* Platform Media Asset Cards */}
            <div style={{ borderTop: "1px solid var(--color-border-divider)", paddingTop: "var(--space-4)" }}>
              <h3 style={{ fontSize: "var(--fs-h3)", fontWeight: "var(--fw-bold)", marginBottom: "var(--space-1)" }}>
                Platform Screenshots &amp; UI Assets
              </h3>
              <p className="app-muted" style={{ fontSize: "var(--fs-caption)", marginBottom: "var(--space-4)" }}>
                PNG, JPEG, WebP up to 10 MB. All uploaded files are automatically scanned by ClamAV for malware.
              </p>

              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))",
                  gap: "var(--space-4)",
                }}
              >
                {/* Hero Light */}
                <div
                  style={{
                    padding: "var(--space-4)",
                    border: "1px solid var(--color-border-divider)",
                    borderRadius: "var(--radius-lg)",
                    background: "var(--color-bg-surface)",
                    display: "grid",
                    gap: "var(--space-3)",
                  }}
                >
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                    <strong>Hero Platform (Light)</strong>
                    <Badge tone="neutral">Light Theme</Badge>
                  </div>
                  <div
                    style={{
                      height: "140px",
                      borderRadius: "var(--radius-md)",
                      background: "var(--color-bg-subtle)",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      overflow: "hidden",
                      border: "1px dashed var(--color-border-divider)",
                    }}
                  >
                    {heroLightMediaId ? (
                      <img
                        src={publicMediaUrl(heroLightMediaId) ?? undefined}
                        alt="Hero Light Screenshot Preview"
                        style={{ width: "100%", height: "100%", objectFit: "cover" }}
                      />
                    ) : (
                      <span className="app-muted" style={{ fontSize: "var(--fs-caption)" }}>Default platform image active</span>
                    )}
                  </div>
                  <FileUploadField
                    id="upload-hero-light"
                    label="Choose replacement screenshot"
                    accept="image/png,image/jpeg,image/webp"
                    disabled={uploadingSlot === "heroLight"}
                    fileName={heroLightMediaId ? "Custom image published" : null}
                    onChange={(file) => void handleSlotImageUpload("heroLight", file)}
                    onClear={heroLightMediaId ? () => setHeroLightMediaId(null) : undefined}
                  />
                </div>

                {/* Hero Dark */}
                <div
                  style={{
                    padding: "var(--space-4)",
                    border: "1px solid var(--color-border-divider)",
                    borderRadius: "var(--radius-lg)",
                    background: "var(--color-bg-surface)",
                    display: "grid",
                    gap: "var(--space-3)",
                  }}
                >
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                    <strong>Hero Platform (Dark)</strong>
                    <Badge tone="neutral">Dark Theme</Badge>
                  </div>
                  <div
                    style={{
                      height: "140px",
                      borderRadius: "var(--radius-md)",
                      background: "var(--color-bg-subtle)",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      overflow: "hidden",
                      border: "1px dashed var(--color-border-divider)",
                    }}
                  >
                    {heroDarkMediaId ? (
                      <img
                        src={publicMediaUrl(heroDarkMediaId) ?? undefined}
                        alt="Hero Dark Screenshot Preview"
                        style={{ width: "100%", height: "100%", objectFit: "cover" }}
                      />
                    ) : (
                      <span className="app-muted" style={{ fontSize: "var(--fs-caption)" }}>Default platform image active</span>
                    )}
                  </div>
                  <FileUploadField
                    id="upload-hero-dark"
                    label="Choose replacement screenshot"
                    accept="image/png,image/jpeg,image/webp"
                    disabled={uploadingSlot === "heroDark"}
                    fileName={heroDarkMediaId ? "Custom image published" : null}
                    onChange={(file) => void handleSlotImageUpload("heroDark", file)}
                    onClear={heroDarkMediaId ? () => setHeroDarkMediaId(null) : undefined}
                  />
                </div>

                {/* Showcase Light */}
                <div
                  style={{
                    padding: "var(--space-4)",
                    border: "1px solid var(--color-border-divider)",
                    borderRadius: "var(--radius-lg)",
                    background: "var(--color-bg-surface)",
                    display: "grid",
                    gap: "var(--space-3)",
                  }}
                >
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                    <strong>Role Showcase (Light)</strong>
                    <Badge tone="neutral">Light Theme</Badge>
                  </div>
                  <div
                    style={{
                      height: "140px",
                      borderRadius: "var(--radius-md)",
                      background: "var(--color-bg-subtle)",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      overflow: "hidden",
                      border: "1px dashed var(--color-border-divider)",
                    }}
                  >
                    {showcaseLightMediaId ? (
                      <img
                        src={publicMediaUrl(showcaseLightMediaId) ?? undefined}
                        alt="Role Showcase Light Preview"
                        style={{ width: "100%", height: "100%", objectFit: "cover" }}
                      />
                    ) : (
                      <span className="app-muted" style={{ fontSize: "var(--fs-caption)" }}>Default platform image active</span>
                    )}
                  </div>
                  <FileUploadField
                    id="upload-showcase-light"
                    label="Choose replacement screenshot"
                    accept="image/png,image/jpeg,image/webp"
                    disabled={uploadingSlot === "showcaseLight"}
                    fileName={showcaseLightMediaId ? "Custom image published" : null}
                    onChange={(file) => void handleSlotImageUpload("showcaseLight", file)}
                    onClear={showcaseLightMediaId ? () => setShowcaseLightMediaId(null) : undefined}
                  />
                </div>

                {/* Showcase Dark */}
                <div
                  style={{
                    padding: "var(--space-4)",
                    border: "1px solid var(--color-border-divider)",
                    borderRadius: "var(--radius-lg)",
                    background: "var(--color-bg-surface)",
                    display: "grid",
                    gap: "var(--space-3)",
                  }}
                >
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                    <strong>Role Showcase (Dark)</strong>
                    <Badge tone="neutral">Dark Theme</Badge>
                  </div>
                  <div
                    style={{
                      height: "140px",
                      borderRadius: "var(--radius-md)",
                      background: "var(--color-bg-subtle)",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      overflow: "hidden",
                      border: "1px dashed var(--color-border-divider)",
                    }}
                  >
                    {showcaseDarkMediaId ? (
                      <img
                        src={publicMediaUrl(showcaseDarkMediaId) ?? undefined}
                        alt="Role Showcase Dark Preview"
                        style={{ width: "100%", height: "100%", objectFit: "cover" }}
                      />
                    ) : (
                      <span className="app-muted" style={{ fontSize: "var(--fs-caption)" }}>Default platform image active</span>
                    )}
                  </div>
                  <FileUploadField
                    id="upload-showcase-dark"
                    label="Choose replacement screenshot"
                    accept="image/png,image/jpeg,image/webp"
                    disabled={uploadingSlot === "showcaseDark"}
                    fileName={showcaseDarkMediaId ? "Custom image published" : null}
                    onChange={(file) => void handleSlotImageUpload("showcaseDark", file)}
                    onClear={showcaseDarkMediaId ? () => setShowcaseDarkMediaId(null) : undefined}
                  />
                </div>
              </div>
            </div>

            <div style={{ display: "flex", justifyContent: "flex-end", borderTop: "1px solid var(--color-border-divider)", paddingTop: "var(--space-4)" }}>
              <Button type="submit" busy={savingConfig}>
                {savingConfig ? "Saving…" : "Save page content"}
              </Button>
            </div>
          </form>
        </Panel>
      )}

      {/* ────────────────────────────────────────────────────────────────────── */}
      {/* TAB 2: TESTIMONIALS & VIDEOS                                           */}
      {/* ────────────────────────────────────────────────────────────────────── */}
      {tab === "testimonials" && cms && (
        <>
          <div className="kpi-grid">
            <MetricCard
              label="All Testimonials"
              value={cms.testimonials.length}
              note="Leadership & user feedback"
              mark="book"
            />
            <MetricCard
              label="Published Stories"
              value={cms.testimonials.filter((t) => t.published).length}
              note="Active in public marquee"
              mark="check-circle"
            />
            <MetricCard
              label="Video Testimonials"
              value={cms.testimonials.filter((t) => t.kind === "VIDEO").length}
              note="Rich media showcase"
              mark="layers"
            />
            <MetricCard
              label="Draft Reviews"
              value={cms.testimonials.filter((t) => !t.published).length}
              note="Unpublished feedback"
              mark="clock"
            />
          </div>

          <Panel
            title="Reviews & Video Testimonials"
            description="Manage customer feedback, testimonials, and video spotlight cards displayed on the public landing page."
            action={
              <Button size="sm" onClick={openNewTestimonial}>
                + Add testimonial
              </Button>
            }
          >
            {/* Filter Tabs */}
            <div style={{ display: "flex", gap: "var(--space-3)", flexWrap: "wrap", alignItems: "center", marginBottom: "var(--space-4)" }}>
              <div className="app-filter-tabs" role="tablist" aria-label="Filter testimonials">
                {(["ALL", "PUBLISHED", "DRAFT", "VIDEO"] as const).map((f) => {
                  const count =
                    f === "ALL"
                      ? cms.testimonials.length
                      : f === "PUBLISHED"
                        ? cms.testimonials.filter((t) => t.published).length
                        : f === "DRAFT"
                          ? cms.testimonials.filter((t) => !t.published).length
                          : cms.testimonials.filter((t) => t.kind === "VIDEO").length;
                  const label = f === "ALL" ? "All" : f === "PUBLISHED" ? "Published" : f === "DRAFT" ? "Drafts" : "Videos";
                  return (
                    <button
                      key={f}
                      type="button"
                      role="tab"
                      aria-selected={testimonialFilter === f}
                      className={`app-filter-tab ${testimonialFilter === f ? "app-filter-tab--active" : ""}`}
                      onClick={() => setTestimonialFilter(f)}
                    >
                      {label} ({count})
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Testimonials Table */}
            <DataTable
              headers={["Order", "Type", "Author & Institution", "Quote / Feedback", "Status", "Actions"]}
              empty={filteredTestimonials.length === 0}
              emptyMessage="No testimonials match the current filter."
            >
              {filteredTestimonials.map((item, idx) => (
                <tr key={item.testimonialId}>
                  <td style={{ width: "90px" }}>
                    <div style={{ display: "flex", gap: "4px" }}>
                      <Button
                        size="sm"
                        variant="secondary"
                        disabled={idx === 0}
                        onClick={() => void handleMoveTestimonial(idx, -1)}
                        aria-label="Move up"
                      >
                        ↑
                      </Button>
                      <Button
                        size="sm"
                        variant="secondary"
                        disabled={idx === cms.testimonials.length - 1}
                        onClick={() => void handleMoveTestimonial(idx, 1)}
                        aria-label="Move down"
                      >
                        ↓
                      </Button>
                    </div>
                  </td>
                  <td>
                    <Badge tone={item.kind === "VIDEO" ? "info" : "neutral"}>
                      {item.kind}
                    </Badge>
                  </td>
                  <td>
                    <strong>{item.authorName}</strong>
                    <div className="app-muted" style={{ fontSize: "var(--fs-caption)" }}>
                      {item.authorRole} · {item.institutionName}
                    </div>
                  </td>
                  <td style={{ maxWidth: "340px", whiteSpace: "normal" }}>
                    {item.quoteText}
                  </td>
                  <td>
                    <Badge tone={item.published ? "success" : "warning"}>
                      {item.published ? "Published" : "Draft"}
                    </Badge>
                  </td>
                  <td>
                    <div style={{ display: "flex", gap: "var(--space-2)" }}>
                      <Button size="sm" variant="secondary" onClick={() => openEditTestimonial(item)}>
                        Edit
                      </Button>
                      <Button size="sm" variant="danger" onClick={() => void handleDeleteTestimonial(item.testimonialId)}>
                        Delete
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </DataTable>
          </Panel>
        </>
      )}

      {/* ────────────────────────────────────────────────────────────────────── */}
      {/* TAB 3: SIGN-IN DIRECTORY                                               */}
      {/* ────────────────────────────────────────────────────────────────────── */}
      {tab === "directory" && cms && (
        <>
          <div className="kpi-grid">
            <MetricCard
              label="Registered Tenants"
              value={cms.directoryTenants.length}
              note="All platform institutions"
              mark="building"
            />
            <MetricCard
              label="Public Directory"
              value={cms.directoryTenants.filter((t) => t.listedInDirectory).length}
              note="Opted into landing search"
              mark="check-circle"
            />
            <MetricCard
              label="Hidden Institutions"
              value={cms.directoryTenants.filter((t) => !t.listedInDirectory).length}
              note="Direct subdomain sign-in only"
              mark="shield"
            />
          </div>

          <Panel
            title="Public Sign-In Institution Directory"
            description="Control which institutions are discoverable on the public landing page institution picker."
          >
            {/* Filter tabs */}
            <div style={{ display: "flex", gap: "var(--space-3)", flexWrap: "wrap", alignItems: "center", marginBottom: "var(--space-4)" }}>
              <div className="app-filter-tabs" role="tablist" aria-label="Filter directory tenants">
                {(["ALL", "LISTED", "HIDDEN"] as const).map((f) => {
                  const count =
                    f === "ALL"
                      ? cms.directoryTenants.length
                      : f === "LISTED"
                        ? cms.directoryTenants.filter((t) => t.listedInDirectory).length
                        : cms.directoryTenants.filter((t) => !t.listedInDirectory).length;
                  const label = f === "ALL" ? "All" : f === "LISTED" ? "Listed in directory" : "Hidden";
                  return (
                    <button
                      key={f}
                      type="button"
                      role="tab"
                      aria-selected={directoryFilter === f}
                      className={`app-filter-tab ${directoryFilter === f ? "app-filter-tab--active" : ""}`}
                      onClick={() => setDirectoryFilter(f)}
                    >
                      {label} ({count})
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Toolbar */}
            <div className="app-toolbar" style={{ marginBottom: "var(--space-4)" }}>
              <SearchBox
                value={directorySearch}
                onChange={setDirectorySearch}
                placeholder="Search institutions by name, subdomain prefix, or tenant type…"
              />
            </div>

            {/* Directory Table */}
            <DataTable
              headers={["Institution Name", "Subdomain Slug", "Institution Type", "Account State", "Directory Visibility", "Action"]}
              empty={filteredDirectoryTenants.length === 0}
              emptyMessage="No institutions match the current directory filter."
            >
              {filteredDirectoryTenants.map((tenant) => (
                <tr key={tenant.tenantId}>
                  <td>
                    <strong>{tenant.tenantName}</strong>
                  </td>
                  <td>
                    <code style={{ fontSize: "12px", background: "var(--color-bg-subtle)", padding: "2px 6px", borderRadius: "4px" }}>
                      {tenant.subdomain}
                    </code>
                  </td>
                  <td>{tenant.tenantType.replaceAll("_", " ")}</td>
                  <td>
                    <StatusBadge value={tenant.state} />
                  </td>
                  <td>
                    <Badge tone={tenant.listedInDirectory ? "success" : "neutral"}>
                      {tenant.listedInDirectory ? "Listed in Directory" : "Hidden"}
                    </Badge>
                  </td>
                  <td>
                    <Button
                      size="sm"
                      variant={tenant.listedInDirectory ? "secondary" : "primary"}
                      onClick={() => void handleDirectoryToggle(tenant)}
                    >
                      {tenant.listedInDirectory ? "Hide from dropdown" : "Show in dropdown"}
                    </Button>
                  </td>
                </tr>
              ))}
            </DataTable>
          </Panel>
        </>
      )}

      {/* ────────────────────────────────────────────────────────────────────── */}
      {/* TAB 4: ENQUIRIES & QUOTES                                              */}
      {/* ────────────────────────────────────────────────────────────────────── */}
      {tab === "enquiries" && (
        <>
          {/* Executive KPI Grid */}
          <div className="kpi-grid">
            <MetricCard
              label="Total Enquiries"
              value={enquiries.length}
              note="All inbound submissions"
              mark="book"
            />
            <MetricCard
              label="Action Required"
              value={enquiries.filter((e) => e.status === "NEW").length}
              note="New leads pending contact"
              mark="clock"
            />
            <MetricCard
              label="Commercial Quotes"
              value={enquiries.filter((e) => e.enquiryType === "QUOTATION").length}
              note="Full catalog scoped leads"
              mark="layers"
            />
            <MetricCard
              label="Demo Requests"
              value={enquiries.filter((e) => e.enquiryType === "DEMO").length}
              note="Tailored walkthrough requests"
              mark="users"
            />
          </div>

          <Panel
            title="Inbound Lead Register"
            description="Manage prospect requirements, commercial quote estimates, and internal sales progress."
          >
            {/* Status Filter Tabs */}
            <div style={{ display: "flex", gap: "var(--space-3)", flexWrap: "wrap", alignItems: "center", marginBottom: "var(--space-4)" }}>
              <div className="app-filter-tabs" role="tablist" aria-label="Filter enquiries by status">
                {(["ALL", "NEW", "CONTACTED", "QUALIFIED", "CLOSED"] as const).map((st) => {
                  const count = st === "ALL" ? enquiries.length : enquiries.filter((e) => e.status === st).length;
                  const label = st === "ALL" ? "All" : st.charAt(0) + st.slice(1).toLowerCase();
                  return (
                    <button
                      key={st}
                      type="button"
                      role="tab"
                      aria-selected={enquiryStatusFilter === st}
                      className={`app-filter-tab ${enquiryStatusFilter === st ? "app-filter-tab--active" : ""}`}
                      onClick={() => setEnquiryStatusFilter(st)}
                    >
                      {label} ({count})
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Toolbar: Search + Type Filter */}
            <div className="app-toolbar" style={{ display: "flex", gap: "var(--space-3)", flexWrap: "wrap", alignItems: "center", marginBottom: "var(--space-4)" }}>
              <div style={{ flex: 1, minWidth: "260px" }}>
                <SearchBox
                  value={enquirySearch}
                  onChange={setEnquirySearch}
                  placeholder="Search by institution, contact person, or email…"
                />
              </div>
              <div style={{ display: "flex", gap: "var(--space-2)", alignItems: "center" }}>
                <label htmlFor="filter-lead-type" className="app-muted" style={{ fontSize: "var(--fs-caption)", whiteSpace: "nowrap" }}>
                  Lead type:
                </label>
                <select
                  id="filter-lead-type"
                  value={enquiryTypeFilter}
                  onChange={(e) => setEnquiryTypeFilter(e.target.value as "ALL" | "QUOTATION" | "DEMO")}
                  style={{
                    padding: "6px 12px",
                    borderRadius: "var(--radius-md)",
                    border: "1px solid var(--color-border-divider)",
                    background: "var(--color-bg-surface)",
                    color: "var(--color-text)",
                    fontSize: "var(--fs-body-sm)",
                  }}
                >
                  <option value="ALL">All types ({enquiries.length})</option>
                  <option value="QUOTATION">
                    Quotations ({enquiries.filter((e) => e.enquiryType === "QUOTATION").length})
                  </option>
                  <option value="DEMO">
                    Demos ({enquiries.filter((e) => e.enquiryType === "DEMO").length})
                  </option>
                </select>
              </div>
            </div>

            {/* Enquiries: Structured DataTable */}
            <DataTable
              headers={["Date", "Type", "Institution & Scope", "Contact Person", "Proposal Snapshot", "Status", "Action"]}
              empty={filteredEnquiries.length === 0}
              emptyMessage="No enquiries match the current filters or search query."
            >
              {filteredEnquiries.map((item) => {
                let quoteTotal: string | null = null;
                if (item.quoteSnapshotJson) {
                  try {
                    const parsed = JSON.parse(item.quoteSnapshotJson);
                    if (parsed?.plans?.[0]?.total != null) {
                      quoteTotal = `${parsed.currencyCode || "INR"} ${Number(parsed.plans[0].total).toLocaleString(undefined, { minimumFractionDigits: 2 })}`;
                    }
                  } catch {
                    // Ignore
                  }
                }
                return (
                  <tr key={item.enquiryId}>
                    <td style={{ whiteSpace: "nowrap" }}>
                      {new Date(item.createdAt).toLocaleDateString()}
                    </td>
                    <td>
                      <Badge tone={item.enquiryType === "QUOTATION" ? "info" : "neutral"}>
                        {item.enquiryType}
                      </Badge>
                    </td>
                    <td>
                      <strong>{item.institutionName}</strong>
                      <div className="app-muted" style={{ fontSize: "var(--fs-caption)" }}>
                        {[item.institutionType, item.studentRange].filter(Boolean).join(" · ") || "Standard scope"}
                      </div>
                    </td>
                    <td>
                      <div>{item.contactName}</div>
                      <div className="app-muted" style={{ fontSize: "var(--fs-caption)" }}>
                        <a href={`mailto:${item.contactEmail}`} className="app-text-link">
                          {item.contactEmail}
                        </a>
                      </div>
                    </td>
                    <td>
                      {quoteTotal ? (
                        <strong style={{ color: "var(--color-text-brand)" }}>{quoteTotal}</strong>
                      ) : (
                        <span className="app-muted" style={{ fontSize: "var(--fs-caption)" }}>Custom scoping</span>
                      )}
                    </td>
                    <td>
                      <StatusBadge value={item.status} />
                    </td>
                    <td>
                      <Button
                        size="sm"
                        variant="secondary"
                        onClick={() => {
                          setSelectedEnquiry(item);
                          setEnquiryStatusInput(item.status);
                          setEnquiryNotesInput(item.internalNotes ?? "");
                        }}
                      >
                        Review
                      </Button>
                    </td>
                  </tr>
                );
              })}
            </DataTable>
          </Panel>
        </>
      )}

      {/* ────────────────────────────────────────────────────────────────────── */}
      {/* TESTIMONIAL MODAL                                                      */}
      {/* ────────────────────────────────────────────────────────────────────── */}
      <Modal
        isOpen={testimonialModalOpen}
        onClose={() => setTestimonialModalOpen(false)}
        kicker="TESTIMONIAL"
        title={editingTestimonial ? "Edit Testimonial" : "Add Testimonial"}
        description="Showcase genuine leader, faculty, or parent feedback on the public landing page."
        size="md"
        footer={
          <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)", width: "100%" }}>
            <Button
              variant="secondary"
              type="button"
              onClick={() => setTestimonialModalOpen(false)}
              disabled={savingTestimonial}
            >
              Cancel
            </Button>
            <Button form="testimonial-modal-form" type="submit" busy={savingTestimonial}>
              {savingTestimonial ? "Saving…" : "Save testimonial"}
            </Button>
          </div>
        }
      >
        <form id="testimonial-modal-form" onSubmit={handleSaveTestimonial} style={{ display: "grid", gap: "var(--space-3)" }}>
          <SelectField
            id="t-kind"
            label="Testimonial type"
            value={tForm.kind}
            onChange={(e) => setTForm((p) => ({ ...p, kind: e.target.value as "TEXT" | "VIDEO" }))}
            options={[
              { value: "TEXT", label: "Written feedback" },
              { value: "VIDEO", label: "Video testimonial" },
            ]}
          />
          <TextField
            id="t-author"
            label="Author name"
            required
            value={tForm.authorName}
            onChange={(e) => setTForm((p) => ({ ...p, authorName: e.target.value }))}
          />
          <TextField
            id="t-role"
            label="Author role"
            required
            placeholder="e.g. Principal / Registrar / Head of Academics"
            value={tForm.authorRole}
            onChange={(e) => setTForm((p) => ({ ...p, authorRole: e.target.value }))}
          />
          <TextField
            id="t-institution"
            label="Institution name"
            required
            placeholder="e.g. St. Jude International School"
            value={tForm.institutionName}
            onChange={(e) => setTForm((p) => ({ ...p, institutionName: e.target.value }))}
          />
          <TextAreaField
            id="t-quote"
            label="Quote / summary"
            rows={3}
            required
            placeholder="Key operational outcomes or leadership feedback…"
            value={tForm.quoteText}
            onChange={(e) => setTForm((p) => ({ ...p, quoteText: e.target.value }))}
          />

          {tForm.kind === "VIDEO" && (
            <div style={{ display: "grid", gap: "var(--space-3)", padding: "var(--space-3)", background: "var(--color-bg-subtle)", borderRadius: "var(--radius-md)" }}>
              <TextField
                id="t-video-url"
                label="External video embed URL (YouTube, Vimeo, etc.)"
                placeholder="https://www.youtube.com/watch?v=..."
                value={tForm.videoUrl}
                onChange={(e) => setTForm((p) => ({ ...p, videoUrl: e.target.value }))}
              />
              <FileUploadField
                id="t-video-upload"
                label="Or upload MP4/WebM video file (malware-scanned)"
                accept="video/mp4,video/webm"
                disabled={uploadingTestimonialMedia}
                fileName={tForm.videoMediaId ? `Uploaded Media ID: ${tForm.videoMediaId}` : null}
                onChange={(file) => void handleUploadTestimonialAsset("VIDEO", file)}
                onClear={tForm.videoMediaId ? () => setTForm((p) => ({ ...p, videoMediaId: "" })) : undefined}
              />
              <FileUploadField
                id="t-poster-upload"
                label="Upload poster thumbnail (PNG/JPG/WebP, optional)"
                accept="image/png,image/jpeg,image/webp"
                disabled={uploadingTestimonialMedia}
                fileName={tForm.posterMediaId ? `Uploaded Poster ID: ${tForm.posterMediaId}` : null}
                onChange={(file) => void handleUploadTestimonialAsset("IMAGE", file)}
                onClear={tForm.posterMediaId ? () => setTForm((p) => ({ ...p, posterMediaId: "" })) : undefined}
              />
            </div>
          )}

          <label style={{ display: "flex", alignItems: "center", gap: "var(--space-2)", marginTop: "var(--space-1)" }}>
            <input
              type="checkbox"
              checked={tForm.published}
              onChange={(e) => setTForm((p) => ({ ...p, published: e.target.checked }))}
            />
            <span>Publish on public landing page</span>
          </label>
        </form>
      </Modal>

      {/* ────────────────────────────────────────────────────────────────────── */}
      {/* ENQUIRY DETAILS MODAL (RESPONSIVE 2-COLUMN WITH STICKY FOOTER)          */}
      {/* ────────────────────────────────────────────────────────────────────── */}
      <Modal
        isOpen={Boolean(selectedEnquiry)}
        onClose={() => setSelectedEnquiry(null)}
        kicker="ENQUIRY DETAILS"
        title={selectedEnquiry ? selectedEnquiry.institutionName : "Enquiry"}
        size="xl"
        footer={
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", width: "100%", flexWrap: "wrap", gap: "var(--space-2)" }}>
            <div className="app-muted" style={{ fontSize: "var(--fs-caption)" }}>
              {selectedEnquiry && `Lead ID: ${selectedEnquiry.enquiryId.slice(0, 8)} · Received ${new Date(selectedEnquiry.createdAt).toLocaleString()}`}
            </div>
            <div style={{ display: "flex", gap: "var(--space-2)" }}>
              <Button
                variant="secondary"
                type="button"
                onClick={() => setSelectedEnquiry(null)}
                disabled={savingEnquiry}
              >
                Close
              </Button>
              <Button form="enquiry-update-modal-form" type="submit" busy={savingEnquiry}>
                {savingEnquiry ? "Saving…" : "Update enquiry status"}
              </Button>
            </div>
          </div>
        }
      >
        {selectedEnquiry && (
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))",
              gap: "var(--space-4)",
              alignItems: "start",
            }}
          >
            {/* ── Left Column: Prospect Profile, Scope, & Triage Form ── */}
            <div style={{ display: "grid", gap: "var(--space-3)" }}>
              {/* Top Badges */}
              <div style={{ display: "flex", gap: "var(--space-2)", alignItems: "center", flexWrap: "wrap" }}>
                <Badge tone={selectedEnquiry.enquiryType === "QUOTATION" ? "info" : "neutral"}>
                  {selectedEnquiry.enquiryType}
                </Badge>
                <StatusBadge value={selectedEnquiry.status} />
              </div>

              {/* Contact Card */}
              <div
                style={{
                  padding: "var(--space-3)",
                  background: "var(--color-bg-subtle)",
                  borderRadius: "var(--radius-md)",
                  border: "1px solid var(--color-border-divider)",
                  display: "grid",
                  gap: "var(--space-2)",
                }}
              >
                <div>
                  <span className="app-muted" style={{ fontSize: "11px", textTransform: "uppercase", letterSpacing: "0.05em" }}>
                    Contact Person:
                  </span>
                  <div style={{ fontWeight: "var(--fw-semibold)", fontSize: "var(--fs-body)" }}>
                    {selectedEnquiry.contactName}
                  </div>
                </div>
                <div>
                  <span className="app-muted" style={{ fontSize: "11px", textTransform: "uppercase", letterSpacing: "0.05em" }}>
                    Work Email:
                  </span>
                  <div>
                    <a href={`mailto:${selectedEnquiry.contactEmail}`} className="app-text-link">
                      {selectedEnquiry.contactEmail}
                    </a>
                  </div>
                </div>
                <div>
                  <span className="app-muted" style={{ fontSize: "11px", textTransform: "uppercase", letterSpacing: "0.05em" }}>
                    Phone Number:
                  </span>
                  <div>
                    {selectedEnquiry.contactPhone ? (
                      <a href={`tel:${selectedEnquiry.contactPhone}`} className="app-text-link">
                        {selectedEnquiry.contactPhone}
                      </a>
                    ) : (
                      <span className="app-muted">Not provided</span>
                    )}
                  </div>
                </div>
              </div>

              {/* Scope & Modules Card */}
              <div
                style={{
                  padding: "var(--space-3)",
                  background: "var(--color-bg-surface)",
                  borderRadius: "var(--radius-md)",
                  border: "1px solid var(--color-border-divider)",
                  display: "grid",
                  gap: "var(--space-2)",
                }}
              >
                <div>
                  <span className="app-muted" style={{ fontSize: "var(--fs-caption)" }}>Institution Scale: </span>
                  <strong>
                    {[selectedEnquiry.institutionType, selectedEnquiry.studentRange].filter(Boolean).join(" · ") || "Standard"}
                  </strong>
                </div>
                {selectedEnquiry.migrationSupport && (
                  <div>
                    <span className="app-muted" style={{ fontSize: "var(--fs-caption)" }}>Data Migration: </span>
                    <span>{selectedEnquiry.migrationSupport}</span>
                  </div>
                )}
                {selectedEnquiry.trainingMode && (
                  <div>
                    <span className="app-muted" style={{ fontSize: "var(--fs-caption)" }}>Training: </span>
                    <span>{selectedEnquiry.trainingMode}</span>
                  </div>
                )}

                <div style={{ marginTop: "var(--space-1)" }}>
                  <div className="app-muted" style={{ fontSize: "11px", marginBottom: "var(--space-1)" }}>
                    Requested Modules:
                  </div>
                  <div style={{ display: "flex", flexWrap: "wrap", gap: "var(--space-1)" }}>
                    {parsedEnquiryModules.length > 0 ? (
                      parsedEnquiryModules.map((m) => (
                        <Badge key={m} tone="neutral">
                          {formatModuleCode(m)}
                        </Badge>
                      ))
                    ) : (
                      <span className="app-muted" style={{ fontSize: "var(--fs-caption)" }}>All core platform capabilities</span>
                    )}
                  </div>
                </div>

                {selectedEnquiry.notes && (
                  <div style={{ marginTop: "var(--space-1)" }}>
                    <div className="app-muted" style={{ fontSize: "11px", marginBottom: "4px" }}>
                      Prospect Notes:
                    </div>
                    <div
                      style={{
                        padding: "var(--space-2)",
                        background: "var(--color-bg-subtle)",
                        borderRadius: "var(--radius-sm)",
                        fontSize: "var(--fs-caption)",
                        fontStyle: "italic",
                      }}
                    >
                      “{selectedEnquiry.notes}”
                    </div>
                  </div>
                )}
              </div>

              {/* Status Update Form */}
              <form
                id="enquiry-update-modal-form"
                onSubmit={handleSaveEnquiryStatus}
                style={{
                  padding: "var(--space-3)",
                  background: "var(--color-bg-surface)",
                  borderRadius: "var(--radius-md)",
                  border: "1px solid var(--color-border-divider)",
                  display: "grid",
                  gap: "var(--space-3)",
                }}
              >
                <SelectField
                  id="enq-status"
                  label="Update lead status"
                  value={enquiryStatusInput}
                  onChange={(e) =>
                    setEnquiryStatusInput(e.target.value as "NEW" | "CONTACTED" | "QUALIFIED" | "CLOSED")
                  }
                  options={[
                    { value: "NEW", label: "NEW — Pending outreach" },
                    { value: "CONTACTED", label: "CONTACTED — Outreach in progress" },
                    { value: "QUALIFIED", label: "QUALIFIED — Requirement verified" },
                    { value: "CLOSED", label: "CLOSED — Deal closed / archived" },
                  ]}
                />
                <TextAreaField
                  id="enq-notes"
                  label="Internal sales / onboarding notes"
                  rows={3}
                  placeholder="Log notes from follow-up calls, demo dates, or account setup…"
                  value={enquiryNotesInput}
                  onChange={(e) => setEnquiryNotesInput(e.target.value)}
                />
              </form>
            </div>

            {/* ── Right Column: Commercial Quote Snapshot ── */}
            <div
              style={{
                padding: "var(--space-3)",
                background: "var(--color-bg-surface)",
                borderRadius: "var(--radius-md)",
                border: "1px solid var(--color-border-divider)",
                display: "grid",
                gap: "var(--space-3)",
              }}
            >
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", flexWrap: "wrap", gap: "var(--space-1)" }}>
                <strong style={{ fontSize: "var(--fs-body-sm)" }}>
                  Commercial Proposal Snapshot
                </strong>
                {quoteSnapshotData.parsed?.currencyCode && (
                  <span className="app-muted" style={{ fontSize: "var(--fs-caption)" }}>
                    Currency: {quoteSnapshotData.parsed.currencyCode} · Cycle: {quoteSnapshotData.parsed.billingCycleMonths ?? 12}M
                  </span>
                )}
              </div>

              {quoteSnapshotData.isStructured && quoteSnapshotData.parsed?.plans ? (
                <div style={{ display: "grid", gap: "var(--space-3)" }}>
                  {quoteSnapshotData.parsed.plans.map((p, idx) => {
                    const isBest =
                      p.planId === quoteSnapshotData.parsed?.bestFitPlanId ||
                      p.recommendation === "BEST_FIT";
                    const curr = quoteSnapshotData.parsed?.currencyCode || "INR";
                    return (
                      <div
                        key={p.planId || idx}
                        style={{
                          border: `1px solid ${isBest ? "var(--color-primary-600, #10b981)" : "var(--color-border-divider)"}`,
                          borderRadius: "var(--radius-md)",
                          padding: "var(--space-3)",
                          background: isBest ? "var(--color-bg-subtle)" : "var(--color-bg-surface)",
                        }}
                      >
                        <div
                          style={{
                            display: "flex",
                            justifyContent: "space-between",
                            alignItems: "center",
                            marginBottom: "var(--space-2)",
                            flexWrap: "wrap",
                            gap: "var(--space-1)",
                          }}
                        >
                          <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)" }}>
                            <strong>{p.planName || "Standard Plan"}</strong>
                            {isBest && <Badge tone="success">Recommended Plan</Badge>}
                          </div>
                          <div style={{ fontSize: "var(--fs-h3)", fontWeight: "var(--fw-bold)", color: "var(--color-text-brand)" }}>
                            {curr} {Number(p.total ?? 0).toLocaleString(undefined, { minimumFractionDigits: 2 })}
                          </div>
                        </div>

                        {p.lines && p.lines.length > 0 && (
                          <div style={{ marginTop: "var(--space-2)", display: "grid", gap: "var(--space-2)" }}>
                            <div style={{ display: "grid", gap: "6px" }}>
                              {p.lines.map((line, lIdx) => (
                                <div
                                  key={lIdx}
                                  style={{
                                    display: "flex",
                                    justifyContent: "space-between",
                                    alignItems: "flex-start",
                                    gap: "var(--space-2)",
                                    fontSize: "var(--fs-caption)",
                                    paddingBottom: "4px",
                                    borderBottom: "1px dashed var(--color-border-divider)",
                                  }}
                                >
                                  <div>
                                    <div style={{ fontWeight: "var(--fw-medium)" }}>
                                      {line.description || line.code || "Plan fee"}
                                    </div>
                                    <div className="app-muted" style={{ fontSize: "11px" }}>
                                      Qty: {Number(line.quantity ?? 1).toLocaleString()} × {curr} {Number(line.unitAmount ?? 0).toFixed(2)}
                                    </div>
                                  </div>
                                  <div style={{ fontWeight: "var(--fw-semibold)", whiteSpace: "nowrap" }}>
                                    {curr} {Number(line.amount ?? 0).toFixed(2)}
                                  </div>
                                </div>
                              ))}
                            </div>

                            <div
                              style={{
                                display: "flex",
                                justifyContent: "space-between",
                                fontSize: "var(--fs-caption)",
                                color: "var(--color-text-muted)",
                                paddingTop: "2px",
                              }}
                            >
                              <span>Applicable Tax</span>
                              <span style={{ whiteSpace: "nowrap" }}>
                                {curr} {Number(p.tax ?? 0).toFixed(2)}
                              </span>
                            </div>

                            <div
                              style={{
                                display: "flex",
                                justifyContent: "space-between",
                                alignItems: "center",
                                paddingTop: "var(--space-2)",
                                borderTop: "1px solid var(--color-border-divider)",
                                fontWeight: "var(--fw-bold)",
                                fontSize: "var(--fs-body)",
                              }}
                            >
                              <span>Net Total Proposal</span>
                              <span style={{ color: "var(--color-text-brand)", whiteSpace: "nowrap" }}>
                                {curr} {Number(p.total ?? 0).toFixed(2)}
                              </span>
                            </div>
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              ) : (
                <Notice tone="info">
                  {quoteSnapshotData.parsed?.note || "Quotation requirements logged for sales engineering review."}
                </Notice>
              )}

              <details style={{ marginTop: "var(--space-2)", fontSize: "11px", color: "var(--color-text-muted)" }}>
                <summary style={{ cursor: "pointer", userSelect: "none" }}>Technical Snapshot JSON</summary>
                <pre
                  style={{
                    marginTop: "var(--space-1)",
                    maxHeight: "130px",
                    overflow: "auto",
                    padding: "var(--space-2)",
                    background: "var(--color-bg-subtle)",
                    borderRadius: "var(--radius-sm)",
                    whiteSpace: "pre-wrap",
                    wordBreak: "break-all",
                    fontSize: "11px",
                  }}
                >
                  {quoteSnapshotData.rawFormatted}
                </pre>
              </details>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}

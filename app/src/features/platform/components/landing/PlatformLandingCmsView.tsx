import { type ChangeEvent, type FormEvent, useCallback, useEffect, useState } from "react";
import {
  Badge,
  Button,
  Modal,
  Notice,
  PageHeader,
  Panel,
  SelectField,
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

  // Enquiry filter & detail modal
  const [enquiryTypeFilter, setEnquiryTypeFilter] = useState("");
  const [enquiryStatusFilter, setEnquiryStatusFilter] = useState("");
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
        landingApi.listEnquiries(enquiryTypeFilter || undefined, enquiryStatusFilter || undefined),
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
  }, [enquiryStatusFilter, enquiryTypeFilter]);

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
    e: ChangeEvent<HTMLInputElement>,
  ) => {
    const file = e.target.files?.[0];
    if (!file) return;
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
      e.target.value = "";
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

  const handleUploadTestimonialAsset = async (
    kind: "VIDEO" | "IMAGE",
    e: ChangeEvent<HTMLInputElement>,
  ) => {
    const file = e.target.files?.[0];
    if (!file) return;
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
      e.target.value = "";
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

  return (
    <div className="app-page">
      <PageHeader
        title="Landing Page & Enquiries"
        subtitle="Customize public marketing copy, manage text & video testimonials, opt institutions into the sign-in dropdown, and review quotation & demo leads."
        actions={
          <Button variant="secondary" onClick={() => void loadAll()} disabled={loading}>
            Refresh
          </Button>
        }
      />

      {error && <Notice tone="danger">{error}</Notice>}

      <Tabs
        activeId={tab}
        onChange={(id) => setTab(id as CmsTab)}
        items={[
          { id: "content", label: "Page Content & Photos" },
          { id: "testimonials", label: `Testimonials & Videos (${cms?.testimonials.length ?? 0})` },
          { id: "directory", label: "Sign-in Directory" },
          { id: "enquiries", label: `Enquiries & Quotes (${enquiries.length})` },
        ]}
      />

      {tab === "content" && cms && (
        <Panel title="Marketing Page Content & Platform Screenshots">
          <form onSubmit={handleSaveConfig} style={{ display: "grid", gap: "var(--space-4)" }}>
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

            <div className="app-form-grid">
              <div className="eds-field">
                <label htmlFor="upload-hero-light">Hero platform screenshot (Light theme)</label>
                <input
                  id="upload-hero-light"
                  type="file"
                  accept="image/png,image/jpeg,image/webp"
                  disabled={uploadingSlot === "heroLight"}
                  onChange={(e) => void handleSlotImageUpload("heroLight", e)}
                />
                {heroLightMediaId && (
                  <small className="eds-field__hint">
                    Uploaded: {publicMediaUrl(heroLightMediaId)}
                  </small>
                )}
              </div>
              <div className="eds-field">
                <label htmlFor="upload-hero-dark">Hero platform screenshot (Dark theme)</label>
                <input
                  id="upload-hero-dark"
                  type="file"
                  accept="image/png,image/jpeg,image/webp"
                  disabled={uploadingSlot === "heroDark"}
                  onChange={(e) => void handleSlotImageUpload("heroDark", e)}
                />
                {heroDarkMediaId && (
                  <small className="eds-field__hint">
                    Uploaded: {publicMediaUrl(heroDarkMediaId)}
                  </small>
                )}
              </div>
              <div className="eds-field">
                <label htmlFor="upload-showcase-light">Role showcase screenshot (Light theme)</label>
                <input
                  id="upload-showcase-light"
                  type="file"
                  accept="image/png,image/jpeg,image/webp"
                  disabled={uploadingSlot === "showcaseLight"}
                  onChange={(e) => void handleSlotImageUpload("showcaseLight", e)}
                />
              </div>
              <div className="eds-field">
                <label htmlFor="upload-showcase-dark">Role showcase screenshot (Dark theme)</label>
                <input
                  id="upload-showcase-dark"
                  type="file"
                  accept="image/png,image/jpeg,image/webp"
                  disabled={uploadingSlot === "showcaseDark"}
                  onChange={(e) => void handleSlotImageUpload("showcaseDark", e)}
                />
              </div>
            </div>

            <div>
              <Button type="submit" busy={savingConfig}>
                {savingConfig ? "Saving…" : "Save page content"}
              </Button>
            </div>
          </form>
        </Panel>
      )}

      {tab === "testimonials" && cms && (
        <Panel
          title="Reviews & Video Testimonials"
          action={
            <Button size="sm" onClick={openNewTestimonial}>
              + Add testimonial
            </Button>
          }
        >
          {cms.testimonials.length === 0 ? (
            <Notice tone="info">
              No custom testimonials published yet. Add a text review or video testimonial to replace
              the default showcase cards.
            </Notice>
          ) : (
            <div className="app-table-wrap">
              <table className="app-table">
                <thead>
                  <tr>
                    <th>Order</th>
                    <th>Type</th>
                    <th>Author &amp; Institution</th>
                    <th>Quote</th>
                    <th>Status</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {cms.testimonials.map((item, idx) => (
                    <tr key={item.testimonialId}>
                      <td>
                        <div style={{ display: "flex", gap: "4px" }}>
                          <Button
                            size="sm"
                            variant="secondary"
                            disabled={idx === 0}
                            onClick={() => void handleMoveTestimonial(idx, -1)}
                          >
                            ↑
                          </Button>
                          <Button
                            size="sm"
                            variant="secondary"
                            disabled={idx === cms.testimonials.length - 1}
                            onClick={() => void handleMoveTestimonial(idx, 1)}
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
                        <div className="app-muted">
                          {item.authorRole} · {item.institutionName}
                        </div>
                      </td>
                      <td style={{ maxWidth: "360px" }}>{item.quoteText}</td>
                      <td>
                        <Badge tone={item.published ? "success" : "warning"}>
                          {item.published ? "Published" : "Draft"}
                        </Badge>
                      </td>
                      <td>
                        <div style={{ display: "flex", gap: "var(--space-2)" }}>
                          <Button
                            size="sm"
                            variant="secondary"
                            onClick={() => openEditTestimonial(item)}
                          >
                            Edit
                          </Button>
                          <Button
                            size="sm"
                            variant="danger"
                            onClick={() => void handleDeleteTestimonial(item.testimonialId)}
                          >
                            Delete
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Panel>
      )}

      {tab === "directory" && cms && (
        <Panel title="Public Sign-In Institution Directory Opt-In">
          <Notice tone="info">
            Only Active or Trial tenants explicitly opted in here appear in the public sign-in
            dropdown on the landing page. Tenants default to hidden.
          </Notice>
          <div className="app-table-wrap" style={{ marginTop: "var(--space-4)" }}>
            <table className="app-table">
              <thead>
                <tr>
                  <th>Institution</th>
                  <th>Subdomain</th>
                  <th>Type</th>
                  <th>State</th>
                  <th>Public Directory</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {cms.directoryTenants.map((tenant) => (
                  <tr key={tenant.tenantId}>
                    <td>
                      <strong>{tenant.tenantName}</strong>
                    </td>
                    <td>{tenant.subdomain}</td>
                    <td>{tenant.tenantType}</td>
                    <td>
                      <Badge tone={tenant.state === "ACTIVE" ? "success" : "neutral"}>
                        {tenant.state}
                      </Badge>
                    </td>
                    <td>
                      <Badge tone={tenant.listedInDirectory ? "success" : "neutral"}>
                        {tenant.listedInDirectory ? "Listed" : "Hidden"}
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
              </tbody>
            </table>
          </div>
        </Panel>
      )}

      {tab === "enquiries" && (
        <Panel
          title="Demo & Quotation Enquiries"
          action={
            <div style={{ display: "flex", gap: "var(--space-2)" }}>
              <SelectField
                id="filter-enquiry-type"
                label=""
                value={enquiryTypeFilter}
                onChange={(e) => setEnquiryTypeFilter(e.target.value)}
                options={[
                  { value: "", label: "All types" },
                  { value: "QUOTATION", label: "Quotations" },
                  { value: "DEMO", label: "Demo requests" },
                ]}
              />
              <SelectField
                id="filter-enquiry-status"
                label=""
                value={enquiryStatusFilter}
                onChange={(e) => setEnquiryStatusFilter(e.target.value)}
                options={[
                  { value: "", label: "All statuses" },
                  { value: "NEW", label: "New" },
                  { value: "CONTACTED", label: "Contacted" },
                  { value: "QUALIFIED", label: "Qualified" },
                  { value: "CLOSED", label: "Closed" },
                ]}
              />
            </div>
          }
        >
          <div className="app-table-wrap">
            <table className="app-table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Type</th>
                  <th>Institution</th>
                  <th>Contact</th>
                  <th>Status</th>
                  <th>Inspect</th>
                </tr>
              </thead>
              <tbody>
                {enquiries.map((item) => (
                  <tr key={item.enquiryId}>
                    <td>{new Date(item.createdAt).toLocaleDateString()}</td>
                    <td>
                      <Badge tone={item.enquiryType === "QUOTATION" ? "info" : "neutral"}>
                        {item.enquiryType}
                      </Badge>
                    </td>
                    <td>
                      <strong>{item.institutionName}</strong>
                      <div className="app-muted">
                        {[item.institutionType, item.studentRange].filter(Boolean).join(" · ")}
                      </div>
                    </td>
                    <td>
                      <div>{item.contactName}</div>
                      <div className="app-muted">{item.contactEmail}</div>
                    </td>
                    <td>
                      <Badge
                        tone={
                          item.status === "QUALIFIED"
                            ? "success"
                            : item.status === "NEW"
                              ? "warning"
                              : "neutral"
                        }
                      >
                        {item.status}
                      </Badge>
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
                ))}
              </tbody>
            </table>
          </div>
        </Panel>
      )}

      {/* Testimonial Add/Edit Modal */}
      <Modal
        isOpen={testimonialModalOpen}
        onClose={() => setTestimonialModalOpen(false)}
        kicker="TESTIMONIAL"
        title={editingTestimonial ? "Edit Testimonial" : "Add Testimonial"}
        size="md"
      >
        <form onSubmit={handleSaveTestimonial} style={{ display: "grid", gap: "var(--space-3)" }}>
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
            placeholder="e.g. Principal / Registrar / Parent"
            value={tForm.authorRole}
            onChange={(e) => setTForm((p) => ({ ...p, authorRole: e.target.value }))}
          />
          <TextField
            id="t-institution"
            label="Institution name"
            required
            value={tForm.institutionName}
            onChange={(e) => setTForm((p) => ({ ...p, institutionName: e.target.value }))}
          />
          <TextAreaField
            id="t-quote"
            label="Quote / summary"
            rows={3}
            required
            value={tForm.quoteText}
            onChange={(e) => setTForm((p) => ({ ...p, quoteText: e.target.value }))}
          />
          {tForm.kind === "VIDEO" && (
            <>
              <TextField
                id="t-video-url"
                label="External video / embed URL (optional if uploading MP4/WebM)"
                value={tForm.videoUrl}
                onChange={(e) => setTForm((p) => ({ ...p, videoUrl: e.target.value }))}
              />
              <div className="eds-field">
                <label htmlFor="t-video-upload">Upload video file (MP4/WebM, malware-scanned)</label>
                <input
                  id="t-video-upload"
                  type="file"
                  accept="video/mp4,video/webm"
                  disabled={uploadingTestimonialMedia}
                  onChange={(e) => void handleUploadTestimonialAsset("VIDEO", e)}
                />
                {tForm.videoMediaId && (
                  <small className="eds-field__hint">Video ID: {tForm.videoMediaId}</small>
                )}
              </div>
              <div className="eds-field">
                <label htmlFor="t-poster-upload">Upload poster thumbnail (PNG/JPG/WebP, optional)</label>
                <input
                  id="t-poster-upload"
                  type="file"
                  accept="image/png,image/jpeg,image/webp"
                  disabled={uploadingTestimonialMedia}
                  onChange={(e) => void handleUploadTestimonialAsset("IMAGE", e)}
                />
              </div>
            </>
          )}
          <label style={{ display: "flex", alignItems: "center", gap: "var(--space-2)" }}>
            <input
              type="checkbox"
              checked={tForm.published}
              onChange={(e) => setTForm((p) => ({ ...p, published: e.target.checked }))}
            />
            <span>Published on public landing page</span>
          </label>
          <Button type="submit" busy={savingTestimonial}>
            {savingTestimonial ? "Saving…" : "Save testimonial"}
          </Button>
        </form>
      </Modal>

      {/* Enquiry Review Modal */}
      <Modal
        isOpen={Boolean(selectedEnquiry)}
        onClose={() => setSelectedEnquiry(null)}
        kicker="ENQUIRY DETAILS"
        title={selectedEnquiry ? selectedEnquiry.institutionName : "Enquiry"}
        size="lg"
      >
        {selectedEnquiry && (
          <form onSubmit={handleSaveEnquiryStatus} style={{ display: "grid", gap: "var(--space-4)" }}>
            <div>
              <strong>Contact:</strong> {selectedEnquiry.contactName} ({selectedEnquiry.contactEmail}
              {selectedEnquiry.contactPhone ? ` · ${selectedEnquiry.contactPhone}` : ""})
            </div>
            <div>
              <strong>Scope:</strong> {selectedEnquiry.institutionType || "—"} ·{" "}
              {selectedEnquiry.studentRange || "—"} · Modules: {selectedEnquiry.selectedModules}
            </div>
            {selectedEnquiry.notes && (
              <div>
                <strong>Notes:</strong> {selectedEnquiry.notes}
              </div>
            )}
            {selectedEnquiry.quoteSnapshotJson && (
              <div>
                <strong>Internal Server-Computed Commercial Quote Snapshot:</strong>
                <pre
                  style={{
                    maxHeight: "200px",
                    overflow: "auto",
                    padding: "var(--space-3)",
                    background: "var(--color-bg-subtle)",
                    borderRadius: "var(--radius-md)",
                    fontSize: "12px",
                  }}
                >
                  {selectedEnquiry.quoteSnapshotJson}
                </pre>
              </div>
            )}
            <SelectField
              id="enq-status"
              label="Status"
              value={enquiryStatusInput}
              onChange={(e) =>
                setEnquiryStatusInput(e.target.value as "NEW" | "CONTACTED" | "QUALIFIED" | "CLOSED")
              }
              options={[
                { value: "NEW", label: "NEW" },
                { value: "CONTACTED", label: "CONTACTED" },
                { value: "QUALIFIED", label: "QUALIFIED" },
                { value: "CLOSED", label: "CLOSED" },
              ]}
            />
            <TextAreaField
              id="enq-notes"
              label="Internal sales / onboarding notes"
              rows={3}
              value={enquiryNotesInput}
              onChange={(e) => setEnquiryNotesInput(e.target.value)}
            />
            <Button type="submit" busy={savingEnquiry}>
              {savingEnquiry ? "Updating…" : "Update enquiry"}
            </Button>
          </form>
        )}
      </Modal>
    </div>
  );
}

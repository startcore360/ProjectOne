import { useCallback, useEffect, useState } from "react";
import { Button, Modal, Notice, SkeletonCard, SkeletonText, TextField } from "../../../../design-system/components";
import api from "../../../../services/axios";
import { readErrorMessage } from "../common/types";

type Branding = { hasLogo: boolean; logoVersion: string | null; primary: string | null; accent: string | null; colorsAllowed: boolean };

export interface TenantBrandingModalProps {
  isOpen: boolean;
  tenantId: string;
  tenantName: string;
  onClose: () => void;
  onSaved: (message: string) => void;
}

const MAX_BYTES = 512 * 1024;
const HEX = /^#[0-9A-Fa-f]{6}$/;

// E1-S4: logo on every plan (PNG/SVG up to 512 KB); colours only when the tenant's plan allows them.
// The server checks the file's real content again and refuses unsafe SVGs.
export function TenantBrandingModal({ isOpen, tenantId, tenantName, onClose, onSaved }: TenantBrandingModalProps) {
  const [branding, setBranding] = useState<Branding | null>(null);
  const [logoUrl, setLogoUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState("");
  const [primary, setPrimary] = useState("");
  const [accent, setAccent] = useState("");
  const [removeLogo, setRemoveLogo] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState("");

  const load = useCallback(async () => {
    try {
      const { data } = await api.get<Branding>(`/platform/tenants/${tenantId}/branding`);
      setBranding(data);
      setPrimary(data.primary ?? "");
      setAccent(data.accent ?? "");
      if (data.hasLogo) {
        const res = await api.get<Blob>(`/platform/tenants/${tenantId}/branding/logo`, { responseType: "blob" });
        setLogoUrl(URL.createObjectURL(res.data));
      }
    } catch (err) {
      setLoadError(readErrorMessage(err, "Could not load this tenant's branding."));
    } finally {
      setLoading(false);
    }
  }, [tenantId]);

  useEffect(() => {
    if (isOpen) void load();
  }, [isOpen, load]);

  useEffect(() => () => { if (logoUrl) URL.revokeObjectURL(logoUrl); }, [logoUrl]);

  const pickFile = (picked: File | null) => {
    setFileError("");
    setFile(null);
    if (!picked) return;
    if (!["image/png", "image/svg+xml"].includes(picked.type)) {
      setFileError("Choose a PNG or SVG file.");
      return;
    }
    if (picked.size > MAX_BYTES) {
      setFileError("The logo must be 512 KB or smaller.");
      return;
    }
    setFile(picked);
    setRemoveLogo(false);
  };

  const colorError = (value: string) => (value && !HEX.test(value) ? "Use a colour like #1A2B3C." : undefined);
  const invalid = !!fileError || !!colorError(primary) || !!colorError(accent);

  const save = async () => {
    if (!branding || invalid) return;
    setSaving(true);
    setSaveError("");
    const form = new FormData();
    if (file) form.append("logo", file);
    if (removeLogo) form.append("removeLogo", "true");
    if (branding.colorsAllowed || primary === "") form.append("primary", primary);
    if (branding.colorsAllowed || accent === "") form.append("accent", accent);
    try {
      await api.put(`/platform/tenants/${tenantId}/branding`, form);
      onSaved(`Branding of "${tenantName}" saved. Its users see it the next time they open the app.`);
    } catch (err) {
      setSaveError(readErrorMessage(err, "Could not save the branding."));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      kicker="TENANT BRANDING"
      title={`Branding of ${tenantName}`}
      titleId="tenant-branding-title"
      description="The logo is shown to this tenant's users on every plan. Brand colours need a plan that includes them."
      size="md"
      footer={
        <div style={{ display: "flex", justifyContent: "flex-end", gap: "var(--space-2)", width: "100%" }}>
          <Button variant="secondary" onClick={onClose} disabled={saving}>Cancel</Button>
          <Button onClick={() => void save()} busy={saving} disabled={!branding || invalid}>Save branding</Button>
        </div>
      }
    >
      {loading ? (
        <div role="status" aria-busy="true" aria-label="Loading branding">
          <span className="sr-only">Loading branding...</span>
          <SkeletonCard>
            <SkeletonText height="1rem" width="30%" />
            <SkeletonText height="3rem" width="45%" />
            <SkeletonText height="1rem" width="55%" />
            <SkeletonText height="2.5rem" />
          </SkeletonCard>
        </div>
      ) : loadError ? (
        <Notice tone="danger">{loadError}</Notice>
      ) : branding ? (
        <div style={{ display: "grid", gap: "var(--space-4)" }}>
          {saveError && <Notice tone="danger">{saveError}</Notice>}
          <div>
            <strong>Logo</strong>
            <div style={{ display: "flex", alignItems: "center", gap: "var(--space-3)", marginTop: "var(--space-2)" }}>
              {logoUrl && !removeLogo && !file ? (
                <img src={logoUrl} alt="Current logo" style={{ maxHeight: "48px", maxWidth: "160px", objectFit: "contain" }} />
              ) : (
                <span className="app-cell-sub">{file ? file.name : "No logo"}</span>
              )}
              {logoUrl && !file && (
                <Button variant="secondary" size="sm" onClick={() => setRemoveLogo((r) => !r)}>
                  {removeLogo ? "Keep logo" : "Remove logo"}
                </Button>
              )}
            </div>
            <label htmlFor="branding-logo" style={{ display: "block", marginTop: "var(--space-2)" }}>
              Upload a new logo (PNG or SVG, up to 512 KB)
            </label>
            <input id="branding-logo" type="file" accept="image/png,image/svg+xml"
              onChange={(e) => pickFile(e.target.files?.[0] ?? null)} />
            {fileError && <Notice tone="danger">{fileError}</Notice>}
          </div>
          <div className="app-form-grid">
            <TextField id="branding-primary" label="Primary colour" value={primary} placeholder="#1A2B3C"
              disabled={!branding.colorsAllowed} onChange={(e) => setPrimary(e.target.value.trim())}
              error={colorError(primary)}
              hint={branding.colorsAllowed ? "Buttons and links." : "This tenant's plan does not include brand colours."} />
            <TextField id="branding-accent" label="Accent colour" value={accent} placeholder="#D4A017"
              disabled={!branding.colorsAllowed} onChange={(e) => setAccent(e.target.value.trim())}
              error={colorError(accent)}
              hint={branding.colorsAllowed ? "Highlights." : undefined} />
          </div>
        </div>
      ) : null}
    </Modal>
  );
}

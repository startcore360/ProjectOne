import { type FormEvent, useEffect, useMemo, useState } from "react";
import { Button, Modal, TextField } from "../../../design-system/components";
import { Icon } from "../../../components/ui/Icon";
import {
  buildTargetLoginUrl,
  isValidInstitutionSlug,
  normalizeInstitutionSlug,
} from "../../../lib/tenantHost";
import { landingApi, type PublicInstitutionItem } from "../api/landingApi";

export interface InstitutionSelectorModalProps {
  isOpen: boolean;
  onClose: () => void;
  onNavigate?: (url: string) => void;
}

const defaultNavigate = (url: string) => {
  window.location.assign(url);
};

export function InstitutionSelectorModal({
  isOpen,
  onClose,
  onNavigate = defaultNavigate,
}: InstitutionSelectorModalProps) {
  const [institutions, setInstitutions] = useState<PublicInstitutionItem[]>([]);
  const [loadingDirectory, setLoadingDirectory] = useState(false);
  const [search, setSearch] = useState("");
  const [showManual, setShowManual] = useState(false);
  const [manualCode, setManualCode] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    if (!isOpen) return;
    let active = true;
    const load = async () => {
      setLoadingDirectory(true);
      try {
        const items = await landingApi.listPublicInstitutions();
        if (!active) return;
        setInstitutions(items);
      } catch {
        if (!active) return;
        setInstitutions([]);
      } finally {
        if (active) setLoadingDirectory(false);
      }
    };
    void load();
    return () => {
      active = false;
    };
  }, [isOpen]);

  const filteredInstitutions = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return institutions;
    return institutions.filter(
      (inst) =>
        inst.name.toLowerCase().includes(q) ||
        inst.slug.toLowerCase().includes(q) ||
        inst.tenantType.toLowerCase().includes(q),
    );
  }, [institutions, search]);

  const handleSelectSlug = (slug: string) => {
    // Navigation only — remembered preference is saved ONLY after login succeeds.
    onNavigate(buildTargetLoginUrl(slug, window.location));
  };

  const handleManualSubmit = (e: FormEvent) => {
    e.preventDefault();
    const normalized = normalizeInstitutionSlug(manualCode);
    if (!normalized) {
      setError("Enter your institution code to continue.");
      return;
    }
    if (!isValidInstitutionSlug(normalized)) {
      setError("Use only letters, numbers, and hyphens (e.g. greenwood or apex-college).");
      return;
    }
    setError("");
    handleSelectSlug(normalized);
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      kicker="WORKSPACE DISCOVERY"
      title="Sign in to EDU 360"
      description="Search and select your institution below, or open the EDU 360 Platform Console."
      size="sm"
    >
      <div className="selector-form">
        <TextField
          id="institution-search-input"
          label="Search institution"
          placeholder="Type institution name…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          autoFocus
        />

        {loadingDirectory ? (
          <div className="selector-empty">Loading institutions…</div>
        ) : filteredInstitutions.length > 0 ? (
          <div className="selector-list" role="listbox" aria-label="Institutions">
            {filteredInstitutions.map((inst) => (
              <button
                key={inst.slug}
                type="button"
                role="option"
                aria-selected={false}
                className="selector-item"
                onClick={() => handleSelectSlug(inst.slug)}
              >
                <div>
                  <span className="selector-item__name">{inst.name}</span>
                  <span className="selector-item__meta">
                    {inst.tenantType.replaceAll("_", " ")} · {inst.slug}
                  </span>
                </div>
                <Icon name="chevron-right" size={18} />
              </button>
            ))}
          </div>
        ) : (
          <div className="selector-empty">
            {search.trim()
              ? `No listed institution matching "${search.trim()}".`
              : "No public directory institutions listed yet. Use your institution code below or ask your Platform Admin to opt in."}
          </div>
        )}

        <div>
          <button
            type="button"
            className="app-text-link"
            style={{ background: "none", border: 0, cursor: "pointer", padding: 0 }}
            onClick={() => {
              setShowManual((v) => !v);
              setError("");
            }}
          >
            {showManual ? "Hide manual code entry" : "Don't see your institution? Enter code manually"}
          </button>
        </div>

        {showManual && (
          <form className="selector-form" onSubmit={handleManualSubmit} noValidate>
            <TextField
              id="institution-code-input"
              label="Institution code"
              placeholder="e.g. greenwood"
              autoCapitalize="none"
              autoCorrect="off"
              spellCheck={false}
              value={manualCode}
              onChange={(e) => {
                setManualCode(e.target.value);
                if (error) setError("");
              }}
              error={error}
            />
            <Button type="submit" block>
              Continue to institution login
            </Button>
          </form>
        )}

        <div className="selector-divider">or</div>

        <button
          type="button"
          className="selector-platform-btn"
          onClick={() => handleSelectSlug("platform")}
        >
          <div>
            <span className="selector-platform-btn__title">EDU 360 Platform Console</span>
            <span className="selector-platform-btn__desc">
              For Product Owners, Platform Admins, and support teams
            </span>
          </div>
          <Icon name="chevron-right" size={18} />
        </button>
      </div>
    </Modal>
  );
}

import { useState, useEffect } from "react";
import { Modal, Button, Notice, toast } from "../../../../design-system/components";
import Edu360Logo from "../../../../assets/Edu360Logo";
import {
  type EstimateData,
  formatEstimateShareText,
  generateEstimateImageBlob,
  generateEstimatePdfBlob,
} from "./estimateExportUtils";

export interface BillingEstimateShareModalProps {
  isOpen: boolean;
  onClose: () => void;
  estimate: EstimateData | null;
}

export function BillingEstimateShareModal({ isOpen, onClose, estimate }: BillingEstimateShareModalProps) {
  const [institutionName, setInstitutionName] = useState(estimate?.institutionName || estimate?.recipientName || "");
  const [contactPerson, setContactPerson] = useState(estimate?.contactPerson || "");
  const [contactPhone, setContactPhone] = useState(estimate?.contactPhone || "");
  const [contactEmail, setContactEmail] = useState(estimate?.contactEmail || "");
  const [recipientGst, setRecipientGst] = useState(estimate?.recipientGst || "");
  const [recipientAddress, setRecipientAddress] = useState(estimate?.recipientAddress || "");
  const [validUntil, setValidUntil] = useState(estimate?.validUntil || estimate?.dueDate || "");
  const [notes, setNotes] = useState(estimate?.notes || "");
  const [preparedBy, setPreparedBy] = useState(estimate?.preparedBy || "Commercial Operations Desk");

  const [sharing, setSharing] = useState(false);
  const [downloadingFormat, setDownloadingFormat] = useState<"image" | "pdf" | null>(null);
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (estimate) {
      setInstitutionName(estimate.institutionName || estimate.recipientName || "");
      setContactPerson(estimate.contactPerson || "");
      setContactPhone(estimate.contactPhone || "");
      setContactEmail(estimate.contactEmail || "");
      setRecipientGst(estimate.recipientGst || "");
      setRecipientAddress(estimate.recipientAddress || "");
      setValidUntil(estimate.validUntil || estimate.dueDate || "");
      setNotes(estimate.notes || "");
      setPreparedBy(estimate.preparedBy || "Commercial Operations Desk");
      setError("");
    }
  }, [estimate]);

  if (!estimate) return null;

  const currentEstimate: EstimateData = {
    ...estimate,
    institutionName: institutionName.trim() || estimate.institutionName || estimate.recipientName,
    recipientName: institutionName.trim() || estimate.recipientName,
    contactPerson: contactPerson.trim() || undefined,
    contactPhone: contactPhone.trim() || undefined,
    contactEmail: contactEmail.trim() || undefined,
    recipientGst: recipientGst.trim() || undefined,
    recipientAddress: recipientAddress.trim() || undefined,
    validUntil: validUntil.trim() || undefined,
    dueDate: validUntil.trim() || estimate.dueDate,
    notes: notes.trim() || undefined,
    preparedBy: preparedBy.trim() || undefined,
  };

  const shareText = formatEstimateShareText(currentEstimate);
  const safeFilename = `edu360-${(currentEstimate.statusBadge || "bill").toLowerCase()}-${currentEstimate.referenceNumber.toLowerCase().replace(/[^a-z0-9_-]/g, "-")}`;

  const handleDownloadPdf = async () => {
    try {
      setDownloadingFormat("pdf");
      setError("");
      const blob = await generateEstimatePdfBlob(currentEstimate);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `${safeFilename}.pdf`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
      toast.success("PDF ready", "Official PDF invoice downloaded successfully.");
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Failed to generate PDF.";
      setError(msg);
      toast.error("PDF download failed", msg);
    } finally {
      setDownloadingFormat(null);
    }
  };

  const handleDownloadImage = async () => {
    try {
      setDownloadingFormat("image");
      setError("");
      const blob = await generateEstimateImageBlob(currentEstimate);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `${safeFilename}.png`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
      toast.success("Image ready", "Official invoice image downloaded successfully.");
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Failed to generate image.";
      setError(msg);
      toast.error("Image download failed", msg);
    } finally {
      setDownloadingFormat(null);
    }
  };

  const handleCopyImage = async () => {
    try {
      setError("");
      const blob = await generateEstimateImageBlob(currentEstimate);
      if (navigator.clipboard && typeof ClipboardItem !== "undefined") {
        await navigator.clipboard.write([new ClipboardItem({ "image/png": blob })]);
        toast.success("Image copied", "Invoice image copied to clipboard with official EDU 360 logo.");
      } else {
        throw new Error("Direct image copying not supported on this browser.");
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Could not copy image to clipboard.";
      setError(msg);
      toast.error("Copy failed", msg);
    }
  };

  const handleCopyText = async () => {
    try {
      await navigator.clipboard.writeText(shareText);
      setCopied(true);
      toast.success("Text copied", "Formatted quotation summary copied to clipboard.");
      setTimeout(() => setCopied(false), 2500);
    } catch {
      toast.error("Copy failed", "Could not copy summary to clipboard.");
    }
  };

  const handleNativeShare = async () => {
    setSharing(true);
    setError("");
    try {
      if (typeof navigator.share !== "function") {
        throw new Error("Device sharing not supported on this browser.");
      }
      const blob = await generateEstimatePdfBlob(currentEstimate);
      const file = new File([blob], `${safeFilename}.pdf`, { type: "application/pdf" });

      if (navigator.canShare && navigator.canShare({ files: [file] })) {
        await navigator.share({
          title: `EDU 360 · ${currentEstimate.title}`,
          text: `Official document for ${currentEstimate.institutionName || currentEstimate.referenceNumber}`,
          files: [file],
        });
      } else {
        await navigator.share({
          title: `EDU 360 · ${currentEstimate.title}`,
          text: shareText,
        });
      }
      toast.success("Shared", "Quotation sent via share sheet.");
    } catch (err) {
      if (err instanceof Error && err.name !== "AbortError") {
        setError(err.message);
      }
    } finally {
      setSharing(false);
    }
  };

  const openWhatsApp = () => {
    window.open(`https://api.whatsapp.com/send?text=${encodeURIComponent(shareText)}`, "_blank", "noopener,noreferrer");
  };

  const openTelegram = () => {
    window.open(`https://t.me/share/url?url=${encodeURIComponent(window.location.origin)}&text=${encodeURIComponent(shareText)}`, "_blank", "noopener,noreferrer");
  };

  const openEmail = () => {
    const subject = encodeURIComponent(`${currentEstimate.title} - ${currentEstimate.referenceNumber}`);
    const body = encodeURIComponent(shareText);
    window.location.href = `mailto:${encodeURIComponent(contactEmail)}?subject=${subject}&body=${body}`;
  };

  const canNativeShare = typeof navigator !== "undefined" && typeof navigator.share === "function";

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      kicker="BILLING & COMMERCIALS"
      title={currentEstimate.title}
      size="md"
    >
      <div style={{ display: "grid", gap: "var(--space-3)" }}>
        {error && <Notice tone="danger" onDismiss={() => setError("")}>{error}</Notice>}

        {/* Clean, simple Recipient details */}
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))",
            gap: "var(--space-2)",
            background: "var(--color-bg-subtle)",
            padding: "var(--space-3)",
            borderRadius: "var(--radius-md)",
            border: "1px solid var(--color-border-divider)",
          }}
        >
          <label style={{ display: "grid", gap: "2px" }}>
            <span style={{ fontSize: "11px", fontWeight: "600", color: "var(--color-text-muted)" }}>
              Institution / College Name
            </span>
            <input
              type="text"
              className="app-input"
              style={{ fontSize: "12px", padding: "4px 8px", height: "30px" }}
              value={institutionName}
              onChange={(e) => setInstitutionName(e.target.value)}
              placeholder="e.g. Apex University"
            />
          </label>

          <label style={{ display: "grid", gap: "2px" }}>
            <span style={{ fontSize: "11px", fontWeight: "600", color: "var(--color-text-muted)" }}>
              Contact Person / Dean
            </span>
            <input
              type="text"
              className="app-input"
              style={{ fontSize: "12px", padding: "4px 8px", height: "30px" }}
              value={contactPerson}
              onChange={(e) => setContactPerson(e.target.value)}
              placeholder="e.g. Dr. Ramesh Kumar"
            />
          </label>

          <label style={{ display: "grid", gap: "2px" }}>
            <span style={{ fontSize: "11px", fontWeight: "600", color: "var(--color-text-muted)" }}>
              Phone / WhatsApp
            </span>
            <input
              type="text"
              className="app-input"
              style={{ fontSize: "12px", padding: "4px 8px", height: "30px" }}
              value={contactPhone}
              onChange={(e) => setContactPhone(e.target.value)}
              placeholder="e.g. +91 98765 43210"
            />
          </label>
        </div>

        {/* Modern Brand Logo Action Bar */}
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            gap: "14px",
            flexWrap: "wrap",
            padding: "var(--space-2) 0",
          }}
        >
          {/* WhatsApp */}
          <button
            type="button"
            onClick={openWhatsApp}
            title="Share on WhatsApp"
            style={{
              background: "transparent",
              border: "none",
              cursor: "pointer",
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              gap: "4px",
              padding: "4px",
            }}
          >
            <div
              style={{
                width: "42px",
                height: "42px",
                borderRadius: "50%",
                background: "#25D366",
                color: "#fff",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                boxShadow: "0 2px 6px rgba(37,211,102,0.35)",
              }}
            >
              <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor">
                <path d="M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413Z"/>
              </svg>
            </div>
            <span style={{ fontSize: "11px", fontWeight: "600", color: "var(--color-text)" }}>WhatsApp</span>
          </button>

          {/* Telegram */}
          <button
            type="button"
            onClick={openTelegram}
            title="Share on Telegram"
            style={{
              background: "transparent",
              border: "none",
              cursor: "pointer",
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              gap: "4px",
              padding: "4px",
            }}
          >
            <div
              style={{
                width: "42px",
                height: "42px",
                borderRadius: "50%",
                background: "#0088cc",
                color: "#fff",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                boxShadow: "0 2px 6px rgba(0,136,204,0.35)",
              }}
            >
              <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 0C5.37 0 0 5.37 0 12s5.37 12 12 12 12-5.37 12-12S18.63 0 12 0zm5.894 8.221-1.97 9.28c-.145.658-.537.818-1.084.508l-3-2.21-1.446 1.394c-.16.16-.295.295-.605.295l.213-3.053 5.56-5.023c.242-.213-.054-.333-.373-.121l-6.871 4.326-2.962-.924c-.643-.204-.657-.643.136-.953l11.57-4.458c.536-.196 1.006.128.832.943z"/>
              </svg>
            </div>
            <span style={{ fontSize: "11px", fontWeight: "600", color: "var(--color-text)" }}>Telegram</span>
          </button>

          {/* Device Share */}
          {canNativeShare && (
            <button
              type="button"
              onClick={() => void handleNativeShare()}
              disabled={sharing}
              title="Share"
              style={{
                background: "transparent",
                border: "none",
                cursor: "pointer",
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                gap: "4px",
                padding: "4px",
              }}
            >
              <div
                style={{
                  width: "42px",
                  height: "42px",
                  borderRadius: "50%",
                  background: "#6366f1",
                  color: "#fff",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  boxShadow: "0 2px 6px rgba(99,102,241,0.35)",
                }}
              >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/>
                  <line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/>
                </svg>
              </div>
              <span style={{ fontSize: "11px", fontWeight: "600", color: "var(--color-text)" }}>Share</span>
            </button>
          )}

          {/* Download PDF */}
          <button
            type="button"
            onClick={handleDownloadPdf}
            disabled={downloadingFormat !== null}
            title="Download PDF"
            style={{
              background: "transparent",
              border: "none",
              cursor: "pointer",
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              gap: "4px",
              padding: "4px",
            }}
          >
            <div
              style={{
                width: "42px",
                height: "42px",
                borderRadius: "50%",
                background: "#e11d48",
                color: "#fff",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                boxShadow: "0 2px 6px rgba(225,29,72,0.35)",
              }}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/>
              </svg>
            </div>
            <span style={{ fontSize: "11px", fontWeight: "600", color: "var(--color-text)" }}>PDF</span>
          </button>

          {/* Download Image */}
          <button
            type="button"
            onClick={() => void handleDownloadImage()}
            disabled={downloadingFormat !== null}
            title="Download Image"
            style={{
              background: "transparent",
              border: "none",
              cursor: "pointer",
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              gap: "4px",
              padding: "4px",
            }}
          >
            <div
              style={{
                width: "42px",
                height: "42px",
                borderRadius: "50%",
                background: "#059669",
                color: "#fff",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                boxShadow: "0 2px 6px rgba(5,150,105,0.35)",
              }}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/>
              </svg>
            </div>
            <span style={{ fontSize: "11px", fontWeight: "600", color: "var(--color-text)" }}>Image</span>
          </button>

          {/* Copy Image */}
          <button
            type="button"
            onClick={() => void handleCopyImage()}
            title="Copy Image with EDU 360 Logo"
            style={{
              background: "transparent",
              border: "none",
              cursor: "pointer",
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              gap: "4px",
              padding: "4px",
            }}
          >
            <div
              style={{
                width: "42px",
                height: "42px",
                borderRadius: "50%",
                background: "#0d9488",
                color: "#fff",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                boxShadow: "0 2px 6px rgba(13,148,136,0.35)",
              }}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
              </svg>
            </div>
            <span style={{ fontSize: "11px", fontWeight: "600", color: "var(--color-text)" }}>Copy Img</span>
          </button>

          {/* Copy Text */}
          <button
            type="button"
            onClick={() => void handleCopyText()}
            title="Copy Text"
            style={{
              background: "transparent",
              border: "none",
              cursor: "pointer",
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              gap: "4px",
              padding: "4px",
            }}
          >
            <div
              style={{
                width: "42px",
                height: "42px",
                borderRadius: "50%",
                background: copied ? "#10b981" : "#475569",
                color: "#fff",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                boxShadow: "0 2px 6px rgba(71,85,105,0.35)",
              }}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/><rect x="8" y="2" width="8" height="4" rx="1" ry="1"/>
              </svg>
            </div>
            <span style={{ fontSize: "11px", fontWeight: "600", color: "var(--color-text)" }}>
              {copied ? "Copied" : "Copy"}
            </span>
          </button>

          {/* Email */}
          <button
            type="button"
            onClick={openEmail}
            title="Email"
            style={{
              background: "transparent",
              border: "none",
              cursor: "pointer",
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              gap: "4px",
              padding: "4px",
            }}
          >
            <div
              style={{
                width: "42px",
                height: "42px",
                borderRadius: "50%",
                background: "#0284c7",
                color: "#fff",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                boxShadow: "0 2px 6px rgba(2,132,199,0.35)",
              }}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/>
              </svg>
            </div>
            <span style={{ fontSize: "11px", fontWeight: "600", color: "var(--color-text)" }}>Email</span>
          </button>
        </div>

        {/* Live Invoice Preview with official EDU 360 Logo */}
        <div
          style={{
            background: "#ffffff",
            color: "#0f172a",
            borderRadius: "var(--radius-md)",
            border: "1px solid #cbd5e1",
            padding: "var(--space-4)",
            maxHeight: "260px",
            overflowY: "auto",
            fontSize: "12px",
          }}
        >
          {/* Header with Official Edu360 Logo */}
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", borderBottom: "2px solid #0f172a", paddingBottom: "10px", marginBottom: "12px" }}>
            <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
              <div style={{ width: "34px", height: "34px", display: "flex", alignItems: "center", justifyContent: "center" }}>
                <Edu360Logo variant="mark" style={{ width: "100%", height: "100%" }} />
              </div>
              <div>
                <strong style={{ fontSize: "16px", color: "#0f172a", display: "block", letterSpacing: "-0.01em" }}>EDU 360</strong>
                <span style={{ fontSize: "10.5px", fontWeight: "700", color: "#059669", letterSpacing: "0.02em", display: "block" }}>
                  EDUCATION DRIVEN BY UNITY
                </span>
                <span style={{ fontSize: "10px", color: "#64748b" }}>Higher Education Academic Cloud Platform</span>
              </div>
            </div>
            <div style={{ textAlign: "right" }}>
              <span style={{ fontSize: "11px", fontWeight: "bold", color: "#059669", textTransform: "uppercase" }}>
                {currentEstimate.statusBadge || "OFFICIAL QUOTE"}
              </span>
              <div style={{ fontSize: "11px", color: "#475569" }}>#{currentEstimate.referenceNumber}</div>
            </div>
          </div>

          {/* Institutional Capacity & Scope Bar */}
          {currentEstimate.scope && (
            <div
              style={{
                background: "#f0fdf4",
                border: "1px solid #bbf7d0",
                borderRadius: "8px",
                marginBottom: "12px",
                overflow: "hidden",
                fontSize: "11px",
              }}
            >
              <div
                style={{
                  background: "#dcfce7",
                  padding: "5px 10px",
                  borderBottom: "1px solid #bbf7d0",
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                }}
              >
                <span style={{ color: "#047857", fontWeight: "700", fontSize: "10.5px" }}>
                  ACADEMIC LICENSING CAPACITY & SCOPE
                </span>
                {currentEstimate.scope.termMonths && (
                  <span style={{ color: "#059669", fontWeight: "600", fontSize: "10px" }}>
                    {currentEstimate.scope.termMonths} Months Term
                  </span>
                )}
              </div>
              <div style={{ padding: "8px 10px" }}>
                {currentEstimate.scope.planName && (
                  <div style={{ marginBottom: "6px" }}>
                    <span style={{ color: "#64748b", fontSize: "10px", display: "block" }}>LICENSED PLAN TIER</span>
                    <strong style={{ color: "#0f172a", fontSize: "13px" }}>{currentEstimate.scope.planName}</strong>
                  </div>
                )}
                <div
                  style={{
                    display: "grid",
                    gridTemplateColumns: "repeat(auto-fit, minmax(100px, 1fr))",
                    gap: "8px",
                    paddingTop: "6px",
                    borderTop: "1px solid #dcfce7",
                  }}
                >
                  {currentEstimate.scope.students && (
                    <div>
                      <span style={{ color: "#64748b", fontSize: "9.5px", display: "block" }}>STUDENTS</span>
                      <strong style={{ color: "#047857", fontSize: "12px" }}>
                        {currentEstimate.scope.students.toLocaleString("en-IN")}
                      </strong>
                    </div>
                  )}
                  {currentEstimate.scope.colleges && (
                    <div>
                      <span style={{ color: "#64748b", fontSize: "9.5px", display: "block" }}>COLLEGES</span>
                      <strong style={{ color: "#047857", fontSize: "12px" }}>
                        {currentEstimate.scope.colleges}
                      </strong>
                    </div>
                  )}
                  {currentEstimate.scope.schools && (
                    <div>
                      <span style={{ color: "#64748b", fontSize: "9.5px", display: "block" }}>SCHOOLS / DEPTS</span>
                      <strong style={{ color: "#047857", fontSize: "12px" }}>
                        {currentEstimate.scope.schools}
                      </strong>
                    </div>
                  )}
                  {currentEstimate.scope.staff && (
                    <div>
                      <span style={{ color: "#64748b", fontSize: "9.5px", display: "block" }}>FACULTY & STAFF</span>
                      <strong style={{ color: "#047857", fontSize: "12px" }}>
                        {currentEstimate.scope.staff.toLocaleString("en-IN")}
                      </strong>
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}

          {/* Billed to summary */}
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "10px", background: "#f8fafc", padding: "8px 10px", borderRadius: "6px" }}>
            <div>
              <span style={{ fontSize: "10px", color: "#64748b", textTransform: "uppercase", display: "block" }}>Billed To</span>
              <strong style={{ color: "#0f172a" }}>{currentEstimate.institutionName}</strong>
              {currentEstimate.contactPerson && <div style={{ color: "#475569" }}>Attn: {currentEstimate.contactPerson}</div>}
            </div>
            <div style={{ textAlign: "right" }}>
              <span style={{ fontSize: "10px", color: "#64748b", textTransform: "uppercase", display: "block" }}>Total</span>
              <strong style={{ fontSize: "15px", color: "#059669" }}>
                {currentEstimate.currencySymbol || "₹"}{Number(currentEstimate.total).toLocaleString("en-IN")}
              </strong>
            </div>
          </div>

          {/* Line items list */}
          <div style={{ display: "grid", gap: "4px" }}>
            {currentEstimate.lines.map((l, i) => (
              <div key={i} style={{ display: "flex", justifyContent: "space-between", padding: "4px 0", borderBottom: "1px solid #f1f5f9" }}>
                <span style={{ color: "#334155" }}>{l.description}</span>
                <strong style={{ color: "#0f172a" }}>
                  {currentEstimate.currencySymbol || "₹"}{Number(l.amount).toLocaleString("en-IN")}
                </strong>
              </div>
            ))}
          </div>
        </div>

        {/* Footer */}
        <div style={{ display: "flex", justifyContent: "flex-end" }}>
          <Button variant="secondary" size="sm" onClick={onClose}>
            Close
          </Button>
        </div>
      </div>
    </Modal>
  );
}

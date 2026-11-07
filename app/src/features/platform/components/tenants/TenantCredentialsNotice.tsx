import { useState } from "react";
import { Button, Notice } from "../../../../design-system/components";

export type CreatedTenantCredentials = {
  tenantName: string;
  adminEmail: string;
  loginAddress: string;
  temporaryPassword: string;
};

export interface TenantCredentialsNoticeProps {
  credentials: CreatedTenantCredentials;
  onDismiss: () => void;
}

// The password is returned only once, by the create call, and never stored by the browser. It stays
// on screen until dismissed, so a refresh of the tenant list can't remove it before it's copied.
export function TenantCredentialsNotice({ credentials, onDismiss }: TenantCredentialsNoticeProps) {
  const [copyState, setCopyState] = useState<"idle" | "copied" | "failed">("idle");

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(credentials.temporaryPassword);
      setCopyState("copied");
    } catch {
      setCopyState("failed");
    }
  };

  return (
    <Notice tone="success">
      <div style={{ display: "grid", gap: "var(--space-2)" }}>
        <strong>{credentials.tenantName} is ready. Save this password now: it won't be shown again.</strong>
        <div>
          Sign-in address: <strong>{credentials.loginAddress}</strong>
        </div>
        <div>
          First admin: <strong>{credentials.adminEmail}</strong>
        </div>
        <div>
          Temporary password: <code style={{ userSelect: "all", fontWeight: "var(--fw-semibold)" }}>{credentials.temporaryPassword}</code>
        </div>
        <div>They must change it the first time they sign in.</div>
        <div style={{ display: "flex", flexWrap: "wrap", alignItems: "center", gap: "var(--space-2)" }}>
          <Button variant="secondary" size="sm" onClick={() => void copy()}>
            Copy password
          </Button>
          <Button variant="secondary" size="sm" onClick={onDismiss}>
            I've saved it
          </Button>
          <span aria-live="polite">
            {copyState === "copied" && "Copied."}
            {copyState === "failed" && "Couldn't copy. Select the password and copy it yourself."}
          </span>
        </div>
      </div>
    </Notice>
  );
}

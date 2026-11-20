import { useState } from "react";
import { Button, CheckboxField } from "../../../design-system/components";
import "./BackupCodes.css";

export interface BackupCodesProps {
  codes: string[];
  /** Shown on the confirm button, e.g. "Continue to sign in". */
  continueLabel: string;
  onContinue: () => void;
}

// MFA-01: the one and only time backup codes are shown. The user must confirm they saved them
// before moving on, because the server keeps only scrambled (hashed) copies and cannot show them again.
export function BackupCodes({ codes, continueLabel, onContinue }: BackupCodesProps) {
  const [saved, setSaved] = useState(false);
  const [copied, setCopied] = useState(false);
  const text = codes.join("\n");

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };

  const download = () => {
    const blob = new Blob(
      [`EDU 360 backup codes\nEach code works once. Keep them somewhere safe.\n\n${text}\n`],
      { type: "text/plain" },
    );
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "edu360-backup-codes.txt";
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="backup-codes">
      <p className="backup-codes__intro">
        If you lose your phone, sign in with one of these codes instead of the authenticator code. Each
        code works once. <strong>They will not be shown again.</strong>
      </p>
      <ol className="backup-codes__list" aria-label="Backup codes">
        {codes.map((code) => (
          <li key={code}>
            <code>{code}</code>
          </li>
        ))}
      </ol>
      <div className="backup-codes__actions">
        <Button type="button" variant="secondary" onClick={() => void copy()}>
          {copied ? "Copied" : "Copy codes"}
        </Button>
        <Button type="button" variant="secondary" onClick={download}>
          Download as file
        </Button>
      </div>
      <CheckboxField
        id="backup-codes-saved"
        label="I have saved these codes somewhere safe"
        checked={saved}
        onChange={(e) => setSaved(e.target.checked)}
      />
      <Button type="button" size="lg" block disabled={!saved} onClick={onContinue} className="auth-submit">
        {continueLabel}
      </Button>
    </div>
  );
}

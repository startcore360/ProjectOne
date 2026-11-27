import { useEffect, useState, type FormEvent } from "react";
import axios from "axios";
import { Button, Notice, TextField } from "../../../design-system/components";
import { getApiErrorMessage } from "../../../services/apiHelper";
import { profileService, type ProfileResponse } from "../api/profileService";
import { LockIcon, PencilIcon } from "./ProfileIcons";
import { roleLabel } from "./profileFormatting";

type FieldErrors = { displayName?: string; mobile?: string };

function backendFieldErrors(err: unknown): FieldErrors | null {
  if (axios.isAxiosError<{ fieldErrors?: FieldErrors }>(err)) {
    return err.response?.data?.fieldErrors ?? null;
  }
  return null;
}

function backendMessage(err: unknown): string | null {
  if (axios.isAxiosError<{ error?: string }>(err)) {
    return err.response?.data?.error ? getApiErrorMessage(err) : null;
  }
  return null;
}

function friendlyError(err: unknown): string {
  const status = axios.isAxiosError(err) ? err.response?.status : undefined;
  if (status === 404 || (status !== undefined && status >= 500)) {
    return "Could not save your profile. Please try again.";
  }
  return backendMessage(err) ?? "Could not save your profile. Please try again.";
}

export interface ProfileDetailsTabProps {
  profile: ProfileResponse;
  isPlatform: boolean;
  onSaved: (profile: ProfileResponse) => void;
  onDirtyChange: (dirty: boolean) => void;
}

// PROFILE_EDIT_OWN (Scope: Self). Read view first; "Edit" opens the form. Only the display name
// (and, for tenant accounts, the mobile number) can change - email doubles as the Keycloak login
// name and the role is assigned by an administrator, so both are shown locked.
export function ProfileDetailsTab({ profile, isPlatform, onSaved, onDirtyChange }: ProfileDetailsTabProps) {
  const [editing, setEditing] = useState(false);
  const [displayName, setDisplayName] = useState(profile.displayName);
  const [mobile, setMobile] = useState(profile.mobile ?? "");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  // Guardian mobile changes are intentionally unavailable until phone verification is connected.
  // The API enforces WF-30 with PHONE_VERIFICATION_UNAVAILABLE; do not present an editable field
  // that the server is guaranteed to reject.
  const isGuardian = profile.userType === "GUARDIAN";
  const dirty =
    editing && (displayName !== profile.displayName || (!isPlatform && !isGuardian && mobile !== (profile.mobile ?? "")));

  useEffect(() => {
    onDirtyChange(dirty);
  }, [dirty, onDirtyChange]);

  const startEdit = () => {
    setDisplayName(profile.displayName);
    setMobile(profile.mobile ?? "");
    setFieldErrors({});
    setSaveError(null);
    setEditing(true);
  };

  const cancel = () => {
    setEditing(false);
    setFieldErrors({});
    setSaveError(null);
    onDirtyChange(false);
  };

  const validate = (): FieldErrors => {
    const errors: FieldErrors = {};
    if (!displayName.trim()) errors.displayName = "Display name must not be blank";
    if (!isPlatform && !isGuardian && mobile && !/^\+?[1-9]\d{6,14}$/.test(mobile)) {
      errors.mobile = "Mobile must be a valid E.164-style number";
    }
    return errors;
  };

  const submit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setSaveError(null);
    const clientErrors = validate();
    setFieldErrors(clientErrors);
    if (Object.keys(clientErrors).length > 0) return;

    setSaving(true);
    try {
      const { data } = await profileService.update({
        displayName: displayName.trim(),
        ...(isPlatform || isGuardian ? {} : { mobile: mobile || undefined }),
      });
      onSaved(data);
      setEditing(false);
      onDirtyChange(false);
    } catch (err) {
      const backendErrors = backendFieldErrors(err);
      if (backendErrors) setFieldErrors(backendErrors);
      else setSaveError(friendlyError(err));
    } finally {
      setSaving(false);
    }
  };

  const lockedRows = (
    <>
      <div className="app-profile-field">
        <dt>
          Email <span className="app-profile-lock" title="Managed by your administrator"><LockIcon /></span>
        </dt>
        <dd>{profile.email}</dd>
      </div>
      <div className="app-profile-field">
        <dt>
          {isPlatform ? "Platform role" : "User type"}{" "}
          <span className="app-profile-lock" title="Managed by your administrator"><LockIcon /></span>
        </dt>
        <dd>{roleLabel(profile)}</dd>
      </div>
    </>
  );

  return (
    <section className="app-profile-card" aria-labelledby="profile-details-title">
      <header className="app-profile-card__header">
        <div>
          <h3 id="profile-details-title" className="app-profile-card__title">Personal details</h3>
          <p className="app-profile-card__description">
            {editing ? "Update how your name appears across EDU 360." : "This is how you appear to other people in EDU 360."}
          </p>
        </div>
        {!editing && (
          <Button variant="secondary" size="sm" onClick={startEdit}>
            <PencilIcon /> Edit
          </Button>
        )}
      </header>

      {!editing ? (
        <>
          <dl className="app-profile-fields">
            <div className="app-profile-field">
              <dt>Display name</dt>
              <dd>{profile.displayName}</dd>
            </div>
            {!isPlatform && (
              <div className="app-profile-field">
                <dt>Mobile</dt>
                <dd>{profile.mobile || <span className="app-profile-empty">Not added</span>}</dd>
              </div>
            )}
            {lockedRows}
          </dl>
          <p className="app-hint">Email and role are managed by your administrator.</p>
        </>
      ) : (
        <form onSubmit={submit} noValidate className="app-profile-form">
          {Object.keys(fieldErrors).length > 0 && (
            <Notice tone="danger">Please correct the highlighted fields below.</Notice>
          )}
          {saveError && <Notice tone="danger">{saveError}</Notice>}
          <div className="app-profile-form__grid">
            <TextField
              id="profile-display-name"
              label="Display name"
              required
              autoFocus
              disabled={saving}
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              error={fieldErrors.displayName}
            />
            {!isPlatform && !isGuardian && (
              <TextField
                id="profile-mobile"
                label="Mobile"
                placeholder="+15551234567"
                disabled={saving}
                value={mobile}
                onChange={(e) => setMobile(e.target.value)}
                error={fieldErrors.mobile}
              />
            )}
          </div>
          <dl className="app-profile-fields app-profile-fields--locked">
            {!isPlatform && isGuardian && (
              <div className="app-profile-field">
                <dt>Mobile <span className="app-profile-lock" title="Phone verification is required"><LockIcon /></span></dt>
                <dd>{profile.mobile || <span className="app-profile-empty">Not added</span>}</dd>
              </div>
            )}
            {lockedRows}
          </dl>
          <p className="app-hint">
            {isGuardian
              ? "Mobile updates for guardian accounts require phone verification, which is not available yet. Contact your school office to change this number. Email and role are managed by your administrator."
              : "Email and role are managed by your administrator."}
          </p>
          <div className="app-profile-actions">
            <Button type="button" variant="tertiary" onClick={cancel} disabled={saving}>
              Cancel
            </Button>
            <Button type="submit" busy={saving} disabled={!dirty && !saving}>
              {saving ? "Saving…" : "Save changes"}
            </Button>
          </div>
        </form>
      )}
    </section>
  );
}

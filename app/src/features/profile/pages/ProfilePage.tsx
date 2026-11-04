import { useEffect, useState, type FormEvent } from "react";
import axios from "axios";
import "./ProfilePage.css";
import { Button, Kicker, TextField } from "../../../design-system/components";
import { Badge, Spinner } from "../../../components/ui";
import { PageHeader } from "../../../components/layout/PageHeader";
import { ErrorState } from "../../../components/feedback/ErrorState";
import { useAppSelector } from "../../../store/hooks";
import { profileService, type ProfileResponse } from "../api/profileService";

// PROFILE_EDIT_OWN (Sign-in epic, Scope: Self). View and edit your own display name, and
// (tenant accounts only) your own mobile number. Role/platformRole/email are shown read-only -
// there is no field for them on the backend's ProfileUpdateRequest, by design (it doubles as
// the Keycloak login username for email - see profileService.ts).
type FieldErrors = { displayName?: string; mobile?: string };

function backendFieldErrors(err: unknown): FieldErrors | null {
  if (axios.isAxiosError<{ fieldErrors?: FieldErrors }>(err)) {
    return err.response?.data?.fieldErrors ?? null;
  }
  return null;
}

function backendMessage(err: unknown): string | null {
  if (axios.isAxiosError<{ error?: string }>(err)) {
    return err.response?.data?.error ?? null;
  }
  return null;
}

export default function ProfilePage() {
  // Whether this signed-in principal is tenant or platform decides which fields the edit form
  // shows (platform accounts have no mobile column at all - see ProfileResponse.fromPlatform on
  // the backend). Read from the store, matching how AppLayout already branches on the same
  // fields, rather than re-deriving it from this screen's own fetch.
  const authUser = useAppSelector((state) => state.auth.user);
  const isPlatform = !!authUser?.platformRole;

  const [profile, setProfile] = useState<ProfileResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [displayName, setDisplayName] = useState("");
  const [mobile, setMobile] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [success, setSuccess] = useState(false);

  // No synchronous setState call sits directly in this function's body - only inside the
  // promise callbacks below - so it is safe to call from the mount effect as-is; loading/error
  // start correctly set via useState's initial values above, and "Retry" resets them itself
  // before calling this again.
  const fetchProfile = () => {
    profileService
      .get()
      .then(({ data }) => {
        setProfile(data);
        setDisplayName(data.displayName);
        setMobile(data.mobile ?? "");
        setLoadError(null);
      })
      .catch((err) => {
        setLoadError(
          backendMessage(err) ?? "Could not load your profile. Please try again.",
        );
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchProfile();
  }, []);

  const retry = () => {
    setLoading(true);
    setLoadError(null);
    fetchProfile();
  };

  const validate = (): FieldErrors => {
    const errors: FieldErrors = {};
    if (!displayName.trim()) {
      errors.displayName = "Display name must not be blank";
    }
    if (!isPlatform && mobile && !/^\+?[1-9]\d{6,14}$/.test(mobile)) {
      errors.mobile = "Mobile must be a valid E.164-style number";
    }
    return errors;
  };

  const submit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setSaveError(null);
    setSuccess(false);
    const clientErrors = validate();
    setFieldErrors(clientErrors);
    if (Object.keys(clientErrors).length > 0) {
      return;
    }

    setSaving(true);
    try {
      const { data } = await profileService.update({
        displayName,
        ...(isPlatform ? {} : { mobile: mobile || undefined }),
      });
      setProfile(data);
      setDisplayName(data.displayName);
      setMobile(data.mobile ?? "");
      setFieldErrors({});
      setSuccess(true);
    } catch (err) {
      const backendErrors = backendFieldErrors(err);
      if (backendErrors) {
        setFieldErrors(backendErrors);
      } else {
        setSaveError(backendMessage(err) ?? "Could not save your profile. Please try again.");
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="profile-page">
      <PageHeader title="My profile" description="View and update your own display name and contact details." />

      {loading && (
        <section className="profile-card">
          <Spinner label="Loading your profile…" />
        </section>
      )}

      {!loading && loadError && (
        <ErrorState
          message={loadError}
          action={
            <Button variant="secondary" onClick={retry}>
              Retry
            </Button>
          }
        />
      )}

      {!loading && !loadError && profile && (
        <section className="profile-card" aria-labelledby="profile-title">
          <Kicker>{isPlatform ? "PLATFORM ACCOUNT" : "ACCOUNT"}</Kicker>
          <h2 id="profile-title" className="profile-card__title">
            Account details
          </h2>

          <dl className="profile-readonly">
            <div>
              <dt>Email</dt>
              <dd>{profile.email}</dd>
            </div>
            <div>
              <dt>{isPlatform ? "Platform role" : "User type"}</dt>
              <dd>
                <Badge tone="info">{(profile.platformRole ?? profile.userType ?? "—").replaceAll("_", " ")}</Badge>
              </dd>
            </div>
          </dl>

          {success && (
            <div className="profile-message profile-message--success" role="status">
              Your profile has been updated.
            </div>
          )}
          {saveError && (
            <div className="profile-message profile-message--error" role="alert">
              {saveError}
            </div>
          )}

          <form onSubmit={submit} noValidate>
            <TextField
              id="profile-display-name"
              label="Display name"
              required
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              aria-invalid={!!fieldErrors.displayName}
              aria-describedby={fieldErrors.displayName ? "profile-display-name-error" : undefined}
            />
            {fieldErrors.displayName && (
              <p className="profile-field-error" id="profile-display-name-error" role="alert">
                {fieldErrors.displayName}
              </p>
            )}

            {!isPlatform && (
              <>
                <TextField
                  id="profile-mobile"
                  label="Mobile"
                  placeholder="+15551234567"
                  value={mobile}
                  onChange={(e) => setMobile(e.target.value)}
                  aria-invalid={!!fieldErrors.mobile}
                  aria-describedby={fieldErrors.mobile ? "profile-mobile-error" : undefined}
                />
                {fieldErrors.mobile && (
                  <p className="profile-field-error" id="profile-mobile-error" role="alert">
                    {fieldErrors.mobile}
                  </p>
                )}
              </>
            )}

            <Button type="submit" busy={saving} className="profile-submit">
              {saving ? "Saving…" : "Save changes"}
            </Button>
          </form>
        </section>
      )}
    </div>
  );
}

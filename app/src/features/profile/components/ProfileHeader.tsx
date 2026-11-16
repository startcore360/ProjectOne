import { useRef, type ChangeEvent } from "react";
import { Badge } from "../../../design-system/components";
import type { ProfileResponse } from "../api/profileService";
import { CameraIcon } from "./ProfileIcons";
import { initialsFor, roleLabel } from "./profileFormatting";

export interface ProfileHeaderProps {
  profile: ProfileResponse;
  isPlatform: boolean;
  photoLoading: boolean;
  photoObjectUrl: string | null;
  photoUploading: boolean;
  photoSuccess: boolean;
  photoError: string | null;
  onPhotoSelected: (e: ChangeEvent<HTMLInputElement>) => void;
}

// Identity banner at the top of "My profile": who you are, how you sign in, what you hold.
// Photo upload is for tenant accounts only - the backend has no photo store for platform users.
export function ProfileHeader({
  profile,
  isPlatform,
  photoLoading,
  photoObjectUrl,
  photoUploading,
  photoSuccess,
  photoError,
  onPhotoSelected,
}: ProfileHeaderProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);

  return (
    <section className="app-profile-hero" aria-labelledby="profile-name">
      <div className="app-profile-hero__avatar-wrap">
        {photoLoading ? (
          <span className="app-profile-hero__avatar app-profile-hero__avatar--loading" aria-hidden="true" />
        ) : photoObjectUrl ? (
          <img className="app-profile-hero__avatar" src={photoObjectUrl} alt="" />
        ) : (
          <span className="app-profile-hero__avatar" aria-hidden="true">
            {initialsFor(profile.displayName)}
          </span>
        )}
        {!isPlatform && (
          <>
            <input
              ref={fileInputRef}
              id="profile-photo-input"
              type="file"
              accept="image/jpeg,image/png"
              tabIndex={-1}
              aria-hidden="true"
              className="app-file-input"
              onChange={onPhotoSelected}
            />
            <button
              type="button"
              className="app-profile-hero__photo-btn"
              onClick={() => fileInputRef.current?.click()}
              disabled={photoUploading}
              aria-label={photoObjectUrl ? "Change photo" : "Add photo"}
              title={photoObjectUrl ? "Change photo (JPEG or PNG, up to 2 MB)" : "Add photo (JPEG or PNG, up to 2 MB)"}
            >
              <CameraIcon />
            </button>
          </>
        )}
      </div>

      <div className="app-profile-hero__identity">
        <p className="app-profile-hero__kicker">{isPlatform ? "Platform account" : "Your account"}</p>
        <h2 id="profile-name" className="app-profile-hero__name">{profile.displayName}</h2>
        <p className="app-profile-hero__email">{profile.email}</p>
        <div className="app-profile-hero__chips">
          <Badge tone="info">{roleLabel(profile)}</Badge>
          <Badge tone="neutral">{isPlatform ? "Platform account" : "Tenant account"}</Badge>
        </div>
        {photoUploading && <p className="app-hint" role="status">Uploading your photo…</p>}
        {photoSuccess && (
          <p className="app-status-text app-status-text--success" role="status">Your photo has been updated.</p>
        )}
        {photoError && (
          <p className="app-status-text app-status-text--error" role="alert">{photoError}</p>
        )}
      </div>
    </section>
  );
}

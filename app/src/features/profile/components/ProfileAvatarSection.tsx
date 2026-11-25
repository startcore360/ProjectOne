import { useRef, type ChangeEvent } from "react";
import { Button } from "../../../design-system/components";

export interface ProfileAvatarSectionProps {
  displayName: string;
  isPlatform: boolean;
  photoLoading: boolean;
  photoObjectUrl: string | null;
  photoUploading: boolean;
  photoSuccess: boolean;
  photoError: string | null;
  onPhotoSelected: (e: ChangeEvent<HTMLInputElement>) => void;
}

function initialsFor(name: string): string {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((x) => x[0]?.toUpperCase()).join("") || "A";
}

export function ProfileAvatarSection({
  displayName,
  isPlatform,
  photoLoading,
  photoObjectUrl,
  photoUploading,
  photoSuccess,
  photoError,
  onPhotoSelected,
}: ProfileAvatarSectionProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);

  return (
    <div className="app-avatar-row">
      {photoLoading ? (
        <span className="app-avatar app-avatar--loading" aria-hidden="true" />
      ) : photoObjectUrl ? (
        <img className="app-avatar" src={photoObjectUrl} alt="" loading="lazy" decoding="async" />
      ) : (
        <span className="app-avatar app-avatar--placeholder" aria-hidden="true">
          {initialsFor(displayName)}
        </span>
      )}

      {isPlatform ? (
        <p className="app-hint">Profile photos are not available for platform accounts.</p>
      ) : (
        <div className="app-avatar-actions">
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
          <Button
            type="button"
            variant="secondary"
            size="sm"
            busy={photoUploading}
            onClick={() => fileInputRef.current?.click()}
          >
            {photoUploading ? "Uploading…" : photoObjectUrl ? "Change photo" : "Add photo"}
          </Button>
          <p className="app-hint">JPEG or PNG, up to 2 MB.</p>
          {photoSuccess && (
            <p className="app-status-text app-status-text--success" role="status">
              Your photo has been updated.
            </p>
          )}
          {photoError && (
            <p className="app-status-text app-status-text--error" role="alert">
              {photoError}
            </p>
          )}
        </div>
      )}
    </div>
  );
}

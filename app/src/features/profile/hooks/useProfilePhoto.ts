import { useEffect, useRef, useState } from "react";
import { profileService } from "../api/profileService";

// Read-only version of the blob-fetch pattern ProfilePage.tsx uses for its own (editable) photo
// section - this app authenticates with a bearer header (services/axios.ts), not cookies, so a
// plain <img src="/me/profile/photo"> can never carry it; the bytes have to come through axios
// and get turned into an object URL. Used by the sidebar, where the photo is display-only.
//
// photoUrl is the signal, not the fetch target itself: non-null just means "this user has a
// photo, go fetch it" (see ProfileResponse.photoUrl's own comment) - it is always exactly
// "/me/profile/photo" today, but the hook re-fetches whenever its identity changes rather than
// assuming that, so it stays correct if that ever becomes per-photo.
export function useProfilePhoto(photoUrl: string | null | undefined): string | null {
  const [objectUrl, setObjectUrl] = useState<string | null>(null);
  const liveUrlRef = useRef<string | null>(null);

  // No branch here ever calls setState synchronously in the effect body itself (only inside
  // the async .then()/.catch() callbacks, or inside the returned cleanup). When photoUrl goes
  // from a value to null/undefined, this run's body does nothing - the PREVIOUS run's cleanup
  // (captured with the old photoUrl) is what revokes the old blob: URL and clears objectUrl,
  // since React always runs the old effect's cleanup before the new effect's body.
  useEffect(() => {
    if (!photoUrl) {
      return;
    }
    let active = true;
    profileService
      .getPhoto()
      .then(({ data }) => {
        if (!active) return;
        const url = URL.createObjectURL(data);
        liveUrlRef.current = url;
        setObjectUrl(url);
      })
      .catch(() => {
        if (active) setObjectUrl(null);
      });
    return () => {
      active = false;
      if (liveUrlRef.current) {
        URL.revokeObjectURL(liveUrlRef.current);
        liveUrlRef.current = null;
        setObjectUrl(null);
      }
    };
  }, [photoUrl]);

  return objectUrl;
}

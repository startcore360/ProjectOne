import { useEffect, useState } from "react";
import { profileService } from "../api/profileService";

interface PhotoCacheEntry {
  key: string;
  objectUrl: string;
}

let cachedPhoto: PhotoCacheEntry | null = null;
let pendingPhotoFetch: Promise<string | null> | null = null;

export function clearProfilePhotoCache() {
  if (cachedPhoto) {
    try {
      URL.revokeObjectURL(cachedPhoto.objectUrl);
    } catch {
      /* ignore */
    }
    cachedPhoto = null;
  }
  pendingPhotoFetch = null;
}

async function fetchPhoto(cacheKey: string): Promise<string | null> {
  try {
    const { data } = await profileService.getPhoto();
    const url = URL.createObjectURL(data);
    if (cachedPhoto && cachedPhoto.key !== cacheKey) {
      try {
        URL.revokeObjectURL(cachedPhoto.objectUrl);
      } catch {
        /* ignore */
      }
    }
    cachedPhoto = { key: cacheKey, objectUrl: url };
    return url;
  } catch {
    return null;
  } finally {
    pendingPhotoFetch = null;
  }
}

// Read-only version of the blob-fetch pattern ProfilePage.tsx uses for its own (editable) photo
// section - this app authenticates with a bearer header (services/axios.ts), not cookies, so a
// plain <img src="/me/profile/photo"> can never carry it; the bytes have to come through axios
// and get turned into an object URL. Used by the sidebar, where the photo is display-only.
//
// photoUrl is the signal, not the fetch target itself: non-null just means "this user has a
// photo, go fetch it" (see ProfileResponse.photoUrl's own comment) - it is always exactly
// "/me/profile/photo" today, but the hook re-fetches whenever its identity changes rather than
// assuming that, so it stays correct if that ever becomes per-photo.
//
// revision: bumped by the profile page after each upload (AuthUser.photoRevision). The path
// stays identical when a photo is replaced, so without it the avatar kept the old picture.
//
// Cached per session keyed by `${photoUrl}:${revision}` so remounting does not refetch.
export function useProfilePhoto(photoUrl: string | null | undefined, revision = 0): string | null {
  const cacheKey = photoUrl ? `${photoUrl}:${revision}` : null;

  const [objectUrl, setObjectUrl] = useState<string | null>(() => {
    if (!cacheKey) return null;
    return cachedPhoto?.key === cacheKey ? cachedPhoto.objectUrl : null;
  });

  useEffect(() => {
    if (!cacheKey) {
      setObjectUrl(null);
      return;
    }

    let active = true;

    if (cachedPhoto?.key === cacheKey) {
      setObjectUrl(cachedPhoto.objectUrl);
      return;
    }

    if (!pendingPhotoFetch) {
      pendingPhotoFetch = fetchPhoto(cacheKey);
    }

    pendingPhotoFetch.then((url) => {
      if (!active) return;
      setObjectUrl(url);
    });

    return () => {
      active = false;
    };
  }, [cacheKey]);

  return objectUrl;
}

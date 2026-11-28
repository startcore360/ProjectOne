import { useEffect, useState } from "react";
import { profileService } from "../api/profileService";

interface PhotoCacheEntry {
  key: string;
  objectUrl: string;
}
interface PendingPhotoFetch {
  key: string;
  promise: Promise<string | null>;
}

let cachedPhoto: PhotoCacheEntry | null = null;
let pendingPhotoFetch: PendingPhotoFetch | null = null;
let requestVersion = 0;

export function clearProfilePhotoCache() {
  requestVersion += 1;
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

async function fetchPhoto(cacheKey: string, version: number): Promise<string | null> {
  try {
    const { data } = await profileService.getPhoto();
    const url = URL.createObjectURL(data);
    if (version === requestVersion) {
      if (cachedPhoto && cachedPhoto.key !== cacheKey) {
        try {
          URL.revokeObjectURL(cachedPhoto.objectUrl);
        } catch {
          /* ignore */
        }
      }
      cachedPhoto = { key: cacheKey, objectUrl: url };
    } else {
      URL.revokeObjectURL(url);
    }
    return version === requestVersion ? url : null;
  } catch {
    return null;
  }
}

// Read-only version of the blob-fetch pattern ProfilePage.tsx uses for its own (editable) photo.
// Bytes are fetched through the authenticated axios client and exposed as an object URL.
// Cache entries are keyed by photo URL and revision so remounts do not refetch.
export function useProfilePhoto(photoUrl: string | null | undefined, revision = 0): string | null {
  const cacheKey = photoUrl ? `${photoUrl}:${revision}` : null;
  const [loaded, setLoaded] = useState<{ key: string; url: string | null } | null>(null);

  useEffect(() => {
    if (!cacheKey || cachedPhoto?.key === cacheKey) return;

    let active = true;
    let request = pendingPhotoFetch?.key === cacheKey ? pendingPhotoFetch.promise : null;
    if (!request) {
      const version = ++requestVersion;
      request = fetchPhoto(cacheKey, version);
      pendingPhotoFetch = { key: cacheKey, promise: request };
      void request.finally(() => {
        if (pendingPhotoFetch?.promise === request) pendingPhotoFetch = null;
      });
    }

    request.then((url) => {
      if (active) setLoaded({ key: cacheKey, url });
    });

    return () => {
      active = false;
    };
  }, [cacheKey]);

  if (!cacheKey) return null;
  if (cachedPhoto?.key === cacheKey) return cachedPhoto.objectUrl;
  return loaded?.key === cacheKey ? loaded.url : null;
}

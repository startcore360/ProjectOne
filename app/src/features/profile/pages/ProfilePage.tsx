import { useCallback, useEffect, useRef, useState, type ChangeEvent } from "react";
import axios from "axios";
import { useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { getApiErrorMessage } from "../../../services/apiHelper";
import {
  ConfirmDialog,
  ErrorState,
  PageHeader,
  SkeletonText,
  Tabs,
  Button,
  toast,
} from "../../../design-system/components";
import { useAppDispatch, useAppSelector } from "../../../store/hooks";
import { profileUpdated } from "../../../store/slices/authSlice";
import { profileService, type ProfileResponse } from "../api/profileService";
import {
  ProfileDetailsTab,
  ProfileDevicesTab,
  ProfileHeader,
  ProfileSecurityTab,
} from "../components";

const ALLOWED_PHOTO_TYPES = ["image/jpeg", "image/png"];
const MAX_PHOTO_BYTES = 2 * 1024 * 1024;
const PROFILE_TABS = [
  { id: "details", label: "Details" },
  { id: "security", label: "Security" },
  { id: "devices", label: "Devices" },
] as const;
type ProfileTabId = (typeof PROFILE_TABS)[number]["id"];

type PendingNavigation =
  | { kind: "tab"; tab: ProfileTabId }
  | { kind: "route"; to: { pathname: string; search: string; hash: string } };

function isProfileTab(value: string | null): value is ProfileTabId {
  return PROFILE_TABS.some((tab) => tab.id === value);
}

function backendMessage(err: unknown): string | null {
  if (axios.isAxiosError<{ error?: string }>(err)) {
    return err.response?.data?.error ? getApiErrorMessage(err) : null;
  }
  return null;
}

function friendlyError(err: unknown, fallback: string): string {
  const status = axios.isAxiosError(err) ? err.response?.status : undefined;
  if (status === 404 || (status !== undefined && status >= 500)) return fallback;
  return backendMessage(err) ?? fallback;
}

export default function ProfilePage() {
  const isPlatform = useAppSelector((state) => !!state.auth.user?.platformRole);
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedTab = searchParams.get("tab");
  const activeTab: ProfileTabId = isProfileTab(requestedTab) ? requestedTab : "details";

  const [profile, setProfile] = useState<ProfileResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [detailsDirty, setDetailsDirty] = useState(false);
  const [pendingNavigation, setPendingNavigation] = useState<PendingNavigation | null>(null);

  const [photoObjectUrl, setPhotoObjectUrl] = useState<string | null>(null);
  const [photoLoading, setPhotoLoading] = useState(false);
  const [photoUploading, setPhotoUploading] = useState(false);
  const [photoError, setPhotoError] = useState<string | null>(null);
  const [photoSuccess, setPhotoSuccess] = useState(false);
  const objectUrlRef = useRef<string | null>(null);

  const setDisplayedPhoto = (url: string | null) => {
    if (objectUrlRef.current) URL.revokeObjectURL(objectUrlRef.current);
    objectUrlRef.current = url;
    setPhotoObjectUrl(url);
  };

  useEffect(() => {
    return () => {
      if (objectUrlRef.current) URL.revokeObjectURL(objectUrlRef.current);
    };
  }, []);

  const loadPhoto = async (hasPhoto: boolean) => {
    if (!hasPhoto) {
      setDisplayedPhoto(null);
      return;
    }
    setPhotoLoading(true);
    try {
      const { data } = await profileService.getPhoto();
      setDisplayedPhoto(URL.createObjectURL(data));
    } catch {
      setDisplayedPhoto(null);
    } finally {
      setPhotoLoading(false);
    }
  };

  const fetchProfile = () => {
    profileService
      .get()
      .then(({ data }) => {
        setProfile(data);
        setLoadError(null);
        void loadPhoto(!!data.photoUrl);
      })
      .catch((err) => {
        setLoadError(friendlyError(err, "Could not load your profile. Please try again."));
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchProfile();
    // Load once on mount; Retry calls fetchProfile directly.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const retry = () => {
    setLoading(true);
    setLoadError(null);
    fetchProfile();
  };

  const handleProfileSaved = useCallback((updatedProfile: ProfileResponse) => {
    setProfile(updatedProfile);
    dispatch(profileUpdated({ displayName: updatedProfile.displayName }));
    toast.success("Profile updated", "Your changes have been saved.");
  }, [dispatch]);

  const onPhotoSelected = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;

    setPhotoSuccess(false);
    setPhotoError(null);
    if (!ALLOWED_PHOTO_TYPES.includes(file.type)) {
      setPhotoError("Photo must be a JPEG or PNG image.");
      return;
    }
    if (file.size > MAX_PHOTO_BYTES) {
      setPhotoError("Photo must be 2 MB or smaller.");
      return;
    }

    setPhotoUploading(true);
    try {
      const { data } = await profileService.uploadPhoto(file);
      setProfile(data);
      dispatch(profileUpdated({ photoUrl: data.photoUrl, photoChanged: true }));
      await loadPhoto(!!data.photoUrl);
      setPhotoSuccess(true);
    } catch (err) {
      setPhotoError(friendlyError(err, "Could not upload your photo. Please try again."));
    } finally {
      setPhotoUploading(false);
    }
  };

  const commitTabChange = useCallback((tab: ProfileTabId) => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      next.set("tab", tab);
      return next;
    });
  }, [setSearchParams]);

  const requestTabChange = (nextTab: string) => {
    if (!isProfileTab(nextTab) || nextTab === activeTab) return;
    if (detailsDirty) {
      setPendingNavigation({ kind: "tab", tab: nextTab });
      return;
    }
    commitTabChange(nextTab);
  };

  useEffect(() => {
    if (!detailsDirty) return;
    const warnBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", warnBeforeUnload);
    return () => window.removeEventListener("beforeunload", warnBeforeUnload);
  }, [detailsDirty]);

  useEffect(() => {
    if (!detailsDirty) return;
    const interceptNavigation = (event: MouseEvent) => {
      if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      if (!(event.target instanceof Element)) return;
      const anchor = event.target.closest("a[href]");
      if (!(anchor instanceof HTMLAnchorElement) || anchor.target && anchor.target !== "_self" || anchor.hasAttribute("download")) return;

      const destination = new URL(anchor.href, window.location.href);
      if (destination.origin !== window.location.origin) return;
      if (
        destination.pathname === location.pathname &&
        destination.search === location.search &&
        destination.hash === location.hash
      ) return;

      event.preventDefault();
      event.stopImmediatePropagation();
      setPendingNavigation({
        kind: "route",
        to: { pathname: destination.pathname, search: destination.search, hash: destination.hash },
      });
    };

    document.addEventListener("click", interceptNavigation, true);
    return () => document.removeEventListener("click", interceptNavigation, true);
  }, [detailsDirty, location.hash, location.pathname, location.search]);

  const confirmNavigation = () => {
    if (!pendingNavigation) return;
    const destination = pendingNavigation;
    setPendingNavigation(null);
    setDetailsDirty(false);
    if (destination.kind === "tab") {
      commitTabChange(destination.tab);
    } else {
      navigate(destination.to);
    }
  };

  return (
    <div className="app-page app-profile-page">
      <PageHeader title="My profile" description="Your account, sign-in security, and active devices." />

      {loading && !profile && (
        <section className="app-profile-card app-profile-loading-card" role="status" aria-busy="true" aria-label="Loading your profile">
          <span className="sr-only">Loading your profile...</span>
          <div className="app-profile-skeleton" aria-hidden="true">
            <span />
            <div><span /><span /><span /></div>
          </div>
          <SkeletonText height="1rem" width="55%" />
          <SkeletonText height="0.875rem" width="35%" />
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

      {!loadError && profile && (
        <>
          <ProfileHeader
            profile={profile}
            isPlatform={isPlatform}
            photoLoading={photoLoading}
            photoObjectUrl={photoObjectUrl}
            photoUploading={photoUploading}
            photoSuccess={photoSuccess}
            photoError={photoError}
            onPhotoSelected={(event) => void onPhotoSelected(event)}
          />

          <Tabs
            items={[...PROFILE_TABS]}
            activeId={activeTab}
            onChange={requestTabChange}
            ariaLabel="Profile sections"
            className="app-profile-tabs"
          />

          <div
            id={`panel-${activeTab}`}
            role="tabpanel"
            aria-labelledby={`tab-${activeTab}`}
            className="app-profile-tab-panel"
            tabIndex={0}
          >
            {activeTab === "details" && (
              <ProfileDetailsTab
                profile={profile}
                isPlatform={isPlatform}
                onSaved={handleProfileSaved}
                onDirtyChange={setDetailsDirty}
              />
            )}
            {activeTab === "security" && <ProfileSecurityTab isPlatform={isPlatform} />}
            {activeTab === "devices" && <ProfileDevicesTab />}
          </div>
        </>
      )}

      <ConfirmDialog
        isOpen={pendingNavigation !== null}
        onClose={() => setPendingNavigation(null)}
        onConfirm={confirmNavigation}
        title="Discard unsaved changes?"
        message="Your profile edits have not been saved. Leave this page and discard them?"
        kicker="UNSAVED PROFILE"
        confirmLabel="Discard changes"
        cancelLabel="Keep editing"
        confirmTone="danger"
      />
    </div>
  );
}

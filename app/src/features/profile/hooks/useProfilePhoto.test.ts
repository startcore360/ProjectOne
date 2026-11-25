import { renderHook, waitFor } from "@testing-library/react";
import type { InternalAxiosRequestConfig } from "axios";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useProfilePhoto, clearProfilePhotoCache } from "./useProfilePhoto";
import { profileService } from "../api/profileService";

vi.mock("../api/profileService");

describe("useProfilePhoto", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    clearProfilePhotoCache();
  });

  it("fetches photo on initial mount and reuses cached objectUrl without refetching on remount", async () => {
    const mockBlob = new Blob(["test-photo"], { type: "image/jpeg" });
    vi.mocked(profileService.getPhoto).mockResolvedValue({
      data: mockBlob,
      status: 200,
      statusText: "OK",
      headers: {},
      config: {} as InternalAxiosRequestConfig,
    });

    const { result, unmount } = renderHook(() => useProfilePhoto("/me/profile/photo", 0));

    await waitFor(() => {
      expect(result.current).toBeTruthy();
    });

    const firstUrl = result.current;
    expect(profileService.getPhoto).toHaveBeenCalledTimes(1);

    // Unmount hook
    unmount();

    // Re-mount hook with the exact same revision
    const { result: remountResult } = renderHook(() => useProfilePhoto("/me/profile/photo", 0));

    // Immediately available
    expect(remountResult.current).toBe(firstUrl);
    // Did NOT call getPhoto again!
    expect(profileService.getPhoto).toHaveBeenCalledTimes(1);
  });

  it("refetches when revision changes and updates the cached object URL", async () => {
    const mockBlob1 = new Blob(["photo-v1"], { type: "image/jpeg" });
    const mockBlob2 = new Blob(["photo-v2"], { type: "image/jpeg" });

    vi.mocked(profileService.getPhoto)
      .mockResolvedValueOnce({ data: mockBlob1, status: 200, statusText: "OK", headers: {}, config: {} as any })
      .mockResolvedValueOnce({ data: mockBlob2, status: 200, statusText: "OK", headers: {}, config: {} as any });

    const { result, rerender } = renderHook(
      ({ rev }: { rev: number }) => useProfilePhoto("/me/profile/photo", rev),
      { initialProps: { rev: 0 } }
    );

    await waitFor(() => {
      expect(result.current).toBeTruthy();
    });

    const initialUrl = result.current;
    expect(profileService.getPhoto).toHaveBeenCalledTimes(1);

    // Upload new photo -> bump revision to 1
    rerender({ rev: 1 });

    await waitFor(() => {
      expect(result.current).not.toBe(initialUrl);
    });

    expect(profileService.getPhoto).toHaveBeenCalledTimes(2);
  });

  it("returns null when photoUrl is null or undefined", () => {
    const { result } = renderHook(() => useProfilePhoto(null, 0));
    expect(result.current).toBeNull();
    expect(profileService.getPhoto).not.toHaveBeenCalled();
  });
});

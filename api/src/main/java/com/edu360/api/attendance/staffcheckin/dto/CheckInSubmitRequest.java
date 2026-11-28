package com.edu360.api.attendance.staffcheckin.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One check-in submission. {@code photoFileId} is the caller's own selfie, uploaded through the
 * files feature within the last 10 minutes and never used before. {@code livenessFrames} holds
 * 2-5 JPEG/PNG frames (base64 in JSON) captured a few hundred milliseconds apart, at most 2 MiB
 * each, used only for the basic liveness score - never for face matching. {@code deviceType}
 * (e.g. ANDROID, IOS, WEB) is informational only.
 */
public record CheckInSubmitRequest(
        java.util.UUID photoFileId,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal gpsAccuracyMeters,
        String deviceType,
        List<byte[]> livenessFrames
) {
}

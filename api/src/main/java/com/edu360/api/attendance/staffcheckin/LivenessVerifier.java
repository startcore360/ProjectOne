package com.edu360.api.attendance.staffcheckin;

import java.util.List;

/**
 * Boundary for liveness detection so the actual algorithm is swappable (a future vendor SDK or
 * ML model) without touching StaffCheckInService. Implementations receive 2-3 frames captured
 * over roughly 1-2 seconds and return a confidence in [0,1] that the frames show a live subject
 * rather than one still image. Liveness is NOT identity verification: no implementation may claim
 * to recognise or match the person; a low score only routes the check-in to a human reviewer.
 */
public interface LivenessVerifier {

    /**
     * @param frames 2 or more raw image byte arrays, in capture order
     * @return confidence in [0,1]; 0 for degenerate input (fewer than 2 frames)
     */
    double scoreLiveness(List<byte[]> frames);
}

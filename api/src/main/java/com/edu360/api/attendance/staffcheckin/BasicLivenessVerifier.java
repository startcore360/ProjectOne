package com.edu360.api.attendance.staffcheckin;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

/**
 * WHAT: a self-hosted, deliberately basic liveness heuristic. It answers only "do these 2-5
 * frames, taken a fraction of a second apart, look like a live camera sequence rather than one
 * still image sent several times?". It does NOT recognise faces, compare against a reference
 * photo or verify identity in any way, and a determined person can defeat it (e.g. a printed
 * photo moved in front of the camera). That is why a low score only ever routes the check-in to a
 * human reviewer (PROVISIONAL), never to a refusal.
 *
 * HOW: each frame is decoded (JPEG/PNG via ImageIO) and reduced to a 32x32 grayscale grid; the
 * mean absolute per-cell difference between consecutive frames is the "motion" signal.
 *   - near-zero motion (identical / re-sent still image)      -> low confidence;
 *   - natural micro-movement, blinking, lighting flicker      -> confidence ramps up to 1.0;
 *   - very large change (unrelated images stitched together)  -> capped low.
 * Frames that cannot be decoded, or fewer than two, score 0.0 (inconclusive, so PROVISIONAL).
 * Comparing pixels, not encoded bytes, matters: two JPEG encodings of the same still scene differ
 * almost everywhere byte-for-byte, so a byte comparison would score a still image as "live".
 *
 * Swap this class for a vendor SDK or model behind {@link LivenessVerifier} without touching callers.
 */
@Component
public class BasicLivenessVerifier implements LivenessVerifier {

    static final int GRID = 32;
    /** Mean grid delta (0-255 scale) below this is treated as "no motion at all". */
    static final double STILL_DELTA = 0.5;
    /** Mean grid delta at/above this is treated as fully live. */
    static final double LIVE_DELTA = 4.0;
    /** Mean grid delta above this means the frames are not one continuous capture. */
    static final double SCENE_CHANGE_DELTA = 60.0;
    static final double SCENE_CHANGE_CAP = 0.3;
    /** Frames larger than this (about 4096 x 4096) are refused before decoding. */
    static final long MAX_PIXELS = 16_777_216L;

    @Override
    public double scoreLiveness(List<byte[]> frames) {
        if (frames == null || frames.size() < 2) {
            return 0.0;
        }
        List<double[]> grids = new ArrayList<>(frames.size());
        for (byte[] frame : frames) {
            double[] grid = decodeToGrid(frame);
            if (grid == null) {
                return 0.0;
            }
            grids.add(grid);
        }
        double total = 0.0;
        double max = 0.0;
        for (int i = 1; i < grids.size(); i++) {
            double delta = meanAbsoluteDelta(grids.get(i - 1), grids.get(i));
            total += delta;
            max = Math.max(max, delta);
        }
        double avg = total / (grids.size() - 1);
        if (max > SCENE_CHANGE_DELTA) {
            return Math.min(SCENE_CHANGE_CAP, ramp(avg));
        }
        return ramp(avg);
    }

    private static double ramp(double delta) {
        if (delta <= STILL_DELTA) {
            return 0.0;
        }
        return Math.min(1.0, (delta - STILL_DELTA) / (LIVE_DELTA - STILL_DELTA));
    }

    /** Decodes and downsamples to a GRID x GRID grayscale luminance grid, or null when not an image. */
    static double[] decodeToGrid(byte[] frame) {
        if (frame == null || frame.length == 0) {
            return null;
        }
        BufferedImage image;
        try (javax.imageio.stream.ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(frame))) {
            java.util.Iterator<javax.imageio.ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                return null;
            }
            javax.imageio.ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                // Read the header first: a tiny file can declare enormous dimensions (decompression bomb).
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
                    return null;
                }
                image = reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            return null;
        }
        if (image == null || image.getWidth() < GRID || image.getHeight() < GRID) {
            return null;
        }
        double[] grid = new double[GRID * GRID];
        int[] counts = new int[GRID * GRID];
        int w = image.getWidth();
        int h = image.getHeight();
        // Sample at most ~4 pixels per cell per axis to keep the cost bounded for large frames.
        int stepX = Math.max(1, w / (GRID * 4));
        int stepY = Math.max(1, h / (GRID * 4));
        for (int y = 0; y < h; y += stepY) {
            int gy = Math.min(GRID - 1, y * GRID / h);
            for (int x = 0; x < w; x += stepX) {
                int gx = Math.min(GRID - 1, x * GRID / w);
                int rgb = image.getRGB(x, y);
                double luma = 0.299 * ((rgb >> 16) & 0xFF) + 0.587 * ((rgb >> 8) & 0xFF) + 0.114 * (rgb & 0xFF);
                grid[gy * GRID + gx] += luma;
                counts[gy * GRID + gx]++;
            }
        }
        for (int i = 0; i < grid.length; i++) {
            grid[i] = counts[i] == 0 ? 0.0 : grid[i] / counts[i];
        }
        return grid;
    }

    private static double meanAbsoluteDelta(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            sum += Math.abs(a[i] - b[i]);
        }
        return sum / a.length;
    }
}

package com.edu360.api.attendance.staffcheckin;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class BasicLivenessVerifierTest {

    private final BasicLivenessVerifier verifier = new BasicLivenessVerifier();

    /** A 96x96 gradient "scene", brightened by {@code shift} and with a little sensor noise. */
    private static byte[] frame(int shift, long seed, String format) throws IOException {
        BufferedImage img = new BufferedImage(96, 96, BufferedImage.TYPE_INT_RGB);
        Random noise = new Random(seed);
        for (int y = 0; y < 96; y++) {
            for (int x = 0; x < 96; x++) {
                int v = Math.max(0, Math.min(255, x + y + shift + noise.nextInt(3) - 1));
                img.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private static byte[] solid(int v) throws IOException {
        BufferedImage img = new BufferedImage(96, 96, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 96; y++) {
            for (int x = 0; x < 96; x++) {
                img.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void sameStillImageResent_scoresLow() throws IOException {
        byte[] still = frame(0, 1, "png");
        assertThat(verifier.scoreLiveness(List.of(still, still.clone(), still.clone()))).isLessThan(0.1);
    }

    @Test
    void sameSceneReEncodedAsJpeg_isNotMistakenForLive() throws IOException {
        // Different noise seeds re-encode to very different bytes, but the pixels barely move.
        double score = verifier.scoreLiveness(List.of(frame(0, 1, "jpg"), frame(0, 2, "jpg")));
        assertThat(score).isLessThan(BasicLivenessVerifier.SCENE_CHANGE_CAP + 0.01)
                .isLessThan(StaffCheckInService.LIVENESS_CONFIDENCE_THRESHOLD);
    }

    @Test
    void naturalSmallChangesBetweenFrames_scoreAsLive() throws IOException {
        double score = verifier.scoreLiveness(List.of(frame(0, 1, "png"), frame(6, 2, "png"), frame(12, 3, "png")));
        assertThat(score).isGreaterThanOrEqualTo(StaffCheckInService.LIVENESS_CONFIDENCE_THRESHOLD);
    }

    @Test
    void unrelatedImagesStitchedTogether_areCappedLow() throws IOException {
        assertThat(verifier.scoreLiveness(List.of(solid(0), solid(255)))).isLessThanOrEqualTo(BasicLivenessVerifier.SCENE_CHANGE_CAP);
    }

    @Test
    void undecodableOrTooFewFrames_scoreZero() throws IOException {
        assertThat(verifier.scoreLiveness(List.of(new byte[]{1, 2, 3}, new byte[]{4, 5, 6}))).isZero();
        assertThat(verifier.scoreLiveness(List.of(frame(0, 1, "png")))).isZero();
        assertThat(verifier.scoreLiveness(List.of())).isZero();
        assertThat(verifier.scoreLiveness(null)).isZero();
    }
}

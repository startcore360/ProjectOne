package com.edu360.api.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.edu360.api.document.service.IdCardRenderer;

class IdCardRendererTest {
    private final IdCardRenderer renderer = new IdCardRenderer();

    @Test
    void svgUsesTemplateVisibilityAndValidatedThemeColours() {
        IdCardRenderer.CardData card = new IdCardRenderer.CardData(
                "EDU-12345678", "Asha Rao", "STU-01", "Example College",
                "2026-11-26", "2027-11-26", "PORTRAIT",
                """
                {"showInstitution":false,"showProfileCode":false,"showCardNumber":true,
                 "showIssueDate":false,"showExpiry":false,"showQr":false,
                 "headerColor":"#123456","backgroundColor":"#abcdef","textColor":"#112233"}
                """);

        String svg = renderer.toSvg(card, "signed-token");

        assertThat(svg).contains("fill=\"#123456\"")
                .contains("fill=\"#abcdef\"")
                .contains("fill=\"#112233\"")
                .contains("Asha Rao")
                .contains("EDU-12345678")
                .doesNotContain("Example College")
                .doesNotContain("STU-01")
                .doesNotContain("Scan to verify identity")
                .doesNotContain("Issued:")
                .doesNotContain("Expires:");
    }

    @Test
    void pdfRendererCreatesAValidPdfForAConfiguredCard() {
        IdCardRenderer.CardData card = new IdCardRenderer.CardData(
                "EDU-12345678", "Asha Rao", "STU-01", "Example College",
                "2026-11-26", null, "LANDSCAPE", "{\"showQr\":false}");

        byte[] pdf = renderer.toPdf(List.of(card), List.of("signed-token"));

        assertThat(new String(pdf, java.nio.charset.StandardCharsets.US_ASCII)).startsWith("%PDF");
    }
}

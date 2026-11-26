package com.edu360.api.document.service;

import java.io.ByteArrayOutputStream;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

@Component
public class IdCardRenderer {
    private static final ObjectMapper JSON = new ObjectMapper();

    public record CardData(String cardNumber, String fullName, String profileCode,
            String institutionName, String issueDate, String expiryDate, String layout,
            String fieldsConfigJson) { }

    public String toSvg(CardData card, String qrToken) {
        try {
            JsonNode config = config(card.fieldsConfigJson());
            boolean landscape = "LANDSCAPE".equals(card.layout());
            boolean showQr = enabled(config, "showQr", true);
            int width = landscape ? 760 : 520;
            int height = landscape ? 520 : 760;
            int qrSize = landscape ? 190 : 210;
            int qrX = landscape ? width - qrSize - 44 : (width - qrSize) / 2;
            int qrY = landscape ? 160 : 430;
            StringBuilder path = new StringBuilder();
            if (showQr) {
                BitMatrix qr = qr(qrToken, qrSize);
                int scale = 1;
                for (int y = 0; y < qr.getHeight(); y++) {
                    for (int x = 0; x < qr.getWidth(); x++) {
                        if (qr.get(x, y)) {
                            path.append('M').append(qrX + x * scale).append(' ')
                                    .append(qrY + y * scale).append("h").append(scale)
                                    .append("v").append(scale).append("h-").append(scale).append('z');
                        }
                    }
                }
            }

            int textX = landscape ? 48 : 60;
            int nameY = landscape ? 205 : 300;
            StringBuilder svg = new StringBuilder();
            svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(width)
                    .append("\" height=\"").append(height).append("\" viewBox=\"0 0 ")
                    .append(width).append(' ').append(height)
                    .append("\" role=\"img\" aria-label=\"EDU 360 identity card\">");
            svg.append("<rect x=\"2\" y=\"2\" width=\"").append(width - 4)
                    .append("\" height=\"").append(height - 4)
                    .append("\" rx=\"26\" fill=\"").append(colour(config, "backgroundColor", "#ffffff"))
                    .append("\" stroke=\"#cbd5e1\" stroke-width=\"4\"/>");
            svg.append("<rect x=\"2\" y=\"2\" width=\"").append(width - 4)
                    .append("\" height=\"92\" rx=\"24\" fill=\"")
                    .append(colour(config, "headerColor", "#172554")).append("\"/>");
            if (enabled(config, "showInstitution", true)) {
                svg.append(text(textX, 58, 28, "#ffffff", true, card.institutionName()));
            }
            svg.append(text(textX, nameY, 30, colour(config, "textColor", "#0f172a"), true, card.fullName()));
            if (enabled(config, "showProfileCode", true)) {
                svg.append(text(textX, nameY + 42, 20, "#475569", false, card.profileCode()));
            }
            int lineY = nameY + 96;
            if (enabled(config, "showCardNumber", true)) {
                svg.append(text(textX, lineY, 18, colour(config, "textColor", "#0f172a"), false,
                        "Card: " + card.cardNumber()));
                lineY += 34;
            }
            if (enabled(config, "showIssueDate", true)) {
                svg.append(text(textX, lineY, 18, colour(config, "textColor", "#0f172a"), false,
                        "Issued: " + card.issueDate()));
                lineY += 34;
            }
            if (enabled(config, "showExpiry", true)) {
                svg.append(text(textX, lineY, 18, colour(config, "textColor", "#0f172a"), false,
                        "Expires: " + (card.expiryDate() == null ? "No expiry" : card.expiryDate())));
            }
            if (showQr) {
                svg.append("<path d=\"").append(path).append("\" fill=\"#0f172a\"/>");
                svg.append(text(landscape ? qrX : width / 2 - 68, qrY + qrSize + 28, 12,
                        "#64748b", false, "Scan to verify identity"));
            }
            return svg.append("</svg>").toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not render the identity-card QR code.", ex);
        }
    }

    public byte[] toPdf(List<CardData> cards, List<String> qrTokens) {
        if (cards.size() != qrTokens.size() || cards.isEmpty()) {
            throw new IllegalArgumentException("At least one card and matching QR token are required.");
        }
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int i = 0; i < cards.size(); i++) {
                CardData card = cards.get(i);
                JsonNode config = config(card.fieldsConfigJson());
                boolean landscape = "LANDSCAPE".equals(card.layout());
                PDRectangle pageSize = landscape ? new PDRectangle(540, 360) : new PDRectangle(360, 540);
                PDPage page = new PDPage(pageSize);
                document.addPage(page);
                boolean showQr = enabled(config, "showQr", true);
                PDImageXObject image = null;
                if (showQr) {
                    BitMatrix matrix = qr(qrTokens.get(i), 256);
                    ByteArrayOutputStream png = new ByteArrayOutputStream();
                    MatrixToImageWriter.writeToStream(matrix, "PNG", png);
                    image = PDImageXObject.createFromByteArray(document, png.toByteArray(), "identity-qr");
                }
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    float width = pageSize.getWidth(), height = pageSize.getHeight();
                    stream.setNonStrokingColor(pdfColour(colour(config, "backgroundColor", "#ffffff")));
                    stream.addRect(0, 0, width, height);
                    stream.fill();
                    stream.setNonStrokingColor(pdfColour(colour(config, "headerColor", "#172554")));
                    stream.addRect(0, height - 76, width, 76);
                    stream.fill();
                    if (enabled(config, "showInstitution", true)) {
                        drawText(stream, ascii(card.institutionName()), 28, height - 45, 18, true, 255, 255, 255);
                    }
                    drawText(stream, ascii(card.fullName()), 28, height - 125, 20, true,
                            pdfColour(colour(config, "textColor", "#0f172a")));
                    float textY = height - 150;
                    if (enabled(config, "showProfileCode", true)) {
                        drawText(stream, ascii(card.profileCode()), 28, textY, 12, false, 71, 85, 105);
                        textY -= 22;
                    }
                    if (enabled(config, "showCardNumber", true)) {
                        drawText(stream, "Card: " + ascii(card.cardNumber()), 28, textY, 12, false, 71, 85, 105);
                        textY -= 18;
                    }
                    if (enabled(config, "showIssueDate", true)) {
                        drawText(stream, "Issued: " + ascii(card.issueDate()), 28, textY, 12, false, 71, 85, 105);
                        textY -= 18;
                    }
                    if (enabled(config, "showExpiry", true)) {
                        drawText(stream, "Expires: " + ascii(card.expiryDate() == null ? "No expiry" : card.expiryDate()),
                                28, textY, 12, false, 71, 85, 105);
                    }
                    if (showQr) {
                        float qrSize = landscape ? 150 : 170;
                        float qrX = landscape ? width - qrSize - 26 : (width - qrSize) / 2;
                        float qrY = landscape ? 78 : 92;
                        stream.drawImage(image, qrX, qrY, qrSize, qrSize);
                    }
                }
            }
            document.save(output);
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not create the identity-card PDF.", ex);
        }
    }

    private static void drawText(PDPageContentStream stream, String value, float x, float y,
            float size, boolean bold, int red, int green, int blue) throws Exception {
        stream.beginText();
        stream.setNonStrokingColor(red, green, blue);
        stream.setFont(new PDType1Font(bold
                ? Standard14Fonts.FontName.HELVETICA_BOLD : Standard14Fonts.FontName.HELVETICA), size);
        stream.newLineAtOffset(x, y);
        stream.showText(value);
        stream.endText();
    }

    private static void drawText(PDPageContentStream stream, String value, float x, float y,
            float size, boolean bold, java.awt.Color colour) throws Exception {
        drawText(stream, value, x, y, size, bold, colour.getRed(), colour.getGreen(), colour.getBlue());
    }

    private static JsonNode config(String json) {
        try {
            JsonNode node = JSON.readTree(json == null || json.isBlank() ? "{}" : json);
            return node != null && node.isObject() ? node : JSON.createObjectNode();
        } catch (Exception ignored) {
            return JSON.createObjectNode();
        }
    }

    private static boolean enabled(JsonNode config, String key, boolean defaultValue) {
        JsonNode value = config.path(key);
        return value.isBoolean() ? value.asBoolean() : defaultValue;
    }

    private static String colour(JsonNode config, String key, String defaultValue) {
        String value = config.path(key).asText(defaultValue);
        return value.matches("#[0-9a-fA-F]{6}") ? value : defaultValue;
    }

    private static java.awt.Color pdfColour(String value) {
        return java.awt.Color.decode(value);
    }

    private static BitMatrix qr(String value, int size) throws Exception {
        return new MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, size, size,
                Map.of(EncodeHintType.MARGIN, 1));
    }

    private static String text(int x, int y, int size, String colour, boolean bold, String value) {
        return "<text x=\"" + x + "\" y=\"" + y + "\" fill=\"" + colour
                + "\" font-size=\"" + size + "\" font-family=\"Arial,sans-serif\""
                + (bold ? " font-weight=\"700\"" : "") + ">" + xml(value) + "</text>";
    }

    private static String xml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String ascii(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "").replaceAll("[^\\x20-\\x7E]", "?");
    }
}

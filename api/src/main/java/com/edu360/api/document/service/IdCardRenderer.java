package com.edu360.api.document.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.util.List;

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
    public record CardData(String cardNumber, String fullName, String profileCode,
            String institutionName, String issueDate, String expiryDate, String layout) { }

    public String toSvg(CardData card, String qrToken) {
        try {
            BitMatrix qr = qr(qrToken, 260);
            boolean landscape = "LANDSCAPE".equals(card.layout());
            int width = landscape ? 760 : 520;
            int height = landscape ? 520 : 760;
            int qrSize = landscape ? 190 : 210;
            int qrX = landscape ? width - qrSize - 44 : (width - qrSize) / 2;
            int qrY = landscape ? 160 : 430;
            StringBuilder path = new StringBuilder();
            int scale = qrSize / qr.getWidth();
            for (int y = 0; y < qr.getHeight(); y++) {
                for (int x = 0; x < qr.getWidth(); x++) {
                    if (qr.get(x, y)) {
                        path.append('M').append(qrX + x * scale).append(' ')
                                .append(qrY + y * scale).append("h").append(scale)
                                .append("v").append(scale).append("h-").append(scale).append('z');
                    }
                }
            }
            String name = xml(card.fullName());
            String code = xml(card.profileCode());
            String institution = xml(card.institutionName());
            String number = xml(card.cardNumber());
            String expiry = xml(card.expiryDate() == null ? "No expiry" : card.expiryDate());
            String issue = xml(card.issueDate());
            int textX = landscape ? 48 : 60;
            int nameY = landscape ? 205 : 300;
            return """
                    <svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d" role="img" aria-label="EDU 360 identity card">
                      <rect x="2" y="2" width="%d" height="%d" rx="26" fill="#ffffff" stroke="#cbd5e1" stroke-width="4"/>
                      <rect x="2" y="2" width="%d" height="92" rx="24" fill="#172554"/>
                      <text x="%d" y="58" fill="#ffffff" font-size="28" font-family="Arial,sans-serif" font-weight="700">%s</text>
                      <text x="%d" y="%d" fill="#0f172a" font-size="30" font-family="Arial,sans-serif" font-weight="700">%s</text>
                      <text x="%d" y="%d" fill="#475569" font-size="20" font-family="Arial,sans-serif">%s</text>
                      <text x="%d" y="%d" fill="#0f172a" font-size="18" font-family="Arial,sans-serif">Card: %s</text>
                      <text x="%d" y="%d" fill="#0f172a" font-size="18" font-family="Arial,sans-serif">Issued: %s</text>
                      <text x="%d" y="%d" fill="#0f172a" font-size="18" font-family="Arial,sans-serif">Expires: %s</text>
                      <path d="%s" fill="#0f172a"/>
                      <text x="%d" y="%d" fill="#64748b" font-size="12" font-family="Arial,sans-serif">Scan to verify identity</text>
                    </svg>
                    """.formatted(width, height, width, height, width - 4, height - 4,
                    width - 4, textX, institution, textX, nameY, name, textX, nameY + 42, code,
                    textX, nameY + 96, number, textX, nameY + 130, issue,
                    textX, nameY + 164, expiry, path,
                    landscape ? qrX : width / 2 - 68, landscape ? qrY + qrSize + 28 : qrY + qrSize + 28);
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
                boolean landscape = "LANDSCAPE".equals(card.layout());
                PDRectangle pageSize = landscape ? new PDRectangle(540, 360) : new PDRectangle(360, 540);
                PDPage page = new PDPage(pageSize);
                document.addPage(page);
                BitMatrix matrix = qr(qrTokens.get(i), 256);
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                MatrixToImageWriter.writeToStream(matrix, "PNG", png);
                PDImageXObject image = PDImageXObject.createFromByteArray(document, png.toByteArray(), "identity-qr");
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    float width = pageSize.getWidth(), height = pageSize.getHeight();
                    stream.setNonStrokingColor(255, 255, 255);
                    stream.addRect(0, 0, width, height);
                    stream.fill();
                    stream.setNonStrokingColor(23, 37, 84);
                    stream.addRect(0, height - 76, width, 76);
                    stream.fill();
                    stream.beginText();
                    stream.setNonStrokingColor(255, 255, 255);
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 18);
                    stream.newLineAtOffset(28, height - 45);
                    stream.showText(ascii(card.institutionName()));
                    stream.endText();
                    float qrSize = landscape ? 150 : 170;
                    float qrX = landscape ? width - qrSize - 26 : (width - qrSize) / 2;
                    float qrY = landscape ? 78 : 92;
                    stream.drawImage(image, qrX, qrY, qrSize, qrSize);
                    stream.beginText();
                    stream.setNonStrokingColor(15, 23, 42);
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 20);
                    stream.newLineAtOffset(28, height - 125);
                    stream.showText(ascii(card.fullName()));
                    stream.endText();
                    stream.beginText();
                    stream.setNonStrokingColor(71, 85, 105);
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(28, height - 150);
                    stream.showText(ascii(card.profileCode()));
                    stream.newLineAtOffset(0, -22);
                    stream.showText("Card: " + ascii(card.cardNumber()));
                    stream.newLineAtOffset(0, -18);
                    stream.showText("Issued: " + ascii(card.issueDate()));
                    stream.newLineAtOffset(0, -18);
                    stream.showText("Expires: " + ascii(card.expiryDate() == null ? "No expiry" : card.expiryDate()));
                    stream.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not create the identity-card PDF.", ex);
        }
    }

    private static BitMatrix qr(String value, int size) throws Exception {
        return new MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, size, size,
                java.util.Map.of(EncodeHintType.MARGIN, 1));
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

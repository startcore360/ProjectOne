package com.edu360.api.files;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class FileContentInspector {
    private static final Set<String> EXTENSIONS = Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "jpg", "jpeg", "png", "txt");
    private static final int ZIP_EOCD_SIGNATURE = 0x06054b50;
    private static final int ZIP_CENTRAL_SIGNATURE = 0x02014b50;

    public String inspect(String filename, String declaredType, byte[] bytes) {
        String extension = extension(filename);
        if (!EXTENSIONS.contains(extension) || bytes.length == 0) reject();
        String detected;
        if (startsWith(bytes, new byte[]{0x25, 0x50, 0x44, 0x46})) detected = "pdf";
        else if (startsWith(bytes, new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})) detected = "png";
        else if (startsWith(bytes, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff})) detected = "jpeg";
        else if (startsWith(bytes, new byte[]{(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0, (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1})) {
            detected = switch (extension) { case "doc", "xls", "ppt" -> extension; default -> reject(); };
        } else if (startsWith(bytes, new byte[]{0x50, 0x4b, 0x03, 0x04}) && hasOoxmlContentTypes(bytes)) {
            detected = switch (extension) { case "docx", "xlsx", "pptx" -> extension; default -> reject(); };
        } else if ("txt".equals(extension) && isUtf8Text(bytes) && !looksLikeMarkup(bytes)) detected = "txt";
        else throw new IllegalArgumentException("FILE_TYPE_NOT_ALLOWED: File content is not on the safe list.");

        String expected = mimeFor(detected);
        boolean browserGenericType = declaredType == null || declaredType.isBlank()
                || "application/octet-stream".equalsIgnoreCase(declaredType.trim());
        if (!sameType(extension, detected) || (!browserGenericType && !expected.equalsIgnoreCase(declaredType.trim()))) reject();
        return expected;
    }

    /** Reads ZIP central-directory filenames only; no entry is opened or inflated. */
    private static boolean hasOoxmlContentTypes(byte[] bytes) {
        int eocd = -1;
        int searchStart = Math.max(0, bytes.length - 65_557);
        for (int i = bytes.length - 22; i >= searchStart; i--) {
            if (littleInt(bytes, i) == ZIP_EOCD_SIGNATURE) { eocd = i; break; }
        }
        if (eocd < 0 || eocd + 22 > bytes.length) return false;
        long centralSize = Integer.toUnsignedLong(littleInt(bytes, eocd + 12));
        long centralOffset = Integer.toUnsignedLong(littleInt(bytes, eocd + 16));
        if (centralOffset + centralSize > eocd || centralSize > Integer.MAX_VALUE) return false;
        int end = (int) (centralOffset + centralSize);
        byte[] marker = "[Content_Types].xml".getBytes(StandardCharsets.US_ASCII);
        for (int i = (int) centralOffset; i <= end - 46;) {
            if (i + 46 > end || littleInt(bytes, i) != ZIP_CENTRAL_SIGNATURE) return false;
            int nameLength = littleShort(bytes, i + 28);
            int extraLength = littleShort(bytes, i + 30);
            int commentLength = littleShort(bytes, i + 32);
            int nameStart = i + 46;
            int next = nameStart + nameLength + extraLength + commentLength;
            if (next > end || nameStart + nameLength > end) return false;
            if (nameLength == marker.length) {
                boolean match = true;
                for (int j = 0; j < marker.length; j++) if (bytes[nameStart + j] != marker[j]) { match = false; break; }
                if (match) return true;
            }
            i = next;
        }
        return false;
    }

    private static int littleInt(byte[] b, int i) {
        if (i < 0 || i + 4 > b.length) return -1;
        return (b[i] & 255) | ((b[i + 1] & 255) << 8) | ((b[i + 2] & 255) << 16) | ((b[i + 3] & 255) << 24);
    }
    private static int littleShort(byte[] b, int i) { return (b[i] & 255) | ((b[i + 1] & 255) << 8); }
    private static boolean looksLikeMarkup(byte[] bytes) {
        String text = new String(bytes, 0, Math.min(bytes.length, 512), StandardCharsets.UTF_8).stripLeading().toLowerCase(Locale.ROOT);
        return text.startsWith("<!doctype html") || text.startsWith("<html") || text.startsWith("<svg") || text.startsWith("<script") || text.startsWith("<?xml");
    }
    private static boolean isUtf8Text(byte[] bytes) {
        for (byte value : bytes) if (value == 0) return false;
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException ex) { return false; }
    }
    private static boolean sameType(String extension, String detected) {
        return ("jpg".equals(extension) || "jpeg".equals(extension)) ? "jpeg".equals(detected) : extension.equals(detected);
    }
    private static String mimeFor(String type) {
        return switch (type) {
            case "pdf" -> "application/pdf"; case "jpg", "jpeg" -> "image/jpeg"; case "png" -> "image/png";
            case "doc" -> "application/msword"; case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel"; case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint"; case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "txt" -> "text/plain"; default -> throw new IllegalArgumentException("FILE_TYPE_NOT_ALLOWED: Unsupported file type.");
        };
    }
    private static String extension(String filename) {
        if (filename == null) return "";
        String safe = filename.replace('\\', '/'); int slash = safe.lastIndexOf('/'); int dot = safe.lastIndexOf('.');
        return dot <= slash ? "" : safe.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
    private static boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (value[i] != prefix[i]) return false;
        return true;
    }
    private static String reject() {
        throw new IllegalArgumentException("FILE_TYPE_NOT_ALLOWED: File content, extension and declared content type must match the safe list.");
    }
}

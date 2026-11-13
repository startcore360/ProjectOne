package com.edu360.api.identity;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Institution-selected student/staff identifier format.
 *
 * Supported tokens are {YYYY}, {YY}, {INST}, and exactly one {SEQ:n}
 * where n is between 3 and 10. The academic-session start year is used.
 */
public final class InstitutionCodeFormat {
    private static final Pattern TOKEN = Pattern.compile("\\{(YYYY|YY|INST|SEQ:([0-9]+))\\}");
    private static final Pattern LITERAL = Pattern.compile("[A-Z0-9_-]*");

    private final String source;
    private final int sequenceWidth;

    private InstitutionCodeFormat(String source, int sequenceWidth) {
        this.source = source;
        this.sequenceWidth = sequenceWidth;
    }

    public static InstitutionCodeFormat parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("An ID format is required.");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        Matcher matcher = TOKEN.matcher(normalized);
        int sequenceTokens = 0;
        int cursor = 0;
        int width = -1;
        StringBuilder literal = new StringBuilder();
        while (matcher.find()) {
            literal.append(normalized, cursor, matcher.start());
            String token = matcher.group(1);
            if (token.startsWith("SEQ:")) {
                sequenceTokens++;
                width = Integer.parseInt(matcher.group(2));
                if (width < 3 || width > 10) {
                    throw new IllegalArgumentException("{SEQ:n} must use a width from 3 to 10.");
                }
            }
            cursor = matcher.end();
        }
        literal.append(normalized.substring(cursor));
        if (!LITERAL.matcher(literal).matches()) {
            throw new IllegalArgumentException("Only A-Z, 0-9, underscore and hyphen are allowed outside tokens.");
        }
        // Any brace left behind is an unknown or malformed token.
        if (normalized.indexOf('{') >= 0 || normalized.indexOf('}') >= 0) {
            String withoutTokens = TOKEN.matcher(normalized).replaceAll("");
            if (withoutTokens.indexOf('{') >= 0 || withoutTokens.indexOf('}') >= 0) {
                throw new IllegalArgumentException("The ID format contains an unknown or malformed token.");
            }
        }
        if (sequenceTokens != 1) {
            throw new IllegalArgumentException("Exactly one {SEQ:n} token is required.");
        }
        if (normalized.startsWith("-") || normalized.startsWith("_")
                || normalized.endsWith("-") || normalized.endsWith("_")) {
            throw new IllegalArgumentException("The ID format may not start or end with '-' or '_'.");
        }
        return new InstitutionCodeFormat(normalized, width);
    }

    public String render(String institutionCode, int academicSessionStartYear, long sequence) {
        if (institutionCode == null || institutionCode.isBlank()) {
            throw new IllegalArgumentException("Institution code is required.");
        }
        if (academicSessionStartYear < 1000 || academicSessionStartYear > 9999 || sequence < 1) {
            throw new IllegalArgumentException("A valid session year and positive sequence are required.");
        }
        String result = source.replace("{YYYY}", String.format(Locale.ROOT, "%04d", academicSessionStartYear))
                .replace("{YY}", String.format(Locale.ROOT, "%02d", academicSessionStartYear % 100))
                .replace("{INST}", institutionCode.trim().toUpperCase(Locale.ROOT))
                .replaceAll("\\{SEQ:[0-9]+\\}", String.format(Locale.ROOT, "%0" + sequenceWidth + "d", sequence));
        if (result.length() < 1 || result.length() > 40 || result.startsWith("-") || result.startsWith("_")
                || result.endsWith("-") || result.endsWith("_")) {
            throw new IllegalArgumentException("The generated identifier must be 1 to 40 characters and may not start or end with '-' or '_'.");
        }
        return result;
    }

    public String value() {
        return source;
    }

    public int sequenceWidth() {
        return sequenceWidth;
    }

    /** Literal characters remaining after all supported tokens are treated as wildcards. */
    public String fixedText() {
        return TOKEN.matcher(source).replaceAll("");
    }

    public boolean includesInstitutionToken() {
        return source.contains("{INST}");
    }

    public String fixedPrefix() {
        Matcher matcher = TOKEN.matcher(source);
        return matcher.find() ? source.substring(0, matcher.start()) : source;
    }

    public boolean clashesWith(InstitutionCodeFormat other) {
        String left = fixedPrefix();
        String right = other.fixedPrefix();
        return !left.isBlank() && !right.isBlank()
                && (left.startsWith(right) || right.startsWith(left));
    }
}

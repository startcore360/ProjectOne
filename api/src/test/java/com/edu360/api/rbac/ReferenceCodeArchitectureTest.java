package com.edu360.api.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A-01 (rule R6): every role code and action code written in Java must exist in the reference
 * data the database is seeded from ({@code sql/05_ref_data.sql}: {@code ref.standard_role} and
 * {@code ref.action_catalog}). A code that does not exist there can never match a real row, so a
 * lock list or a permission check built on it silently does nothing (audit finding C7).
 *
 * <p>What is scanned in {@code src/main/java}:
 * <ul>
 *   <li>the action-code argument of every {@code authorize(principal, "CODE", ...)} and
 *       {@code can(principal, "CODE", ...)} call, including a {@code static final String}
 *       constant passed in its place;</li>
 *   <li>every upper-case literal inside {@code Set.of(...)}, {@code Map.of(...)},
 *       {@code Map.ofEntries(...)} and {@code Map.entry(...)} when that collection is a collection
 *       of codes: either it already holds at least one real role or action code, or it is
 *       assigned to a name that says it holds roles or actions. Collections of statuses or event
 *       types (for example {@code Set.of("PLANNED", "ACTIVE", "CLOSED")}) are not codes and are
 *       left alone.</li>
 * </ul>
 */
class ReferenceCodeArchitectureTest {

    private static final Path MAIN = Path.of("src", "main", "java");
    private static final Path REF_DATA = Path.of("..", "sql", "05_ref_data.sql");
    private static final Path SQL_DIR = Path.of("..", "sql");

    // Baseline seed form: one "INSERT INTO ref.action_catalog VALUES ('CODE', ...);" per row.
    private static final Pattern REF_ROW = Pattern.compile(
            "INSERT INTO ref\\.(?:standard_role|action_catalog) VALUES \\('([A-Z][A-Z0-9_]*)'");

    // V4.1 migration form: a single multi-row statement with an explicit column list, e.g.
    // "INSERT INTO ref.action_catalog(action_code,...) VALUES ('CODE1',...), ('CODE2',...) ..."
    // Matches the whole statement up to its closing ';', so the row scan below can pick out
    // every row's leading code, not just the first.
    private static final Pattern REF_INSERT_STATEMENT = Pattern.compile(
            "INSERT INTO ref\\.(?:standard_role|action_catalog)\\s*\\([^)]*\\)\\s*VALUES\\s*(.*?);",
            Pattern.DOTALL);
    private static final Pattern REF_ROW_TUPLE = Pattern.compile("\\(\\s*'([A-Z][A-Z0-9_]*)'");
    private static final Pattern CODE_LITERAL = Pattern.compile("\"([A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+|[A-Z]{3,})\"");
    private static final Pattern COLLECTION_START = Pattern.compile(
            "(?:(\\w+)\\s*=\\s*)?\\b(Set\\.of|Map\\.ofEntries|Map\\.of|Map\\.entry)\\s*\\(");
    private static final Pattern CHECK_CALL = Pattern.compile(
            "\\.(?:authorize|can)\\(\\s*[^,;]+?,\\s*(\"([A-Z][A-Z0-9_]*)\"|([A-Z][A-Z0-9_]*)\\s*[,)])");
    private static final Pattern STRING_CONSTANT = Pattern.compile(
            "static\\s+final\\s+String\\s+([A-Z][A-Z0-9_]*)\\s*=\\s*\"([A-Z][A-Z0-9_]*)\"");
    private static final Pattern CODE_HOLDER_NAME = Pattern.compile("(?i).*(role|action).*");

    // Admission quota-category keys ("GENERAL", "MERIT", "SPORTS", "MANAGEMENT") are business
    // field values, not role/action codes. "MANAGEMENT" happens to collide with the real
    // MANAGEMENT role code, which trips the collection heuristic below for its co-members. Same
    // treatment as the status/event-type collections this test already leaves alone.
    private static final Set<String> QUOTA_CATEGORY_FALSE_POSITIVES = Set.of("GENERAL", "MERIT", "SPORTS");

    @Test
    @DisplayName("REG-A01-1: every role and action code used in Java exists in sql/05_ref_data.sql")
    void regA01_1_everyCodeUsedInJavaExistsInReferenceData() throws IOException {
        Set<String> known = loadReferenceCodes();
        assertThat(known).as("codes parsed from " + REF_DATA).hasSizeGreaterThan(300);

        Map<String, Set<String>> unknown = new TreeMap<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = stripComments(Files.readString(file));
                String where = MAIN.relativize(file).toString().replace('\\', '/');
                for (String code : codesUsedIn(source, known)) {
                    if (!known.contains(code)) {
                        unknown.computeIfAbsent(code, k -> new TreeSet<>()).add(where);
                    }
                }
            }
        }

        assertThat(unknown)
                .as("role/action codes used in Java that do not exist in ref.standard_role or ref.action_catalog")
                .isEmpty();
    }

    static Set<String> loadReferenceCodes() throws IOException {
        Set<String> codes = new HashSet<>();
        addBaselineRows(codes, Files.readString(REF_DATA));

        // V4.1 migrations add role/action codes incrementally (e.g. sql/59_..._indexes.sql adds
        // the M13 appraisal actions); the baseline seed file alone no longer lists every code a
        // later migration's Java callers rely on, so those migrations must be scanned too.
        try (Stream<Path> files = Files.walk(SQL_DIR)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".sql"))
                    .filter(p -> p.getFileName().toString().matches("\\d+_v4\\d_.*\\.sql"))
                    .filter(p -> !p.getFileName().toString().contains("rollback"))
                    .toList()) {
                String source = Files.readString(file);
                addBaselineRows(codes, source);
                Matcher stmt = REF_INSERT_STATEMENT.matcher(source);
                while (stmt.find()) {
                    Matcher row = REF_ROW_TUPLE.matcher(stmt.group(1));
                    while (row.find()) {
                        codes.add(row.group(1));
                    }
                }
            }
        }
        return codes;
    }

    private static void addBaselineRows(Set<String> codes, String source) {
        Matcher m = REF_ROW.matcher(source);
        while (m.find()) {
            codes.add(m.group(1));
        }
    }

    static Set<String> codesUsedIn(String source, Set<String> known) {
        Set<String> found = new TreeSet<>();

        Map<String, String> constants = new HashMap<>();
        Matcher c = STRING_CONSTANT.matcher(source);
        while (c.find()) {
            constants.put(c.group(1), c.group(2));
        }

        Matcher call = CHECK_CALL.matcher(source);
        while (call.find()) {
            if (call.group(2) != null) {
                found.add(call.group(2));
            } else if (constants.containsKey(call.group(3))) {
                found.add(constants.get(call.group(3)));
            }
        }

        Matcher start = COLLECTION_START.matcher(source);
        while (start.find()) {
            String body = balancedArguments(source, start.end());
            List<String> literals = literalsIn(body);
            if (literals.isEmpty()) {
                continue;
            }
            boolean holdsCodes = literals.stream().anyMatch(known::contains)
                    || (start.group(1) != null && CODE_HOLDER_NAME.matcher(start.group(1)).matches());
            if (holdsCodes) {
                literals.stream()
                        .filter(code -> !QUOTA_CATEGORY_FALSE_POSITIVES.contains(code))
                        .forEach(found::add);
            }
        }
        return found;
    }

    private static List<String> literalsIn(String body) {
        Matcher m = CODE_LITERAL.matcher(body);
        List<String> out = new java.util.ArrayList<>();
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static String balancedArguments(String source, int openParenEnd) {
        int depth = 1;
        int i = openParenEnd;
        boolean inString = false;
        while (i < source.length() && depth > 0) {
            char ch = source.charAt(i);
            if (ch == '"' && source.charAt(i - 1) != '\\') {
                inString = !inString;
            } else if (!inString) {
                if (ch == '(') {
                    depth++;
                } else if (ch == ')') {
                    depth--;
                }
            }
            i++;
        }
        return source.substring(openParenEnd, Math.max(openParenEnd, i - 1));
    }

    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//[^\\n\"]*$", "");
    }
}

package com.edu360.api.platformops.dataprotection;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * E5-S3 build-time guard: every app.* table created by sql/01_schema.sql or any numbered migration must be
 * either exported (in exactly one group) or explicitly excluded. A new table fails this test, not a tenant's
 * export at run time.
 */
class ExportManifestDriftTest {

    private static final Pattern APP_TABLE = Pattern.compile(
            "(?im)^\\s*CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?app\\.([a-z0-9_]+)");
    /** Forward migrations: NN_name.sql. Rollbacks (NNr_) and tests (NNt_) never match. */
    private static final Pattern FORWARD_SQL = Pattern.compile("\\d{2}_[A-Za-z0-9_]+\\.sql");

    @Test
    void everyAppTableIsExportedOrExcludedExactlyOnce() throws IOException {
        Set<String> discovered = discoverAppTables();
        assertThat(discovered).hasSizeGreaterThan(150);

        List<String> unclassified = new ArrayList<>();
        List<String> both = new ArrayList<>();
        for (String table : discovered) {
            boolean grouped = ExportManifest.groupedTableNames().contains(table);
            boolean excluded = ExportManifest.isExcludedTable(table);
            if (!grouped && !excluded) unclassified.add(table);
            if (grouped && excluded) both.add(table);
        }
        assertThat(unclassified).as("app.* tables missing from ExportManifest (add to a group or exclude)").isEmpty();
        assertThat(both).as("tables both exported and excluded").isEmpty();
    }

    @Test
    void manifestNamesOnlyRealTablesAndNoDuplicates() throws IOException {
        Set<String> discovered = discoverAppTables();
        List<String> all = ExportManifest.GROUPS.values().stream().flatMap(List::stream).toList();
        assertThat(new HashSet<>(all)).as("a table appears in more than one group").hasSameSizeAs(all);
        assertThat(discovered).as("manifest lists a table that no SQL file creates").containsAll(all);
        assertThat(discovered).containsAll(ExportManifest.EXCLUDED_TABLES.keySet());
        ExportManifest.EXCLUDED_TABLES.values().forEach(reason -> assertThat(reason).isNotBlank());
        ExportManifest.EXCLUDED_COLUMNS.values().forEach(columns ->
                columns.values().forEach(reason -> assertThat(reason).isNotBlank()));
    }

    @Test
    void secretColumnsAreNeverExported() {
        assertThat(ExportManifest.isExcludedColumn("user_account", "password_hash")).isTrue();
        assertThat(ExportManifest.isExcludedColumn("channel_config", "secret_ref")).isTrue();
        assertThat(ExportManifest.isExcludedColumn("file_object", "storage_key")).isTrue();
        assertThat(ExportManifest.isExcludedColumn("setting_value", "value")).isTrue();
        assertThat(ExportManifest.isExcludedColumn("student_profile", "first_name")).isFalse();
    }

    private static Set<String> discoverAppTables() throws IOException {
        Path sql = repositoryRoot().resolve("sql");
        Set<String> tables = new HashSet<>();
        try (Stream<Path> files = Files.list(sql)) {
            for (Path file : files.filter(p -> FORWARD_SQL.matcher(p.getFileName().toString()).matches()).toList()) {
                Matcher matcher = APP_TABLE.matcher(Files.readString(file));
                while (matcher.find()) tables.add(matcher.group(1));
            }
        }
        return tables;
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("sql/01_schema.sql"))) return current;
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate sql/01_schema.sql for the export-manifest drift test.");
    }
}

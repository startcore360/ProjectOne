package com.edu360.api.platformops.dataprotection;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.files.FileStorage;
import com.edu360.api.tenant.Tenant;

/**
 * E5-S3: builds one tenant's export ZIP (CL-09).
 *
 * WHAT: one UTF-8 CSV per populated table under its manifest group folder (real column headers), a
 * data_dictionary.md built from the PostgreSQL table/column comments, and documents/ holding the tenant's
 * clean, active uploaded files plus documents_manifest.csv.
 * HOW: reads through the @Primary tenant-routing DataSource (edu_app) inside a read-only transaction on the
 * default transaction manager. ExportJob sets TenantContext (this tenant, institution scope ALL) BEFORE
 * calling this proxied method, so the borrowed connection carries the tenant GUCs and RLS stays enforced;
 * every query also filters tenant_id explicitly. Rows are fetched in batches and written straight to disk;
 * file bytes are streamed into the ZIP.
 * WHY: no new database role, no BYPASSRLS and no wider grants (approved E5-S3 design). Every cell gets
 * spreadsheet formula-injection protection because schools open these files in Excel.
 * Drafted in the A5 data-protection chat; completed and integrated locally (2026-11-18).
 */
@Component
public class ExportArchiveBuilder {

    private static final int FETCH_SIZE = 1000;

    private static final String DOCUMENT_QUERY = """
            SELECT file_id, institution_id, purpose, original_filename, content_type, size_bytes, sha256, storage_key
              FROM app.file_object
             WHERE tenant_id = ? AND status = 'ACTIVE' AND scan_status = 'CLEAN'
             ORDER BY created_at, file_id
            """;

    private static final String COMMENTS_QUERY = """
            SELECT obj_description(c.oid, 'pg_class') AS table_comment,
                   a.attname AS column_name,
                   col_description(c.oid, a.attnum) AS column_comment
              FROM pg_class c
              JOIN pg_namespace n ON n.oid = c.relnamespace
              JOIN pg_attribute a ON a.attrelid = c.oid
             WHERE n.nspname = 'app' AND c.relname = ?
               AND a.attnum > 0 AND NOT a.attisdropped
             ORDER BY a.attnum
            """;

    private final JdbcTemplate jdbc;
    private final FileStorage fileStorage;

    public ExportArchiveBuilder(DataSource dataSource, FileStorage fileStorage) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.jdbc.setFetchSize(FETCH_SIZE);
        this.fileStorage = fileStorage;
    }

    /** Returns a temporary ZIP file the caller must store and then delete. */
    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public Path build(Tenant tenant) {
        UUID tenantId = tenant.getTenantId();
        if (tenant.getIsolationMode() == null) {
            throw new IllegalStateException("Tenant isolation mode is missing.");
        }
        Path workDir = null;
        Path archive = null;
        boolean completed = false;
        try {
            workDir = Files.createTempDirectory("edu360-export-work-");
            archive = Files.createTempFile("edu360-tenant-export-", ".zip");
            Map<String, List<Path>> populatedGroups = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> group : ExportManifest.GROUPS.entrySet()) {
                Path groupDir = Files.createDirectories(workDir.resolve(group.getKey()));
                List<Path> tableFiles = new ArrayList<>();
                for (String table : group.getValue()) {
                    Path csv = groupDir.resolve(table + ".csv");
                    if (writeTableCsv(csv, table, tenantId) > 0) {
                        tableFiles.add(csv);
                    } else {
                        Files.deleteIfExists(csv);
                    }
                }
                if (!tableFiles.isEmpty()) populatedGroups.put(group.getKey(), tableFiles);
            }

            String dictionary = buildDictionary();
            try (OutputStream output = Files.newOutputStream(archive);
                 ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, List<Path>> group : populatedGroups.entrySet()) {
                    for (Path csv : group.getValue()) {
                        addFile(zip, csv, group.getKey() + "/" + csv.getFileName());
                    }
                }
                addBytes(zip, "data_dictionary.md", dictionary.getBytes(StandardCharsets.UTF_8));
                addDocuments(zip, tenantId);
            }
            completed = true;
            return archive;
        } catch (IOException ex) {
            throw new IllegalStateException("Tenant export archive could not be built.", ex);
        } finally {
            deleteTree(workDir);
            if (!completed) deleteFile(archive);
        }
    }

    private long writeTableCsv(Path csv, String table, UUID tenantId) throws IOException {
        List<String> columns = exportableColumns(table, tenantId);
        if (columns.isEmpty()) return 0;
        String select = "SELECT " + String.join(", ", columns.stream().map(ExportArchiveBuilder::quoteIdentifier).toList())
                + " FROM app." + quoteIdentifier(table) + " WHERE tenant_id = ?";
        try (BufferedWriter writer = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) writer.write(',');
                writer.write(csvCell(columns.get(i)));
            }
            writer.newLine();
            Long rows = jdbc.query(select, ps -> ps.setObject(1, tenantId), rs -> {
                long count = 0;
                while (rs.next()) {
                    try {
                        for (int i = 0; i < columns.size(); i++) {
                            if (i > 0) writer.write(',');
                            writer.write(csvCell(rs.getString(i + 1)));
                        }
                        writer.newLine();
                    } catch (IOException ex) {
                        throw new IllegalStateException("A tenant export CSV row could not be written.", ex);
                    }
                    count++;
                }
                return count;
            });
            return rows == null ? 0 : rows;
        }
    }

    private List<String> exportableColumns(String table, UUID tenantId) {
        return jdbc.query("SELECT * FROM app." + quoteIdentifier(table) + " WHERE tenant_id = ? AND 1 = 0",
                ps -> ps.setObject(1, tenantId), rs -> {
                    ResultSetMetaData metadata = rs.getMetaData();
                    List<String> columns = new ArrayList<>();
                    for (int i = 1; i <= metadata.getColumnCount(); i++) {
                        String name = metadata.getColumnLabel(i);
                        if (!ExportManifest.isExcludedColumn(table, name)) columns.add(name);
                    }
                    return columns;
                });
    }

    private String buildDictionary() {
        StringBuilder markdown = new StringBuilder("# Tenant export data dictionary\n\n")
                .append("Each populated table is a separate UTF-8 CSV inside its group folder; the first row holds ")
                .append("the database column names. Descriptions come from the PostgreSQL comments installed by ")
                .append("`sql/01_schema.sql` and later migrations. Cells that begin with = + - @ tab or carriage ")
                .append("return are prefixed with an apostrophe so spreadsheets do not run them as formulas.\n\n");
        for (Map.Entry<String, List<String>> group : ExportManifest.GROUPS.entrySet()) {
            markdown.append("## ").append(group.getKey()).append("\n\n");
            for (String table : group.getValue()) {
                List<DictionaryColumn> columns = jdbc.query(COMMENTS_QUERY, ps -> ps.setString(1, table),
                        (rs, rowNum) -> new DictionaryColumn(rs.getString("table_comment"),
                                rs.getString("column_name"), rs.getString("column_comment")));
                String tableComment = columns.isEmpty() ? null : columns.get(0).tableComment();
                markdown.append("### `app.").append(table).append("`\n\n")
                        .append(markdownText(tableComment == null ? "No table comment is defined." : tableComment))
                        .append("\n\n| Column | Description |\n|---|---|\n");
                for (DictionaryColumn column : columns) {
                    if (ExportManifest.isExcludedColumn(table, column.columnName())) continue;
                    markdown.append('|').append(markdownText(column.columnName())).append('|')
                            .append(markdownText(column.columnComment() == null
                                    ? "No column comment is defined." : column.columnComment()))
                            .append("|\n");
                }
                markdown.append('\n');
            }
        }
        markdown.append("## Excluded tables\n\n| Table | Reason |\n|---|---|\n");
        ExportManifest.EXCLUDED_TABLES.forEach((table, reason) ->
                markdown.append("|`app.").append(table).append("`|").append(markdownText(reason)).append("|\n"));
        markdown.append("|`app.attendance_entry_yYYYY`|Yearly partitions; their rows are in attendance/attendance_entry.csv.|\n");
        markdown.append("\n## Excluded columns\n\n| Table.column | Reason |\n|---|---|\n");
        ExportManifest.EXCLUDED_COLUMNS.forEach((table, columns) -> columns.forEach((column, reason) ->
                markdown.append("|`app.").append(table).append('.').append(column).append("`|")
                        .append(markdownText(reason)).append("|\n")));
        markdown.append("\nColumns matching the credential/token/secret/MFA/recovery/key/storage-locator denylist are ")
                .append("also omitted.\n");
        return markdown.toString();
    }

    private void addDocuments(ZipOutputStream zip, UUID tenantId) throws IOException {
        List<DocumentFile> documents = jdbc.query(DOCUMENT_QUERY, ps -> ps.setObject(1, tenantId),
                (rs, rowNum) -> new DocumentFile(rs.getObject("file_id", UUID.class),
                        rs.getObject("institution_id", UUID.class), rs.getString("purpose"),
                        rs.getString("original_filename"), rs.getString("content_type"),
                        rs.getLong("size_bytes"), rs.getString("sha256"), rs.getString("storage_key")));

        StringBuilder manifest = new StringBuilder(
                "file_id,institution_id,purpose,original_filename,content_type,size_bytes,sha256,archive_path\n");
        for (DocumentFile document : documents) {
            requireTenantStorageKey(tenantId, document.storageKey());
            String archivePath = "documents/" + document.fileId() + "-" + safeFilename(document.originalFilename());
            manifest.append(csvCell(document.fileId().toString())).append(',')
                    .append(csvCell(document.institutionId() == null ? "" : document.institutionId().toString())).append(',')
                    .append(csvCell(document.purpose())).append(',')
                    .append(csvCell(document.originalFilename())).append(',')
                    .append(csvCell(document.contentType())).append(',')
                    .append(csvCell(Long.toString(document.sizeBytes()))).append(',')
                    .append(csvCell(document.sha256())).append(',')
                    .append(csvCell(archivePath)).append('\n');
            zip.putNextEntry(new ZipEntry(archivePath));
            try (InputStream input = fileStorage.openRead(document.storageKey())) {
                input.transferTo(zip);
            }
            zip.closeEntry();
        }
        addBytes(zip, "documents_manifest.csv", manifest.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void addFile(ZipOutputStream zip, Path file, String entryName) throws IOException {
        zip.putNextEntry(new ZipEntry(entryName));
        try (InputStream input = Files.newInputStream(file)) {
            input.transferTo(zip);
        }
        zip.closeEntry();
    }

    private static void addBytes(ZipOutputStream zip, String entryName, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(entryName));
        zip.write(bytes);
        zip.closeEntry();
    }

    private static String quoteIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("Unsafe SQL identifier in export manifest.");
        }
        return "\"" + identifier + "\"";
    }

    /** Quotes a CSV cell and neutralises spreadsheet formulas (leading = + - @ tab CR get an apostrophe). */
    static String csvCell(String value) {
        String safe = value == null ? "" : value;
        if (!safe.isEmpty() && "=+-@\t\r".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    static void requireTenantStorageKey(UUID tenantId, String storageKey) {
        if (storageKey == null) throw new IllegalStateException("Tenant document has no storage key.");
        Path normalized = Path.of(storageKey.replace('\\', '/')).normalize();
        Path tenantPrefix = Path.of("tenants", tenantId.toString());
        if (normalized.isAbsolute() || !normalized.startsWith(tenantPrefix)) {
            throw new IllegalStateException("Tenant document storage key does not belong to the export tenant.");
        }
    }

    static String safeFilename(String filename) {
        String value = filename == null ? "document" : filename.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._-]", "_");
        if (value.isBlank() || value.equals(".") || value.equals("..")) return "document";
        return value.length() > 180 ? value.substring(value.length() - 180) : value;
    }

    private static String markdownText(String value) {
        return value == null ? "" : value.replace("|", "\\|").replace("\r", " ").replace("\n", " ");
    }

    private static void deleteTree(Path directory) {
        if (directory == null || !Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(ExportArchiveBuilder::deleteFile);
        } catch (IOException ignored) {
            // temporary-file cleanup must not mask the export result
        }
    }

    static void deleteFile(Path file) {
        if (file == null) return;
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // best-effort temporary cleanup
        }
    }

    private record DocumentFile(UUID fileId, UUID institutionId, String purpose, String originalFilename,
                                String contentType, long sizeBytes, String sha256, String storageKey) { }

    private record DictionaryColumn(String tableComment, String columnName, String columnComment) { }
}

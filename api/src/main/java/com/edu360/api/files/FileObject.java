package com.edu360.api.files;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * WHAT: one row of app.file_object - a stored file's metadata (sql/01_schema.sql, 'Stored file;
 * storage key never leaves the server; download only when the scan is CLEAN [TE-11]'). This is
 * the real V4.0 schema's generic file-storage table, already shared by many purposes (its own
 * CHECK constraint lists MESSAGE_ATTACHMENT, STAFF_DOCUMENT, PROFILE_PHOTO, etc.) - this slice
 * only uses the PROFILE_PHOTO purpose (see selfservice/ProfileService), but the entity itself is
 * generic so a later story can reuse it for another purpose without a new table.
 *
 * scanStatus: the schema expects a real virus-scan pipeline ("download only when CLEAN") that
 * does not exist in this codebase. ProfileService marks a freshly uploaded photo CLEAN directly
 * at upload time as a stated, deliberate placeholder - not a hidden shortcut - until a real scan
 * integration exists.
 *
 * ownership: uploaded_by is who owns this file; there is no separate profile table FK (this app
 * has no staff_profile/student_profile entities yet, and building them out just to attach a
 * photo_file_id would be large, unrelated scope) - ProfileService looks up "does this user have
 * an ACTIVE PROFILE_PHOTO file" directly via uploaded_by instead.
 *
 * row_version mapped read-only, same reasoning as RoleDefinition: this slice only needs
 * INSERT + SELECT + a status flip to DELETED on replacement, never a concurrent in-place update.
 */
@Entity
@Table(schema = "app", name = "file_object")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileObject {

    @EmbeddedId
    private FileObjectId id;

    @Column(name = "institution_id")
    private UUID institutionId;

    @Column(nullable = false)
    private String purpose;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private String sha256;

    @Column(name = "scan_status", nullable = false)
    private String scanStatus;

    @Column(nullable = false)
    private String status;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "row_version", insertable = false, updatable = false)
    private Integer rowVersion;
}

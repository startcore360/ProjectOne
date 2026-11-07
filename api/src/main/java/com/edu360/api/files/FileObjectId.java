package com.edu360.api.files;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** WHAT: the composite primary key of app.file_object - (tenant_id, file_id), same tenant-first
 *  composite-key pattern as every other app.* table (see rbac.RoleDefinitionId). */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class FileObjectId implements Serializable {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;
}

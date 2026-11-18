package com.edu360.api.platformops.dataprotection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class ExportArchiveBuilderTest {

    @Test
    void formulaInjectionPrefixesEveryDangerousLeadingCharacter() {
        assertThat(ExportArchiveBuilder.csvCell("=1+1")).isEqualTo("\"'=1+1\"");
        assertThat(ExportArchiveBuilder.csvCell("+SUM(A1:A2)")).isEqualTo("\"'+SUM(A1:A2)\"");
        assertThat(ExportArchiveBuilder.csvCell("-1+2")).isEqualTo("\"'-1+2\"");
        assertThat(ExportArchiveBuilder.csvCell("@SUM(A1)")).isEqualTo("\"'@SUM(A1)\"");
        assertThat(ExportArchiveBuilder.csvCell("\tcmd")).isEqualTo("\"'\tcmd\"");
        assertThat(ExportArchiveBuilder.csvCell("\rcmd")).isEqualTo("\"'\rcmd\"");
        assertThat(ExportArchiveBuilder.csvCell("ordinary")).isEqualTo("\"ordinary\"");
        assertThat(ExportArchiveBuilder.csvCell("a\"b")).isEqualTo("\"a\"\"b\"");
        assertThat(ExportArchiveBuilder.csvCell(null)).isEqualTo("\"\"");
    }

    @Test
    void documentsFromAnotherTenantAreRefused() {
        UUID tenant = UUID.randomUUID();
        ExportArchiveBuilder.requireTenantStorageKey(tenant, "tenants/" + tenant + "/" + UUID.randomUUID());
        assertThatThrownBy(() -> ExportArchiveBuilder.requireTenantStorageKey(tenant,
                "tenants/" + UUID.randomUUID() + "/" + UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ExportArchiveBuilder.requireTenantStorageKey(tenant,
                "tenants/" + tenant + "/../" + UUID.randomUUID() + "/x"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void archiveFilenamesAreSanitised() {
        assertThat(ExportArchiveBuilder.safeFilename("../../etc/passwd")).isEqualTo("passwd");
        assertThat(ExportArchiveBuilder.safeFilename("report card (final).pdf")).isEqualTo("report_card__final_.pdf");
        assertThat(ExportArchiveBuilder.safeFilename(null)).isEqualTo("document");
        assertThat(ExportArchiveBuilder.safeFilename("..")).isEqualTo("document");
    }
}

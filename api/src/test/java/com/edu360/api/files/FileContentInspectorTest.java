package com.edu360.api.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class FileContentInspectorTest {
    private final FileContentInspector inspector = new FileContentInspector();

    @Test
    void acceptsPdfByMagicBytesAndDeclaredType() {
        byte[] pdf = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
        assertThat(inspector.inspect("report.pdf", "application/pdf", pdf)).isEqualTo("application/pdf");
    }

    @Test
    void acceptsValidPdfWhenBrowserUsesGenericOctetStreamType() {
        byte[] pdf = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);
        assertThat(inspector.inspect("report.pdf", "application/octet-stream", pdf)).isEqualTo("application/pdf");
    }

    @Test
    void S2_27_rejectsExecutableRenamedAsPdf() {
        byte[] executable = new byte[]{0x4d, 0x5a, 0, 0, 0, 0};
        assertThatThrownBy(() -> inspector.inspect("report.pdf.exe", "application/x-msdownload", executable))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FILE_TYPE_NOT_ALLOWED");
    }

    @Test
    void rejectsHtmlAndNulContainingText() {
        assertThatThrownBy(() -> inspector.inspect("page.txt", "text/plain", "<html>bad</html>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inspector.inspect("bad.txt", "text/plain", new byte[]{'a', 0, 'b'}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

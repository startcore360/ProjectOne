package com.edu360.api.files;

import java.io.InputStream;

public record StoredFile(InputStream content, String filename, String contentType) implements AutoCloseable {
    @Override
    public void close() throws java.io.IOException {
        content.close();
    }
}

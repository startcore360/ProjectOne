package com.edu360.api.files;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.edu360.api.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * ClamAV clamd INSTREAM adapter. A scanner transport/protocol failure fails closed with 503;
 * it never converts an unavailable scanner into a CLEAN result.
 */
@Component
@ConditionalOnProperty(prefix = "files.scanner.clamav", name = "enabled", havingValue = "true")
public class ClamAvFileScanner implements FileScanner {
    private static final int CHUNK_BYTES = 8192;
    private static final int MAX_RESPONSE_BYTES = 1024;
    private final String host;
    private final int port;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public ClamAvFileScanner(
            @Value("${files.scanner.clamav.host:127.0.0.1}") String host,
            @Value("${files.scanner.clamav.port:3310}") int port,
            @Value("${files.scanner.clamav.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${files.scanner.clamav.read-timeout-ms:10000}") int readTimeoutMs) {
        this.host = host;
        this.port = port;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    @Override
    public ScanResult scan(byte[] content) {
        if (content == null) throw new IllegalArgumentException("File content is required for scanning.");
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            socket.setSoTimeout(readTimeoutMs);
            try (DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                    DataInputStream input = new DataInputStream(socket.getInputStream())) {
                output.write("zINSTREAM\\0".getBytes(StandardCharsets.US_ASCII));
                int offset = 0;
                while (offset < content.length) {
                    int length = Math.min(CHUNK_BYTES, content.length - offset);
                    output.writeInt(length);
                    output.write(content, offset, length);
                    offset += length;
                }
                output.writeInt(0);
                output.flush();

                String response = readResponse(input);
                if (response.endsWith(": OK")) return ScanResult.CLEAN;
                if (response.endsWith(" FOUND")) return ScanResult.INFECTED;
                throw unavailable("ClamAV returned an error response.");
            }
        } catch (IOException | IllegalArgumentException ex) {
            if (ex instanceof ApiException apiException) throw apiException;
            throw unavailable("The malware scanning service is unavailable.", ex);
        }
    }

    private static String readResponse(DataInputStream input) throws IOException {
        byte[] response = new byte[MAX_RESPONSE_BYTES];
        int length = 0;
        while (length < response.length) {
            int value = input.read();
            if (value == -1 || value == 0 || value == '\n') break;
            response[length++] = (byte) value;
        }
        if (length == 0 || length == response.length) {
            throw new IOException("Invalid ClamAV response.");
        }
        return new String(response, 0, length, StandardCharsets.US_ASCII).trim();
    }

    private static ApiException unavailable(String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", message);
    }

    private static ApiException unavailable(String message, Throwable cause) {
        ApiException exception = unavailable(message);
        exception.initCause(cause);
        return exception;
    }
}

package com.edu360.api.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.edu360.api.exception.ApiException;
import org.springframework.http.HttpStatus;

class ClamAvFileScannerTest {

    @Test
    void missingClamAvConfigurationFailsClosedInsteadOfReturningPending() {
        new ApplicationContextRunner()
                .withUserConfiguration(ScannerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(FileScanner.class);
                    FileScanner scanner = context.getBean(FileScanner.class);
                    assertThat(scanner).isInstanceOf(PendingFileScanner.class);
                    assertThatThrownBy(() -> scanner.scan(new byte[] {1, 2, 3}))
                            .isInstanceOf(ApiException.class)
                            .satisfies(error -> {
                                ApiException apiError = (ApiException) error;
                                assertThat(apiError.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                                assertThat(apiError.getErrorCode()).isEqualTo("SERVICE_UNAVAILABLE");
                            });
                });
    }

    @Test
    void explicitlyDisabledClamAvAlsoFailsClosed() {
        new ApplicationContextRunner()
                .withUserConfiguration(ScannerConfiguration.class)
                .withPropertyValues("files.scanner.clamav.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(FileScanner.class);
                    assertThat(context.getBean(FileScanner.class)).isInstanceOf(PendingFileScanner.class);
                    assertThatThrownBy(() -> context.getBean(FileScanner.class).scan(new byte[] {1}))
                            .isInstanceOf(ApiException.class)
                            .hasMessageContaining("Malware scanning is not configured");
                });
    }

    @Test
    void clamAvScannerIsSelectedWhenEnabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(ScannerConfiguration.class)
                .withPropertyValues("files.scanner.clamav.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(FileScanner.class);
                    assertThat(context.getBean(FileScanner.class)).isInstanceOf(ClamAvFileScanner.class);
                });
    }

    @Test
    void sendsNulTerminatedInstreamFramesAndRecognizesCleanResponse() throws Exception {
        byte[] payload = "safe file bytes".getBytes(StandardCharsets.US_ASCII);
        try (ServerSocket server = new ServerSocket(0);
                ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<ReceivedRequest> received = executor.submit(() -> {
                try (Socket client = server.accept();
                        DataInputStream input = new DataInputStream(client.getInputStream());
                        DataOutputStream output = new DataOutputStream(client.getOutputStream())) {
                    ByteArrayOutputStream command = new ByteArrayOutputStream();
                    int nextByte;
                    while ((nextByte = input.read()) != 0) {
                        if (nextByte < 0) throw new java.io.EOFException("INSTREAM command was not NUL terminated.");
                        command.write(nextByte);
                    }

                    int chunkLength = input.readInt();
                    byte[] content = input.readNBytes(chunkLength);
                    int terminalChunkLength = input.readInt();
                    output.write("stream: OK\0".getBytes(StandardCharsets.US_ASCII));
                    output.flush();
                    return new ReceivedRequest(command.toString(StandardCharsets.US_ASCII), content, terminalChunkLength);
                }
            });

            ClamAvFileScanner scanner = new ClamAvFileScanner("127.0.0.1", server.getLocalPort(), 1_000, 1_000);
            assertThat(scanner.scan(payload)).isEqualTo(FileScanner.ScanResult.CLEAN);

            ReceivedRequest request = received.get(2, TimeUnit.SECONDS);
            assertThat(request.command()).isEqualTo("zINSTREAM");
            assertThat(request.content()).containsExactly(payload);
            assertThat(request.terminalChunkLength()).isZero();
        }
    }

    @Test
    void quarantinesInfectedResponse() throws Exception {
        byte[] payload = "infected sample".getBytes(StandardCharsets.US_ASCII);
        try (ServerSocket server = new ServerSocket(0);
                ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> response = executor.submit(() -> {
                try (Socket client = server.accept();
                        DataInputStream input = new DataInputStream(client.getInputStream());
                        DataOutputStream output = new DataOutputStream(client.getOutputStream())) {
                    int next;
                    while ((next = input.read()) != 0) {
                        if (next < 0) throw new java.io.EOFException("INSTREAM command was not NUL terminated.");
                    }
                    int length = input.readInt();
                    input.readNBytes(length);
                    input.readInt();
                    output.write("stream: Eicar-Test-Signature FOUND\0".getBytes(StandardCharsets.US_ASCII));
                    output.flush();
                }
                return null;
            });
            ClamAvFileScanner scanner = new ClamAvFileScanner("127.0.0.1", server.getLocalPort(), 1_000, 1_000);
            assertThat(scanner.scan(payload)).isEqualTo(FileScanner.ScanResult.INFECTED);
            response.get(2, TimeUnit.SECONDS);
        }
    }

    private record ReceivedRequest(String command, byte[] content, int terminalChunkLength) {}

    @Configuration(proxyBeanMethods = false)
    @Import({ ClamAvFileScanner.class, PendingFileScanner.class })
    static class ScannerConfiguration {}
}

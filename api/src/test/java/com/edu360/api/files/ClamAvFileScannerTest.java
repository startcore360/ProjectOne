package com.edu360.api.files;

import static org.assertj.core.api.Assertions.assertThat;

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

class ClamAvFileScannerTest {

    @Test
    void pendingScannerIsTheSafeDefaultWhenClamAvIsNotEnabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(ScannerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(FileScanner.class);
                    assertThat(context.getBean(FileScanner.class)).isInstanceOf(PendingFileScanner.class);
                });
    }

    @Test
    void clamAvScannerReplacesThePendingScannerWhenEnabled() {
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

    private record ReceivedRequest(String command, byte[] content, int terminalChunkLength) {}

    @Configuration(proxyBeanMethods = false)
    @Import({ ClamAvFileScanner.class, PendingFileScanner.class })
    static class ScannerConfiguration {}
}

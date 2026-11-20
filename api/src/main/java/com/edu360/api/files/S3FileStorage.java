package com.edu360.api.files;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Tenant-partitioned S3 object storage. The persisted key is opaque to callers and always has
 * the form tenants/{tenant UUID}/{file UUID}; user-supplied filenames never become object keys.
 * Uses the AWS default credential chain in production and LocalStack's endpoint override in dev.
 */
@Component
@ConditionalOnProperty(prefix = "app.file-storage", name = "provider", havingValue = "s3")
public class S3FileStorage implements FileStorage {

    private static final Pattern KEY_PATTERN = Pattern.compile(
            "^tenants/[0-9a-fA-F-]{36}/[0-9a-fA-F-]{36}$");

    private final S3Client s3;
    private final String bucket;

    public S3FileStorage(
            @Value("${app.file-storage.s3.bucket}") String bucket,
            @Value("${app.file-storage.s3.region:ap-south-1}") String region,
            @Value("${app.file-storage.s3.endpoint:}") String endpoint,
            @Value("${app.file-storage.s3.access-key:}") String accessKey,
            @Value("${app.file-storage.s3.secret-key:}") String secretKey) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("app.file-storage.s3.bucket must be configured when S3 storage is enabled.");
        }
        this.bucket = bucket;
        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(accessKey != null && !accessKey.isBlank()
                        && secretKey != null && !secretKey.isBlank()
                        ? StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
                        : DefaultCredentialsProvider.create());
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(java.net.URI.create(endpoint))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        this.s3 = builder.build();
    }

    @Override
    public String store(UUID tenantId, UUID fileId, InputStream content) {
        if (tenantId == null || fileId == null || content == null) {
            throw new IllegalArgumentException("Tenant, file identifier and content are required.");
        }
        String key = "tenants/" + tenantId + "/" + fileId;
        try {
            byte[] bytes = readFully(content);
            s3.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentLength((long) bytes.length)
                    .build(), RequestBody.fromBytes(bytes));
            return key;
        } catch (IOException ex) {
            throw new S3FileStorageException("Could not read file content for object storage.", ex);
        } catch (RuntimeException ex) {
            throw new S3FileStorageException("Could not store file in object storage.", ex);
        }
    }

    @Override
    public InputStream openRead(String storageKey) {
        String key = validateKey(storageKey);
        try {
            return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (NoSuchKeyException ex) {
            throw new S3FileStorageException("Stored file does not exist.", ex);
        } catch (RuntimeException ex) {
            throw new S3FileStorageException("Could not read file from object storage.", ex);
        }
    }

    @Override
    public byte[] read(String storageKey) {
        try (InputStream input = openRead(storageKey)) {
            return readFully(input);
        } catch (IOException ex) {
            throw new S3FileStorageException("Could not read file from object storage.", ex);
        }
    }

    @Override
    public void delete(String storageKey) {
        String key = validateKey(storageKey);
        try {
            // S3 DeleteObject is idempotent for a missing key.
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (RuntimeException ex) {
            throw new S3FileStorageException("Could not delete file from object storage.", ex);
        }
    }

    private static String validateKey(String storageKey) {
        if (storageKey == null || !KEY_PATTERN.matcher(storageKey).matches()) {
            throw new IllegalArgumentException("Invalid tenant file storage key.");
        }
        return storageKey;
    }

    private static byte[] readFully(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        input.transferTo(output);
        return output.toByteArray();
    }

    public static class S3FileStorageException extends RuntimeException {
        public S3FileStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

package com.edu360.api.files;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.exception.UnauthorizedException;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/v1/tenants/{tenantId}/files", "/api/tenants/{tenantId}/files"})
@RequiredArgsConstructor
public class FileObjectController {
    private final FileService fileService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileUploadResponse> upload(@PathVariable UUID tenantId,
            @RequestParam("file") MultipartFile file, @RequestParam("purpose") String purpose,
            @RequestParam(value = "institutionId", required = false) UUID institutionId,
            Authentication authentication) {
        return ResponseEntity.status(201).body(fileService.upload(principal(authentication, tenantId), file, purpose, institutionId));
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable UUID tenantId, @PathVariable UUID fileId,
            Authentication authentication) {
        StoredFile file = fileService.download(principal(authentication, tenantId), fileId);
        StreamingResponseBody body = output -> {
            try (file) { file.content().transferTo(output); }
            catch (IOException ex) { throw new java.io.UncheckedIOException("File streaming failed.", ex); }
        };
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff").body(body);
    }

    private static TenantPrincipal principal(Authentication authentication, UUID tenantId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new UnauthorizedException("Tenant authentication is required.");
        }
        if (!tenantId.equals(principal.tenantId())) throw new ResourceNotFoundException("File not found.");
        return principal;
    }
}

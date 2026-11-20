package com.edu360.api.files;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.identity.UserType;

@ExtendWith(MockitoExtension.class)
class FileObjectControllerTest {
    @Mock FileService fileService;
    private MockMvc mvc;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final TenantPrincipal principal = new TenantPrincipal(tenantId, userId, UserType.STAFF, "Staff", false);
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(principal, "n/a", List.of());

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FileObjectController(fileService)).build();
    }

    @Test
    void uploadReturnsCreatedMetadataWithoutStorageKey() throws Exception {
        UUID fileId = UUID.randomUUID();
        when(fileService.upload(eq(principal), any(), eq("STUDY_MATERIAL"), eq(null)))
                .thenReturn(new FileUploadResponse(fileId, "lesson.pdf", "application/pdf", 8, "sha", "PENDING", "ACTIVE", null));
        MockMultipartFile upload = new MockMultipartFile("file", "lesson.pdf", "application/pdf", "%PDF-1.7".getBytes());
        mvc.perform(multipart("/v1/tenants/{tenantId}/files", tenantId).file(upload)
                        .param("purpose", "STUDY_MATERIAL").principal(authentication))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileId").value(fileId.toString()))
                .andExpect(jsonPath("$.scanStatus").value("PENDING"))
                .andExpect(jsonPath("$.storageKey").doesNotExist());
    }

    @Test
    void downloadUsesAttachmentAndNosniffHeaders() throws Exception {
        UUID fileId = UUID.randomUUID();
        when(fileService.download(principal, fileId)).thenReturn(
                new StoredFile(new ByteArrayInputStream("safe".getBytes()), "lesson.pdf", MediaType.APPLICATION_PDF_VALUE));
        mvc.perform(get("/v1/tenants/{tenantId}/files/{fileId}/download", tenantId, fileId).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        verify(fileService).download(principal, fileId);
    }
}

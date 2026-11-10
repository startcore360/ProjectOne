package com.edu360.api.imports;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu360.api.audit.AuditEventType;
import com.edu360.api.audit.AuditLogService;
import com.edu360.api.exception.BusinessRuleViolationException;
import com.edu360.api.exception.ResourceNotFoundException;
import com.edu360.api.files.FileObject;
import com.edu360.api.files.FileObjectId;
import com.edu360.api.files.FileObjectRepository;
import com.edu360.api.files.LocalFileStorage;
import com.edu360.api.identity.UserAccountRepository;
import com.edu360.api.identity.UserAccountService;
import com.edu360.api.identity.UserType;
import com.edu360.api.imports.dto.ImportJobResponse;
import com.edu360.api.imports.dto.ImportRowErrorResponse;
import com.edu360.api.imports.dto.ImportTemplateResponse;
import com.edu360.api.imports.dto.StartImportRequest;
import com.edu360.api.imports.dto.UserImportRequest;
import com.edu360.api.rbac.PermissionEvaluatorService;
import com.edu360.api.rbac.RbacPrincipal;
import com.edu360.api.rbac.RbacScope;
import com.edu360.api.rbac.RbacTarget;
import com.edu360.api.rbac.dto.CreateUserRequest;

import lombok.RequiredArgsConstructor;

/**
 * Service managing user bulk imports from CSV files (Story #10 / USER_BULK_UPLOAD):
 * - Enforces Rule MD-27: Whole file checked first; user imports clean file or good rows only.
 * - Enforces DPDP Act 2023 & UIDAI Circular [R2-34]: Masked 4-digit national ID only.
 * - Emits USER_BULK_UPLOADED audit log on completion.
 */
@Service
@RequiredArgsConstructor
public class UserBulkUploadService {

    private static final Logger log = LoggerFactory.getLogger(UserBulkUploadService.class);

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9]{10,15}$");
    private static final Pattern FOUR_DIGIT_PATTERN = Pattern.compile("^\\d{4}$");
    private static final Pattern TWELVE_DIGIT_PATTERN = Pattern.compile("^\\d{12}$");

    private final ImportJobRepository importJobRepository;
    private final ImportRowErrorRepository importRowErrorRepository;
    private final FileObjectRepository fileObjectRepository;
    private final LocalFileStorage localFileStorage;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final UserAccountRepository userAccountRepository;
    private final UserAccountService userAccountService;
    private final AuditLogService auditLogService;

    /**
     * Downloads CSV template for user bulk uploads (Rule MD-26).
     */
    public ImportTemplateResponse getImportTemplate(String kind) {
        String effectiveKind = kind != null ? kind.toUpperCase() : "USERS";
        List<String> headers = List.of("email", "fullName", "userType", "mobile", "nationalIdLast4", "roleCode", "institutionId");
        List<List<String>> sampleRows = List.of(
                List.of("john.doe@northbridge.edu.in", "John Doe", "STAFF", "+919876543210", "4821", "OFFICE_STAFF", ""),
                List.of("priya.sharma@northbridge.edu.in", "Priya Sharma", "STAFF", "+919812345678", "9034", "TEACHER", "")
        );
        String csvTemplate = String.join(",", headers) + "\n" +
                String.join(",", sampleRows.get(0)) + "\n" +
                String.join(",", sampleRows.get(1)) + "\n";

        return new ImportTemplateResponse(
                effectiveKind,
                "user_bulk_upload_template.csv",
                headers,
                sampleRows,
                csvTemplate
        );
    }

    /**
     * Pre-validates the uploaded CSV file and creates a validated ImportJob with row errors (Rule MD-27).
     */
    @Transactional
    public ImportJobResponse createUserImport(RbacPrincipal principal, UUID tenantId, UserImportRequest request) {
        Objects.requireNonNull(principal, "principal is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(request, "request is required");

        if (!principal.tenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Tenant not found or does not match authenticated context");
        }

        RbacScope actingScope = request.institutionId() != null
                ? RbacScope.institution(request.institutionId())
                : RbacScope.tenant();

        permissionEvaluatorService.authorize(
                principal,
                "USER_BULK_UPLOAD",
                actingScope,
                RbacTarget.NONE
        );

        UUID importJobId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();

        byte[] csvBytes = request.csvContent().getBytes(StandardCharsets.UTF_8);
        String storageKey = localFileStorage.store(tenantId, fileId, new ByteArrayInputStream(csvBytes));

        // Save file_object metadata
        FileObject fileObject = FileObject.builder()
                .id(new FileObjectId(tenantId, fileId))
                .institutionId(request.institutionId())
                .purpose("IMPORT_SOURCE")
                .storageKey(storageKey)
                .originalFilename("user_import.csv")
                .contentType("text/csv")
                .sizeBytes(csvBytes.length)
                .sha256("sha256-placeholder")
                .scanStatus("CLEAN")
                .status("ACTIVE")
                .uploadedBy(principal.userAccountId())
                .createdBy(principal.userAccountId())
                .updatedBy(principal.userAccountId())
                .build();
        fileObjectRepository.save(fileObject);

        // Parse and validate rows
        String[] lines = request.csvContent().split("\\r?\\n");
        if (lines.length < 2) {
            throw new BusinessRuleViolationException("EMPTY_IMPORT_FILE", "CSV file must contain a header row and at least one data row");
        }

        String[] headers = parseCsvLine(lines[0]);
        Map<String, Integer> headerMap = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            headerMap.put(headers[i].trim().toLowerCase().replaceAll("[_\\s]+", ""), i);
        }

        List<ImportRowError> errorList = new ArrayList<>();
        Set<String> seenEmailsInFile = new HashSet<>();
        int validRows = 0;

        for (int lineIdx = 1; lineIdx < lines.length; lineIdx++) {
            String line = lines[lineIdx].trim();
            if (line.isEmpty()) {
                continue;
            }

            int rowNumber = lineIdx + 1;
            String[] cells = parseCsvLine(line);

            String email = getCell(cells, headerMap, "email");
            String fullName = getCell(cells, headerMap, "fullname");
            if (fullName == null) fullName = getCell(cells, headerMap, "name");
            String userType = getCell(cells, headerMap, "usertype");
            if (userType == null) userType = getCell(cells, headerMap, "type");
            String mobile = getCell(cells, headerMap, "mobile");
            if (mobile == null) mobile = getCell(cells, headerMap, "phone");
            String nationalIdLast4 = getCell(cells, headerMap, "nationalidlast4");
            if (nationalIdLast4 == null) nationalIdLast4 = getCell(cells, headerMap, "nationalid");
            if (nationalIdLast4 == null) nationalIdLast4 = getCell(cells, headerMap, "aadhaar");

            List<RowValidationError> rowErrors = new ArrayList<>();

            // 1. Email validation
            if (email == null || email.isBlank()) {
                rowErrors.add(new RowValidationError("email", "REQUIRED_FIELD", "Email is required"));
            } else {
                String normalizedEmail = email.toLowerCase().trim();
                if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
                    rowErrors.add(new RowValidationError("email", "INVALID_EMAIL_FORMAT", "Invalid email address format"));
                } else if (!seenEmailsInFile.add(normalizedEmail)) {
                    rowErrors.add(new RowValidationError("email", "DUPLICATE_RESOURCE", "Duplicate email found in CSV file"));
                } else if (userAccountRepository.existsByIdTenantIdAndEmail(tenantId, normalizedEmail)) {
                    rowErrors.add(new RowValidationError("email", "DUPLICATE_RESOURCE", "User with email '" + normalizedEmail + "' already exists in this tenant"));
                }
            }

            // 2. Full name validation
            if (fullName == null || fullName.isBlank()) {
                rowErrors.add(new RowValidationError("fullName", "REQUIRED_FIELD", "Full name is required"));
            } else if (fullName.trim().length() < 2 || fullName.trim().length() > 100) {
                rowErrors.add(new RowValidationError("fullName", "INVALID_FIELD_LENGTH", "Full name must be between 2 and 100 characters"));
            }

            // 3. User type validation
            if (userType == null || userType.isBlank()) {
                rowErrors.add(new RowValidationError("userType", "REQUIRED_FIELD", "User type is required"));
            } else {
                String normalizedType = userType.trim().toUpperCase();
                if ("FACULTY".equals(normalizedType) || "TEACHER".equals(normalizedType)) {
                    // Mapped to STAFF
                } else {
                    try {
                        UserType.valueOf(normalizedType);
                    } catch (IllegalArgumentException ex) {
                        rowErrors.add(new RowValidationError("userType", "INVALID_USER_TYPE", "User type must be STAFF, FACULTY, STUDENT, or GUARDIAN"));
                    }
                }
            }

            // 4. Mobile validation (optional)
            if (mobile != null && !mobile.isBlank()) {
                if (!PHONE_PATTERN.matcher(mobile.trim()).matches()) {
                    rowErrors.add(new RowValidationError("mobile", "INVALID_PHONE_FORMAT", "Mobile number must be a valid 10-15 digit phone number (E.164)"));
                }
            }

            // 5. Statutory UIDAI / DPDP Act 2023 National ID check [R2-34]
            if (nationalIdLast4 != null && !nationalIdLast4.isBlank()) {
                String trimmedId = nationalIdLast4.trim().replaceAll("[\\s-]+", "");
                if (TWELVE_DIGIT_PATTERN.matcher(trimmedId).matches() || trimmedId.length() == 12) {
                    rowErrors.add(new RowValidationError(
                            "nationalIdLast4",
                            "UIDAI_MASKED_LAST4_REQUIRED",
                            "Aadhaar / National ID must be masked 4 digits only per UIDAI and DPDP Act 2023 [R2-34]. Plaintext 12-digit Aadhaar numbers are prohibited."
                    ));
                } else if (!FOUR_DIGIT_PATTERN.matcher(trimmedId).matches()) {
                    rowErrors.add(new RowValidationError(
                            "nationalIdLast4",
                            "INVALID_NATIONAL_ID_FORMAT",
                            "National ID must be exactly the last 4 digits."
                    ));
                }
            }

            if (!rowErrors.isEmpty()) {
                for (RowValidationError error : rowErrors) {
                    String maskedId = nationalIdLast4 != null && nationalIdLast4.length() > 4
                            ? "XXXX-XXXX-" + nationalIdLast4.substring(nationalIdLast4.length() - 4)
                            : nationalIdLast4;

                    String rowDataJson = String.format(
                            "{\"email\":\"%s\",\"fullName\":\"%s\",\"userType\":\"%s\",\"mobile\":\"%s\",\"nationalId\":\"%s\"}",
                            escapeJson(email), escapeJson(fullName), escapeJson(userType), escapeJson(mobile), escapeJson(maskedId)
                    );

                    ImportRowError err = ImportRowError.builder()
                            .id(new ImportRowErrorId(tenantId, UUID.randomUUID()))
                            .importJobId(importJobId)
                            .rowNumber(rowNumber)
                            .fieldName(error.fieldName())
                            .errorCode(error.errorCode())
                            .errorMessage(error.errorMessage())
                            .rowData(rowDataJson)
                            .createdAt(Instant.now())
                            .createdBy(principal.userAccountId())
                            .updatedAt(Instant.now())
                            .updatedBy(principal.userAccountId())
                            .rowVersion(1)
                            .build();

                    errorList.add(err);
                }
            } else {
                validRows++;
            }
        }

        if (!errorList.isEmpty()) {
            importRowErrorRepository.saveAll(errorList);
        }

        ImportJob job = ImportJob.builder()
                .id(new ImportJobId(tenantId, importJobId))
                .institutionId(request.institutionId())
                .importKind(request.importKind() != null ? request.importKind() : "USERS")
                .uploadedBy(principal.userAccountId())
                .sourceFileId(fileId)
                .status("VALIDATED")
                .totalRows(validRows + errorList.size())
                .validRows(validRows)
                .errorRows(errorList.size())
                .createdAt(Instant.now())
                .createdBy(principal.userAccountId())
                .updatedAt(Instant.now())
                .updatedBy(principal.userAccountId())
                .rowVersion(1)
                .build();

        importJobRepository.save(job);

        log.info("Validated import job {} in tenant {}: total={}, valid={}, errors={}",
                importJobId, tenantId, job.getTotalRows(), validRows, errorList.size());

        return ImportJobResponse.from(job);
    }

    /**
     * Retrieves status and counts of an import job.
     */
    @Transactional(readOnly = true)
    public ImportJobResponse getImportJob(RbacPrincipal principal, UUID tenantId, UUID importJobId) {
        verifyTenantAccess(principal, tenantId);

        ImportJob job = importJobRepository.findByIdTenantIdAndIdImportJobId(tenantId, importJobId)
                .orElseThrow(() -> new ResourceNotFoundException("Import job not found: " + importJobId));

        verifyJobAccess(principal, job);

        return ImportJobResponse.from(job);
    }

    /**
     * Lists row-level validation errors for an import job (Rule MD-27).
     */
    @Transactional(readOnly = true)
    public List<ImportRowErrorResponse> listImportErrors(RbacPrincipal principal, UUID tenantId, UUID importJobId) {
        verifyTenantAccess(principal, tenantId);

        ImportJob job = importJobRepository.findByIdTenantIdAndIdImportJobId(tenantId, importJobId)
                .orElseThrow(() -> new ResourceNotFoundException("Import job not found: " + importJobId));

        verifyJobAccess(principal, job);

        return importRowErrorRepository.findByIdTenantIdAndImportJobIdOrderByRowNumberAsc(tenantId, importJobId)
                .stream()
                .map(ImportRowErrorResponse::from)
                .toList();
    }

    private void verifyJobAccess(RbacPrincipal principal, ImportJob job) {
        if (principal != null && principal.userAccountId() != null && principal.userAccountId().equals(job.getUploadedBy())) {
            return;
        }
        RbacScope actingScope = job.getInstitutionId() != null
                ? RbacScope.institution(job.getInstitutionId())
                : RbacScope.tenant();
        permissionEvaluatorService.authorize(principal, "USER_BULK_UPLOAD", actingScope, RbacTarget.NONE);
    }

    /**
     * Starts execution of an import job under chosen mode (CLEAN_ONLY vs GOOD_ROWS_ONLY per Rule MD-27).
     */
    @Transactional
    public ImportJobResponse startImport(RbacPrincipal principal, UUID tenantId, UUID importJobId, StartImportRequest request) {
        verifyTenantAccess(principal, tenantId);

        ImportJob job = importJobRepository.findByIdTenantIdAndIdImportJobId(tenantId, importJobId)
                .orElseThrow(() -> new ResourceNotFoundException("Import job not found: " + importJobId));

        RbacScope actingScope = job.getInstitutionId() != null
                ? RbacScope.institution(job.getInstitutionId())
                : RbacScope.tenant();

        permissionEvaluatorService.authorize(
                principal,
                "USER_BULK_UPLOAD",
                actingScope,
                RbacTarget.NONE
        );

        if ("COMPLETED".equals(job.getStatus())) {
            throw new BusinessRuleViolationException("IMPORT_ALREADY_COMPLETED", "This import job has already completed.");
        }

        if (!"VALIDATED".equals(job.getStatus())) {
            throw new BusinessRuleViolationException("IMPORT_NOT_VALIDATED", "Import job must be in VALIDATED state before starting. Current: " + job.getStatus());
        }

        // Rule MD-27: CLEAN_ONLY mode must have zero errors
        if ("CLEAN_ONLY".equalsIgnoreCase(request.importMode()) && job.getErrorRows() != null && job.getErrorRows() > 0) {
            throw new BusinessRuleViolationException(
                    "IMPORT_HAS_ERRORS",
                    "Cannot start import in CLEAN_ONLY mode when error rows exist [Rule MD-27]. Found " + job.getErrorRows() + " errors."
            );
        }

        job.setImportMode(request.importMode().toUpperCase());
        job.setStatus("IMPORTING");
        job.setStartedAt(Instant.now());

        // Read stored CSV bytes
        FileObject fileObj = fileObjectRepository.findById(new FileObjectId(tenantId, job.getSourceFileId()))
                .orElseThrow(() -> new ResourceNotFoundException("Source file not found for import job: " + job.getSourceFileId()));

        byte[] bytes = localFileStorage.read(fileObj.getStorageKey());
        String csvContent = new String(bytes, StandardCharsets.UTF_8);

        String[] lines = csvContent.split("\\r?\\n");
        String[] headers = parseCsvLine(lines[0]);
        Map<String, Integer> headerMap = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            headerMap.put(headers[i].trim().toLowerCase().replaceAll("[_\\s]+", ""), i);
        }

        Set<Integer> errorRowNumbers = new HashSet<>();
        List<ImportRowError> errors = importRowErrorRepository.findByIdTenantIdAndImportJobIdOrderByRowNumberAsc(tenantId, importJobId);
        for (ImportRowError err : errors) {
            errorRowNumbers.add(err.getRowNumber());
        }

        int importedCount = 0;

        for (int lineIdx = 1; lineIdx < lines.length; lineIdx++) {
            int rowNumber = lineIdx + 1;
            if (errorRowNumbers.contains(rowNumber)) {
                continue; // Skip invalid row in GOOD_ROWS_ONLY mode
            }

            String line = lines[lineIdx].trim();
            if (line.isEmpty()) {
                continue;
            }

            String[] cells = parseCsvLine(line);
            String email = getCell(cells, headerMap, "email");
            String fullName = getCell(cells, headerMap, "fullname");
            if (fullName == null) fullName = getCell(cells, headerMap, "name");
            String userTypeStr = getCell(cells, headerMap, "usertype");
            if (userTypeStr == null) userTypeStr = getCell(cells, headerMap, "type");
            String mobile = getCell(cells, headerMap, "mobile");
            if (mobile == null) mobile = getCell(cells, headerMap, "phone");
            String nationalIdLast4 = getCell(cells, headerMap, "nationalidlast4");
            if (nationalIdLast4 == null) nationalIdLast4 = getCell(cells, headerMap, "nationalid");
            if (nationalIdLast4 == null) nationalIdLast4 = getCell(cells, headerMap, "aadhaar");
            String roleCode = getCell(cells, headerMap, "rolecode");

            UUID instId = job.getInstitutionId();
            String rowInstIdStr = getCell(cells, headerMap, "institutionid");
            if (rowInstIdStr != null && !rowInstIdStr.isBlank()) {
                try {
                    instId = UUID.fromString(rowInstIdStr.trim());
                } catch (IllegalArgumentException ignored) {}
            }

            String normalizedUserType = userTypeStr.trim().toUpperCase();
            UserType userType = ("FACULTY".equals(normalizedUserType) || "TEACHER".equals(normalizedUserType))
                    ? UserType.STAFF
                    : UserType.valueOf(normalizedUserType);

            CreateUserRequest createReq = new CreateUserRequest(
                    userType,
                    fullName.trim(),
                    email.trim(),
                    mobile != null && !mobile.isBlank() ? mobile.trim() : null,
                    nationalIdLast4 != null && !nationalIdLast4.isBlank() ? nationalIdLast4.trim() : null,
                    roleCode != null && !roleCode.isBlank() ? roleCode.trim() : null,
                    instId != null ? com.edu360.api.rbac.ScopeType.INSTITUTION : com.edu360.api.rbac.ScopeType.TENANT,
                    instId,
                    null
            );

            try {
                userAccountService.createUser(principal, createReq);
                importedCount++;
            } catch (Exception ex) {
                log.warn("Failed to provision imported user row {}: {}", rowNumber, ex.getMessage());
            }
        }

        job.setStatus("COMPLETED");
        job.setFinishedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        job.setUpdatedBy(principal.userAccountId());
        importJobRepository.save(job);

        // Audit log
        auditLogService.record(
                AuditEventType.USER_BULK_UPLOADED,
                null,
                principal.userAccountId().toString(),
                null,
                String.format("Imported %d users in mode %s [Rule MD-27]", importedCount, job.getImportMode())
        );

        log.info("Import job {} completed in tenant {}: imported {} users under mode {}",
                importJobId, tenantId, importedCount, job.getImportMode());

        return ImportJobResponse.from(job);
    }

    private void verifyTenantAccess(RbacPrincipal principal, UUID tenantId) {
        if (!principal.tenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Tenant not found or does not match authenticated context");
        }
    }

    private String[] parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString().trim());
        return tokens.toArray(new String[0]);
    }

    private String getCell(String[] cells, Map<String, Integer> headerMap, String key) {
        Integer idx = headerMap.get(key);
        if (idx == null || idx >= cells.length) {
            return null;
        }
        String val = cells[idx].trim();
        return val.isEmpty() ? null : val;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record RowValidationError(String fieldName, String errorCode, String errorMessage) {}
}

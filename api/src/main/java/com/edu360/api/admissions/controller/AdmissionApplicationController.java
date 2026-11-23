package com.edu360.api.admissions.controller;

import com.edu360.api.admissions.service.AdmissionService;
import com.edu360.api.admissions.service.AdmissionEnrollmentService;
import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.files.FileService;
import com.edu360.api.files.FileUploadResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/tenants/{tenantId}/admissions","/v1/tenants/{tenantId}/admissions"})
public class AdmissionApplicationController {
    private final AdmissionService service;
    private final AdmissionEnrollmentService enrollment;
    private final FileService files;
    public AdmissionApplicationController(AdmissionService service,AdmissionEnrollmentService enrollment,FileService files){this.service=service;this.enrollment=enrollment;this.files=files;}
    @PostMapping("/cycles/{cycleId}/applications")
    public ResponseEntity<Map<String,Object>> submit(@PathVariable UUID tenantId,@PathVariable UUID cycleId,@RequestBody Map<String,Object> body,Authentication auth){
        return ResponseEntity.status(HttpStatus.CREATED).body(service.submitApplication(AdmissionCycleController.actor(auth,tenantId),cycleId,body));
    }
    @PostMapping(value="/applications/{applicationId}/documents",consumes="multipart/form-data")
    public FileUploadResponse upload(@PathVariable UUID tenantId,@PathVariable UUID applicationId,@RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue="SUPPORTING_DOCUMENT") String documentType,Authentication auth){
        TenantPrincipal actor=AdmissionCycleController.actor(auth,tenantId);
        if(file==null||file.isEmpty()||file.getSize()>5L*1024*1024)
            throw new com.edu360.api.exception.BusinessRuleViolationException("ADMISSION_DOCUMENT_SIZE_INVALID","Admission documents must be non-empty and no larger than 5 MB.");
        String mime=file.getContentType();
        if(mime==null||!List.of("application/pdf","image/jpeg","image/png").contains(mime.toLowerCase()))
            throw new com.edu360.api.exception.BusinessRuleViolationException("ADMISSION_DOCUMENT_TYPE_INVALID","Only PDF, JPG and PNG admission documents are accepted.");
        if("PHOTO".equalsIgnoreCase(documentType)&&!"image/jpeg".equalsIgnoreCase(mime)&&!"image/png".equalsIgnoreCase(mime))
            throw new com.edu360.api.exception.BusinessRuleViolationException("ADMISSION_PHOTO_TYPE_INVALID","Applicant photos must be JPG or PNG.");
        UUID institutionId=service.institutionForApplicant(actor,applicationId);
        FileUploadResponse uploaded=files.upload(actor,file,"ADMISSION_DOCUMENT",institutionId);
        service.attachDocument(actor,applicationId,uploaded.fileId(),documentType);
        return uploaded;
    }
    @GetMapping("/applications/mine")
    public List<Map<String,Object>> mine(@PathVariable UUID tenantId,Authentication auth){
        return service.myApplications(AdmissionCycleController.actor(auth,tenantId));
    }
    @GetMapping("/scrutiny")
    public List<Map<String,Object>> scrutiny(@PathVariable UUID tenantId,@RequestParam UUID institutionId,Authentication auth){
        return service.scrutinyQueue(AdmissionCycleController.actor(auth,tenantId),institutionId);
    }
    @PostMapping("/applications/{applicationId}/scrutiny")
    public Map<String,Object> scrutinize(@PathVariable UUID tenantId,@PathVariable UUID applicationId,@RequestBody Map<String,String> body,Authentication auth){
        return service.scrutinize(AdmissionCycleController.actor(auth,tenantId),applicationId,body.get("decision"),body.get("defectNotes"));
    }
    @GetMapping("/applications/{applicationId}")
    public Map<String,Object> getApplication(@PathVariable UUID tenantId,@PathVariable UUID applicationId,Authentication auth){
        return service.applicationForApplicant(AdmissionCycleController.actor(auth,tenantId),applicationId);
    }
    @GetMapping("/applications/{applicationId}/seat-allotments")
    public List<Map<String,Object>> applicantSeatAllotments(@PathVariable UUID tenantId,@PathVariable UUID applicationId,Authentication auth){
        return service.seatAllotmentsForApplicant(AdmissionCycleController.actor(auth,tenantId),applicationId);
    }
    @PostMapping("/applications/{applicationId}/corrections/resubmit")
    public Map<String,Object> resubmitCorrections(@PathVariable UUID tenantId,@PathVariable UUID applicationId,Authentication auth){
        return service.resubmitCorrections(AdmissionCycleController.actor(auth,tenantId),applicationId);
    }
    @PostMapping("/applications/{applicationId}/application-fee-confirmation")
    public Map<String,Object> confirmApplicationFee(@PathVariable UUID tenantId,@PathVariable UUID applicationId,
            @RequestBody(required=false) Map<String,String> body,Authentication auth){
        return service.confirmApplicationFee(AdmissionCycleController.actor(auth,tenantId),applicationId,body==null?null:body.get("reference"));
    }
    @PostMapping("/applications/{applicationId}/admission-fee-confirmation")
    public Map<String,Object> confirmFee(@PathVariable UUID tenantId,@PathVariable UUID applicationId,
            @RequestBody(required=false) Map<String,String> body,Authentication auth){
        return service.confirmAdmissionFee(AdmissionCycleController.actor(auth,tenantId),applicationId,body==null?null:body.get("reference"));
    }
    @PostMapping("/applications/{applicationId}/conversion")
    public Object convert(@PathVariable UUID tenantId,@PathVariable UUID applicationId,Authentication auth){
        return enrollment.convertToStudent(AdmissionCycleController.actor(auth,tenantId),applicationId);
    }
}

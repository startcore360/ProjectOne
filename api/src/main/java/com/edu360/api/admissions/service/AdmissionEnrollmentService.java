package com.edu360.api.admissions.service;

import com.edu360.api.config.TenantPrincipal;
import com.edu360.api.identity.StudentProfileResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Conversion adapter. Profile creation is separate from section enrollment by design. */
@Service
public class AdmissionEnrollmentService {
    private final AdmissionService admissions;
    public AdmissionEnrollmentService(AdmissionService admissions) { this.admissions=admissions; }
    @Transactional
    public StudentProfileResponse convertToStudent(TenantPrincipal actor, UUID applicationId) {
        return admissions.convertToStudent(actor,applicationId);
    }
}

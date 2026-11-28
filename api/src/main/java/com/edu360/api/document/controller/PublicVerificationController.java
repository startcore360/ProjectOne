package com.edu360.api.document.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.edu360.api.document.service.CertificateService;
import com.edu360.api.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/public/certificates")
@RequiredArgsConstructor
public class PublicVerificationController {
    private final CertificateService certificates;

    @GetMapping("/verify/{code}")
    public ResponseEntity<CertificateService.PublicVerification> verify(@PathVariable String code) {
        CertificateService.PublicVerification result = certificates.verifyPublic(code);
        if (result == null) throw new ResourceNotFoundException("Certificate not found.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
}

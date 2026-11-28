package com.edu360.api.auth;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.exception.ApiException;

/** Mobile OTP login is intentionally unavailable until an SMS provider and Keycloak exchange exist. */
@RestController
@RequestMapping({"/api/auth/otp", "/v1/auth/otp"})
public class OtpLoginController {

    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> status() {
        // DEC-24: do not advertise an option until the provider and verified sign-in path exist.
        return ResponseEntity.ok(Map.of("enabled", false));
    }

    @PostMapping("/request")
    public ResponseEntity<Void> request(@RequestBody(required = false) Map<String, String> request) {
        throw notConfigured();
    }

    @PostMapping("/verify")
    public ResponseEntity<Void> verify(@RequestBody(required = false) Map<String, String> request) {
        throw notConfigured();
    }

    private ApiException notConfigured() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "OTP_PROVIDER_NOT_CONFIGURED",
                "Mobile one-time-code sign-in is not configured.");
    }
}

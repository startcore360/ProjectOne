package com.edu360.api.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu360.api.auth.dto.AuthResponse;
import com.edu360.api.auth.dto.AuthUserResponse;
import com.edu360.api.auth.dto.LoginRequest;
import com.edu360.api.auth.dto.RegisterRequest;
import com.edu360.api.config.JwtUtil;
import com.edu360.api.user.User;
import com.edu360.api.user.UserRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "HttpOnly-cookie authentication endpoints")
public class AuthController {
    private static final String COOKIE_NAME = "EDU360_SESSION";
    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Value("${app.auth.cookie-secure:false}")
    private boolean secureCookie;

    private ResponseCookie cookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from(COOKIE_NAME, token).httpOnly(true).secure(secureCookie)
                .sameSite("Lax").path("/api").maxAge(maxAgeSeconds).build();
    }

    private ResponseEntity<AuthUserResponse> createSession(AuthResponse auth, HttpStatus status) {
        User user = userRepository.findById(auth.getUserId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user was not found"));
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie(auth.getToken(), jwtUtil.getExpirationSeconds()).toString())
                .body(AuthUserResponse.from(user));
    }

    @PostMapping("/register")
    @Operation(summary = "Register and create a session")
    public ResponseEntity<AuthUserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return createSession(authService.register(request), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    @Operation(summary = "Login and create a session")
    public ResponseEntity<AuthUserResponse> login(@Valid @RequestBody LoginRequest request) {
        return createSession(authService.login(request), HttpStatus.OK);
    }

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user")
    public ResponseEntity<AuthUserResponse> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(AuthUserResponse.from(user));
    }

    @PostMapping("/logout")
    @Operation(summary = "Clear the session cookie")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", 0).toString()).build();
    }
}

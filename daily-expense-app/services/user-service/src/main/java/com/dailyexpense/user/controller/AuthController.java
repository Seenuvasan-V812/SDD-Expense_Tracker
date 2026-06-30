package com.dailyexpense.user.controller;

import com.dailyexpense.user.domain.User;
import com.dailyexpense.user.dto.AuthTokenResponse;
import com.dailyexpense.user.dto.ForgotPasswordRequest;
import com.dailyexpense.user.dto.LoginRequest;
import com.dailyexpense.user.dto.LogoutRequest;
import com.dailyexpense.user.dto.RefreshRequest;
import com.dailyexpense.user.dto.RegisterRequest;
import com.dailyexpense.user.dto.ResetPasswordRequest;
import com.dailyexpense.user.dto.VerifyByEmailRequest;
import com.dailyexpense.user.service.AccountLifecycleService;
import com.dailyexpense.user.service.AuthenticationService;
import com.dailyexpense.user.service.EmailVerificationService;
import com.dailyexpense.user.service.RegistrationService;
import com.dailyexpense.user.service.TokenRotationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final EmailVerificationService emailVerificationService;
    private final AuthenticationService authenticationService;
    private final TokenRotationService tokenRotationService;
    private final AccountLifecycleService accountLifecycleService;

    public AuthController(RegistrationService registrationService,
                          EmailVerificationService emailVerificationService,
                          AuthenticationService authenticationService,
                          TokenRotationService tokenRotationService,
                          AccountLifecycleService accountLifecycleService) {
        this.registrationService = registrationService;
        this.emailVerificationService = emailVerificationService;
        this.authenticationService = authenticationService;
        this.tokenRotationService = tokenRotationService;
        this.accountLifecycleService = accountLifecycleService;
    }

    // T026 — POST /api/v1/auth/register → 201 + Location; dup → 409
    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        User user = registrationService.register(request.fullName(), request.email(), request.password());
        emailVerificationService.createVerification(user);
        URI location = URI.create("/api/v1/users/" + user.getId());
        return ResponseEntity.created(location).build();
    }

    // T026 — GET /api/v1/auth/verify-email?token=… → 200 activates user
    @GetMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        emailVerificationService.verifyEmail(token);
        return ResponseEntity.ok().build();
    }

    // Phase-1: POST /api/v1/auth/verify-email/by-email — activate by email when token delivery unavailable
    @PostMapping("/verify-email/by-email")
    public ResponseEntity<Void> verifyEmailByEmail(@Valid @RequestBody VerifyByEmailRequest req) {
        emailVerificationService.verifyByEmail(req.email());
        return ResponseEntity.ok().build();
    }

    // T027 — POST /api/v1/auth/login → 200 AuthTokenResponse; wrong/unverified → 401
    @PostMapping("/login")
    public ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthTokenResponse response = authenticationService.login(request.email(), request.password());
        return ResponseEntity.ok(response);
    }

    // T029 — POST /api/v1/auth/refresh → 200 new pair; reused revoked token → 401
    @PostMapping("/refresh")
    public ResponseEntity<AuthTokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthTokenResponse response = tokenRotationService.rotate(request.refreshToken());
        return ResponseEntity.ok(response);
    }

    // T030 — POST /api/v1/auth/logout → 204; session refresh token revoked
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        tokenRotationService.revokeToken(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    // T031 — POST /api/v1/auth/forgot-password → 202 uniform (no enumeration)
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        accountLifecycleService.forgotPassword(request.email());
        return ResponseEntity.accepted().build(); // always 202 — no enumeration leak
    }

    // T031 — POST /api/v1/auth/reset-password → 204; all refresh revoked; reuse → 400
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        accountLifecycleService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}

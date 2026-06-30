package com.dailyexpense.user.controller;

import com.dailyexpense.shared.security.CallerContext;
import com.dailyexpense.user.domain.User;
import com.dailyexpense.user.dto.ChangePasswordRequest;
import com.dailyexpense.user.dto.DataExportResponse;
import com.dailyexpense.user.dto.UpdateProfileRequest;
import com.dailyexpense.user.dto.UserResponse;
import com.dailyexpense.user.service.AccountLifecycleService;
import com.dailyexpense.user.service.DataExportService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final AccountLifecycleService accountLifecycleService;
    private final DataExportService dataExportService;

    public UserController(AccountLifecycleService accountLifecycleService,
                          DataExportService dataExportService) {
        this.accountLifecycleService = accountLifecycleService;
        this.dataExportService = dataExportService;
    }

    // T032 — GET /api/v1/users/me: profile without passwordHash (AL-4)
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getProfile(@AuthenticationPrincipal CallerContext caller) {
        User user = accountLifecycleService.findOwnedUser(caller.userId());
        return ResponseEntity.ok(toResponse(user));
    }

    // T032 — PUT /api/v1/users/me: body userId IGNORED — identity from JWT (AL-5)
    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(
            @AuthenticationPrincipal CallerContext caller,
            @Valid @RequestBody UpdateProfileRequest request) {
        User saved = accountLifecycleService.updateProfile(
                caller.userId(), request.fullName(), request.timezone(),
                request.locale(), request.weeklyDigestEnabled());
        return ResponseEntity.ok(toResponse(saved));
    }

    // T033 — PATCH /api/v1/users/me/password
    @PatchMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal CallerContext caller,
            @Valid @RequestBody ChangePasswordRequest request) {
        accountLifecycleService.changePassword(
                caller.userId(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    // T034 — DELETE /api/v1/users/me: status DELETED + UserDeletedEvent in same tx
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal CallerContext caller) {
        accountLifecycleService.deleteAccount(caller.userId());
        return ResponseEntity.noContent().build();
    }

    // T035 — POST /api/v1/users/me/data-export → 202
    @PostMapping("/me/data-export")
    public ResponseEntity<DataExportResponse> requestDataExport(
            @AuthenticationPrincipal CallerContext caller) {
        var export = dataExportService.requestExport(caller.userId());
        return ResponseEntity.accepted()
                .header("Location", "/api/v1/users/me/data-export/" + export.getId())
                .body(new DataExportResponse(export.getId(), export.getStatus().name()));
    }

    // T035 — GET /api/v1/users/me/data-export/{id}/download: owner→200, foreign→403
    @GetMapping("/me/data-export/{id}/download")
    public ResponseEntity<String> downloadExport(
            @AuthenticationPrincipal CallerContext caller,
            @PathVariable UUID id) {
        String url = dataExportService.getDownloadUrl(caller.userId(), id); // download_ref never logged
        return ResponseEntity.ok(url);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getStatus().name(),
                user.getPreferredCurrency(),
                user.getTimezone(),
                user.getLocale(),
                user.isWeeklyDigestEnabled(),
                user.getCreatedAt()
        );
    }
}

package com.dailyexpense.user.service;

import com.dailyexpense.shared.exception.ForbiddenOwnershipException;
import com.dailyexpense.shared.outbox.EventEnvelope;
import com.dailyexpense.shared.outbox.OutboxPublisher;
import com.dailyexpense.user.domain.PasswordResetToken;
import com.dailyexpense.user.domain.RefreshToken;
import com.dailyexpense.user.domain.User;
import com.dailyexpense.user.domain.UserStatus;
import com.dailyexpense.user.repository.PasswordResetTokenRepository;
import com.dailyexpense.user.repository.RefreshTokenRepository;
import com.dailyexpense.user.repository.UserRepository;
import com.dailyexpense.user.util.TokenHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class AccountLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(AccountLifecycleService.class);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final OutboxPublisher outboxPublisher;

    public AccountLifecycleService(UserRepository userRepository,
                                   PasswordResetTokenRepository resetTokenRepository,
                                   RefreshTokenRepository refreshTokenRepository,
                                   PasswordEncoder passwordEncoder,
                                   OutboxPublisher outboxPublisher) {
        this.userRepository = userRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.outboxPublisher = outboxPublisher;
    }

    /**
     * T031 — Forgot-password: always returns (no enumeration).
     * If user exists and is ACTIVE: writes token + PasswordResetRequestedEvent to outbox in SAME tx.
     */
    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.getStatus() == UserStatus.ACTIVE) {
                String rawToken = TokenHasher.generateSecureToken();
                String tokenHash = TokenHasher.sha256(rawToken);

                PasswordResetToken resetToken = new PasswordResetToken();
                resetToken.setId(UUID.randomUUID());
                resetToken.setUserId(user.getId());
                resetToken.setTokenHash(tokenHash);
                resetToken.setExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
                resetToken.setCreatedAt(Instant.now());
                resetTokenRepository.save(resetToken);

                // Event in SAME transaction — rawToken is NEVER logged
                outboxPublisher.publish(EventEnvelope.builder()
                        .eventType("PasswordResetRequestedEvent")
                        .producer("user-service")
                        .userId(user.getId())
                        .traceId(MDC.get("traceId"))
                        .payload(String.format("{\"userId\":\"%s\"}", user.getId()))
                        .build());

                log.info("Password reset token created for userId={}", user.getId());
            }
        });
        // Always returns — no enumeration leak (REQ-USR-007)
    }

    /**
     * T031 — Reset password with raw token; reuse → 400; success revokes ALL refresh tokens.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        String hash = TokenHasher.sha256(rawToken);
        PasswordResetToken token = resetTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired token"));

        if (token.getConsumedAt() != null) {
            throw new IllegalArgumentException("Token has already been used");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Token has expired");
        }

        token.setConsumedAt(Instant.now());
        resetTokenRepository.save(token);

        User user = userRepository.findById(token.getUserId()).orElseThrow(ForbiddenOwnershipException::new);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        revokeAllRefreshTokens(token.getUserId());
    }

    /**
     * T033 — Change password: verify current; re-hash; revoke all refresh tokens.
     */
    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId).orElseThrow(ForbiddenOwnershipException::new);

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        revokeAllRefreshTokens(userId);
    }

    /**
     * T034 — Delete account: status DELETED + UserDeletedEvent in SAME tx; all refresh revoked.
     * 403-never-404 (INV-1/SEC-3).
     */
    @Transactional
    public void deleteAccount(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(ForbiddenOwnershipException::new);

        user.setStatus(UserStatus.DELETED);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        revokeAllRefreshTokens(userId);

        // UserDeletedEvent in SAME transaction — rollback leaves both absent (T034 AC)
        outboxPublisher.publish(EventEnvelope.builder()
                .eventType("UserDeletedEvent")
                .producer("user-service")
                .userId(userId)
                .traceId(MDC.get("traceId"))
                .payload(String.format("{\"userId\":\"%s\"}", userId))
                .build());

        log.info("Account deleted userId={}", userId);
    }

    /** T032 — Returns user owned by userId; 403 (not 404) when not found (INV-1). */
    @Transactional(readOnly = true)
    public User findOwnedUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(ForbiddenOwnershipException::new);
    }

    /** T032 — Update profile fields; 403 (not 404) when not found (INV-1). */
    @Transactional
    public User updateProfile(UUID userId, String fullName, String timezone, String locale,
                               boolean weeklyDigestEnabled) {
        User user = userRepository.findById(userId).orElseThrow(ForbiddenOwnershipException::new);
        user.setFullName(fullName);
        if (timezone != null) user.setTimezone(timezone);
        if (locale != null)   user.setLocale(locale);
        user.setWeeklyDigestEnabled(weeklyDigestEnabled);
        user.setUpdatedAt(Instant.now());
        return userRepository.save(user);
    }

    private void revokeAllRefreshTokens(UUID userId) {
        List<RefreshToken> tokens = refreshTokenRepository.findAllByUserId(userId);
        Instant now = Instant.now();
        tokens.forEach(t -> {
            if (t.getRevokedAt() == null) {
                t.setRevokedAt(now);
            }
        });
        refreshTokenRepository.saveAll(tokens);
    }
}

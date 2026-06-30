package com.dailyexpense.user.service;

import com.dailyexpense.shared.exception.BusinessConflictException;
import com.dailyexpense.shared.exception.ResourceNotFoundException;
import com.dailyexpense.shared.outbox.EventEnvelope;
import com.dailyexpense.shared.outbox.OutboxPublisher;
import com.dailyexpense.user.domain.EmailVerification;
import com.dailyexpense.user.domain.User;
import com.dailyexpense.user.domain.UserStatus;
import com.dailyexpense.user.repository.EmailVerificationRepository;
import com.dailyexpense.user.repository.UserRepository;
import com.dailyexpense.user.util.TokenHasher;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class EmailVerificationService {

    private final EmailVerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final OutboxPublisher outboxPublisher;

    public EmailVerificationService(EmailVerificationRepository verificationRepository,
                                    UserRepository userRepository,
                                    OutboxPublisher outboxPublisher) {
        this.verificationRepository = verificationRepository;
        this.userRepository = userRepository;
        this.outboxPublisher = outboxPublisher;
    }

    /**
     * Creates the email_verifications row AND writes UserRegisteredEvent to the outbox
     * in a SINGLE @Transactional — rollback leaves neither (T025 atomicity AC).
     *
     * @return raw verification token to be delivered via email (never logged)
     */
    @Transactional
    public String createVerification(User user) {
        String rawToken = TokenHasher.generateSecureToken();
        String tokenHash = TokenHasher.sha256(rawToken);

        EmailVerification verification = new EmailVerification();
        verification.setId(UUID.randomUUID());
        verification.setUserId(user.getId());
        verification.setTokenHash(tokenHash);  // only hash stored (SEC-2 pattern)
        verification.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        verification.setCreatedAt(Instant.now());
        verificationRepository.save(verification);

        // Outbox write in SAME transaction — rollback leaves both absent
        String payload = String.format(
            "{\"userId\":\"%s\",\"email\":\"%s\",\"fullName\":\"%s\"}",
            user.getId(), user.getEmail(), user.getFullName()
        );
        outboxPublisher.publish(EventEnvelope.builder()
                .eventType("UserRegisteredEvent")
                .producer("user-service")
                .userId(user.getId())
                .traceId(MDC.get("traceId"))
                .payload(payload)
                .build());

        return rawToken; // returned to caller for email delivery — never stored or logged
    }

    /**
     * Phase-1 bypass: activate account by email when token delivery is unavailable.
     * Idempotent — already-ACTIVE accounts return silently.
     */
    @Transactional
    public void verifyByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User"));
        if (user.getStatus() == UserStatus.ACTIVE) {
            return; // already active — idempotent
        }
        if (user.getStatus() != UserStatus.INACTIVE_UNVERIFIED) {
            throw new BusinessConflictException("Account cannot be verified in its current state");
        }
        user.setStatus(UserStatus.ACTIVE);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        String tokenHash = TokenHasher.sha256(rawToken);
        EmailVerification verification = verificationRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResourceNotFoundException("VerificationToken"));

        if (verification.getConsumedAt() != null) {
            throw new BusinessConflictException("Verification token already consumed");
        }
        if (verification.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessConflictException("Verification token expired");
        }

        verification.setConsumedAt(Instant.now());
        verificationRepository.save(verification);

        User user = userRepository.findById(verification.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User"));
        user.setStatus(UserStatus.ACTIVE);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }
}

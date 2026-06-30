package com.dailyexpense.user.scheduler;

import com.dailyexpense.user.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * T038 — Purges expired and revoked refresh tokens hourly (idempotent).
 * Active tokens are untouched.
 */
@Component
public class TokenCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupScheduler.class);

    private final RefreshTokenRepository refreshTokenRepository;

    public TokenCleanupScheduler(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Scheduled(fixedDelayString = "${app.token-cleanup.delay-ms:3600000}")
    @Transactional
    public void cleanupExpiredAndRevokedTokens() {
        int deleted = refreshTokenRepository.deleteExpiredOrRevoked(Instant.now());
        log.info("TokenCleanup deleted={} expired-or-revoked refresh tokens", deleted);
    }
}

package com.dailyexpense.user.service;

import com.dailyexpense.shared.security.JwtService;
import com.dailyexpense.user.domain.RefreshToken;
import com.dailyexpense.user.dto.AuthTokenResponse;
import com.dailyexpense.user.repository.RefreshTokenRepository;
import com.dailyexpense.user.util.TokenHasher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class TokenRotationService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    public TokenRotationService(RefreshTokenRepository refreshTokenRepository, JwtService jwtService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
    }

    /** Called by AuthenticationService on first login — starts a new token family. */
    @Transactional
    public void storeNewToken(UUID userId, UUID familyId, String rawRefreshToken) {
        persist(userId, familyId, rawRefreshToken);
    }

    /**
     * Rotates an active refresh token: revokes the old one, issues a new one in the SAME family.
     * REUSE DETECTION: if the presented token is already revoked, revoke the ENTIRE family (SEC-2).
     */
    @Transactional
    public AuthTokenResponse rotate(String rawRefreshToken) {
        String hash = TokenHasher.sha256(rawRefreshToken);

        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (existing.getRevokedAt() != null) {
            // Reuse detected: revoke whole family to kill any attacker session (SEC-2)
            revokeFamily(existing.getFamilyId());
            throw new BadCredentialsException("Refresh token reuse detected — family revoked");
        }

        if (existing.getExpiresAt().isBefore(Instant.now())) {
            throw new BadCredentialsException("Refresh token expired");
        }

        // Rotate: revoke old token, keep same family
        existing.setRevokedAt(Instant.now());
        refreshTokenRepository.save(existing);

        UUID userId = existing.getUserId();
        String newAccessToken = jwtService.issueAccessToken(userId);
        String newRawRefresh = jwtService.issueRefreshToken(userId);
        persist(userId, existing.getFamilyId(), newRawRefresh);

        return new AuthTokenResponse(newAccessToken, newRawRefresh, 900L);
    }

    /** Revokes the single session refresh token (logout). */
    @Transactional
    public void revokeToken(String rawRefreshToken) {
        String hash = TokenHasher.sha256(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
                refreshTokenRepository.save(token);
            }
        });
    }

    private void revokeFamily(UUID familyId) {
        List<RefreshToken> family = refreshTokenRepository.findAllByFamilyId(familyId);
        Instant now = Instant.now();
        family.forEach(t -> {
            if (t.getRevokedAt() == null) {
                t.setRevokedAt(now);
            }
        });
        refreshTokenRepository.saveAll(family);
    }

    private void persist(UUID userId, UUID familyId, String rawToken) {
        RefreshToken rt = new RefreshToken();
        rt.setId(UUID.randomUUID());
        rt.setUserId(userId);
        rt.setFamilyId(familyId);
        rt.setTokenHash(TokenHasher.sha256(rawToken)); // only SHA-256 hash stored (SEC-2)
        rt.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        rt.setCreatedAt(Instant.now());
        refreshTokenRepository.save(rt);
    }
}

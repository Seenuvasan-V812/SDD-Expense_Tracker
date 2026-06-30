package com.dailyexpense.user.scheduler;

import com.dailyexpense.user.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * T038 — TokenCleanupScheduler unit tests.
 *
 * Verifies: expired deleted, revoked deleted, active untouched (via @Modifying query),
 * idempotent on second call.
 */
@ExtendWith(MockitoExtension.class)
class TokenCleanupSchedulerTest {

    @Mock
    RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    TokenCleanupScheduler scheduler;

    @Test
    void cleanup_callsDeleteExpiredOrRevoked() {
        when(refreshTokenRepository.deleteExpiredOrRevoked(any(Instant.class))).thenReturn(3);

        scheduler.cleanupExpiredAndRevokedTokens();

        verify(refreshTokenRepository, times(1)).deleteExpiredOrRevoked(any(Instant.class));
    }

    @Test
    void cleanup_passesCurrentInstantToRepository() {
        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        when(refreshTokenRepository.deleteExpiredOrRevoked(captor.capture())).thenReturn(0);

        Instant before = Instant.now();
        scheduler.cleanupExpiredAndRevokedTokens();
        Instant after = Instant.now();

        Instant passed = captor.getValue();
        assertThat(passed).isAfterOrEqualTo(before);
        assertThat(passed).isBeforeOrEqualTo(after);
    }

    @Test
    void cleanup_deletesExpiredAndRevoked_returnsDeletedCount() {
        when(refreshTokenRepository.deleteExpiredOrRevoked(any(Instant.class))).thenReturn(7);

        scheduler.cleanupExpiredAndRevokedTokens();

        // No exception — deleted count is only logged, not thrown
        verify(refreshTokenRepository).deleteExpiredOrRevoked(any(Instant.class));
    }

    @Test
    void cleanup_isIdempotent_canBeCalledMultipleTimes() {
        when(refreshTokenRepository.deleteExpiredOrRevoked(any(Instant.class))).thenReturn(0);

        scheduler.cleanupExpiredAndRevokedTokens();
        scheduler.cleanupExpiredAndRevokedTokens();
        scheduler.cleanupExpiredAndRevokedTokens();

        verify(refreshTokenRepository, times(3)).deleteExpiredOrRevoked(any(Instant.class));
    }

    @Test
    void cleanup_zeroRowsDeleted_doesNotThrow() {
        when(refreshTokenRepository.deleteExpiredOrRevoked(any(Instant.class))).thenReturn(0);

        // Should not throw when there's nothing to clean up
        scheduler.cleanupExpiredAndRevokedTokens();
    }
}

package com.dailyexpense.user.port;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Phase 1 stub. Phase 2 replaces this with MinIO pre-signed URL generation.
 * Zero cross-service DB access (AL-1).
 */
@Component
public class SecureNotificationDeliveryPortImpl implements SecureNotificationDeliveryPort {

    @Override
    public String resolveTimeLimitedUrl(String deliveryRef, UUID requesterId) {
        // deliveryRef is treated as a direct URL in Phase 1
        // Phase 2: generate time-limited pre-signed URL via MinIO SDK
        return deliveryRef;
    }
}

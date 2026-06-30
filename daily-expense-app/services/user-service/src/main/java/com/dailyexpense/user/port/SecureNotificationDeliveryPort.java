package com.dailyexpense.user.port;

import java.util.UUID;

/**
 * T036 — Resolves a delivery reference to a time-limited download URL.
 * Implementations MUST NOT perform any cross-service DB access (AL-1).
 */
public interface SecureNotificationDeliveryPort {

    /**
     * @param deliveryRef opaque reference stored in data_exports.download_ref
     * @param requesterId caller's userId for authorization
     * @return time-limited URL the caller can use to retrieve the export
     */
    String resolveTimeLimitedUrl(String deliveryRef, UUID requesterId);
}

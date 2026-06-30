package com.dailyexpense.expense.port;

import java.io.InputStream;

/**
 * T060 — Abstraction over object storage (MinIO in production).
 * Mockable in integration tests without a real MinIO server.
 */
public interface StoragePort {

    void store(String key, byte[] data, String mimeType);

    InputStream retrieve(String key);

    void delete(String key);
}

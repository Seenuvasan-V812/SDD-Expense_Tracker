package com.dailyexpense.expense.port;

import io.minio.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * T060 — MinIO-backed StoragePort implementation.
 * @MockBean StoragePort in integration tests replaces this automatically.
 */
@Component
public class MinioStorageAdapter implements StoragePort {

    private final MinioClient minioClient;
    private final String bucket;

    public MinioStorageAdapter(MinioClient minioClient,
                               @Value("${minio.bucket}") String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    @Override
    public void store(String key, byte[] data, String mimeType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(key)
                .stream(new ByteArrayInputStream(data), data.length, -1)
                .contentType(mimeType)
                .build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to store object: " + key, e);
        }
    }

    @Override
    public InputStream retrieve(String key) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(key)
                .build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to retrieve object: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucket)
                .object(key)
                .build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to delete object: " + key, e);
        }
    }
}

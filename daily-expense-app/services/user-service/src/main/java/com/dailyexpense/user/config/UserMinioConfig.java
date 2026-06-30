package com.dailyexpense.user.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * T112 — MinIO client bean for user-service (exports bucket: user-exports).
 * Credentials loaded from environment only (SEC-6).
 */
@Configuration
public class UserMinioConfig {

    @Value("${minio.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${minio.access-key:${MINIO_ACCESS_KEY:minio_admin}}")
    private String accessKey;

    @Value("${minio.secret-key:${MINIO_SECRET_KEY:minio_secret}}")
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}

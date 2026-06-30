package com.dailyexpense.user.service;

import com.dailyexpense.shared.exception.ForbiddenOwnershipException;
import com.dailyexpense.shared.outbox.EventEnvelope;
import com.dailyexpense.shared.outbox.OutboxPublisher;
import com.dailyexpense.user.domain.DataExport;
import com.dailyexpense.user.domain.DataExportStatus;
import com.dailyexpense.user.dto.UserExportSegment;
import com.dailyexpense.user.port.BudgetUserDataAdapter;
import com.dailyexpense.user.port.CategoryUserDataAdapter;
import com.dailyexpense.user.port.ExpenseUserDataAdapter;
import com.dailyexpense.user.port.LocalUserProfileAdapter;
import com.dailyexpense.user.port.SavingsGoalUserDataAdapter;
import com.dailyexpense.user.repository.DataExportRepository;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * T112 — DataExportAggregator: fans out to 4 service adapters + local user profile,
 * builds a ZIP, uploads to MinIO, marks the export READY, and writes DataExportReadyEvent
 * to the outbox — all in the same @Transactional (CQ-8).
 *
 * MinIO upload happens BEFORE the transaction so that the tx only covers the DB state change
 * + outbox event (MinIO is not transactional). If the tx rolls back, the orphan ZIP is harmless.
 */
@Service
public class DataExportAggregatorService {

    private static final Logger log = LoggerFactory.getLogger(DataExportAggregatorService.class);

    private final LocalUserProfileAdapter profileAdapter;
    private final CategoryUserDataAdapter categoryAdapter;
    private final ExpenseUserDataAdapter expenseAdapter;
    private final SavingsGoalUserDataAdapter savingsGoalAdapter;
    private final BudgetUserDataAdapter budgetAdapter;
    private final MinioClient minioClient;
    private final DataExportRepository exportRepository;
    private final OutboxPublisher outboxPublisher;

    @Value("${minio.bucket:user-exports}")
    private String bucket;

    public DataExportAggregatorService(
            LocalUserProfileAdapter profileAdapter,
            CategoryUserDataAdapter categoryAdapter,
            ExpenseUserDataAdapter expenseAdapter,
            SavingsGoalUserDataAdapter savingsGoalAdapter,
            BudgetUserDataAdapter budgetAdapter,
            MinioClient minioClient,
            DataExportRepository exportRepository,
            OutboxPublisher outboxPublisher) {
        this.profileAdapter = profileAdapter;
        this.categoryAdapter = categoryAdapter;
        this.expenseAdapter = expenseAdapter;
        this.savingsGoalAdapter = savingsGoalAdapter;
        this.budgetAdapter = budgetAdapter;
        this.minioClient = minioClient;
        this.exportRepository = exportRepository;
        this.outboxPublisher = outboxPublisher;
    }

    /**
     * Synchronous aggregation — call directly in tests or from an @Async wrapper.
     * Steps:
     *  1. Fan out to all adapters (outside tx).
     *  2. Build ZIP bytes (outside tx).
     *  3. Upload to MinIO (outside tx).
     *  4. @Transactional: mark READY + write DataExportReadyEvent to outbox (CQ-8).
     */
    public void runAggregation(UUID exportId, UUID userId) {
        log.info("Starting data export aggregation: exportId={}", exportId);

        // Step 1 — fan out (outside tx)
        List<UserExportSegment> segments = fanOut(userId);

        // Step 2 — build ZIP
        byte[] zipBytes = buildZip(exportId, segments);

        // Step 3 — upload to MinIO (outside tx)
        String storageKey = "exports/" + exportId + "/user-data.zip";
        uploadToMinio(zipBytes, storageKey);

        // Step 4 — @Transactional: READY + outbox event
        markReadyAndPublishEvent(exportId, userId, storageKey);

        log.info("Data export aggregation complete: exportId={}", exportId);
    }

    private List<UserExportSegment> fanOut(UUID userId) {
        return List.of(
                profileAdapter.exportUserData(userId),
                categoryAdapter.exportUserData(userId),
                expenseAdapter.exportUserData(userId),
                savingsGoalAdapter.exportUserData(userId),
                budgetAdapter.exportUserData(userId)
        );
    }

    private byte[] buildZip(UUID exportId, List<UserExportSegment> segments) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (UserExportSegment seg : segments) {
                ZipEntry entry = new ZipEntry(seg.serviceName() + ".json");
                zos.putNextEntry(entry);
                zos.write(seg.jsonContent().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        } catch (IOException e) {
            log.error("Failed to build ZIP for exportId={}: {}", exportId, e.getMessage());
            throw new IllegalStateException("Failed to build export ZIP", e);
        }
        return baos.toByteArray();
    }

    private void uploadToMinio(byte[] zipBytes, String storageKey) {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(storageKey)
                    .stream(new ByteArrayInputStream(zipBytes), zipBytes.length, -1)
                    .contentType("application/zip")
                    .build());
        } catch (Exception e) {
            log.error("MinIO upload failed for key={}: {}", storageKey, e.getMessage());
            throw new IllegalStateException("Failed to upload export to storage", e);
        }
    }

    @Transactional
    void markReadyAndPublishEvent(UUID exportId, UUID userId, String storageKey) {
        DataExport export = exportRepository.findById(exportId)
                .orElseThrow(ForbiddenOwnershipException::new);

        export.setStatus(DataExportStatus.READY);
        export.setDownloadRef(storageKey);
        export.setCompletedAt(Instant.now());
        export.setUpdatedAt(Instant.now());
        exportRepository.save(export);

        outboxPublisher.publish(EventEnvelope.builder()
                .eventType("DataExportReadyEvent")
                .producer("user-service")
                .userId(userId)
                .traceId(MDC.get("traceId"))
                .payload(String.format("{\"exportId\":\"%s\"}", exportId))
                .build());
    }
}

package com.dailyexpense.expense.service;

import com.dailyexpense.expense.domain.Expense;
import com.dailyexpense.expense.domain.Receipt;
import com.dailyexpense.expense.repository.ExpenseRepository;
import com.dailyexpense.expense.repository.ReceiptRepository;
import com.dailyexpense.shared.exception.BusinessConflictException;
import com.dailyexpense.shared.exception.ForbiddenOwnershipException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.dailyexpense.expense.port.StoragePort;

import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

/**
 * T060/T061 — Receipt upload / download / delete.
 * SEC-1: magic-byte validation (not Content-Type).
 * SEC-2: EXIF stripped before storage — 0 EXIF segments in stored bytes.
 * SEC-3: foreign expense → 403, never 404.
 * Storage key: receipts/{userId}/{uuid}.
 */
@Service
public class ReceiptService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptService.class);

    private final ReceiptRepository receiptRepository;
    private final ExpenseRepository expenseRepository;
    private final StoragePort storagePort;
    private final ImageSecurityService imageSecurityService;

    public ReceiptService(ReceiptRepository receiptRepository,
                          ExpenseRepository expenseRepository,
                          StoragePort storagePort,
                          ImageSecurityService imageSecurityService) {
        this.receiptRepository = receiptRepository;
        this.expenseRepository = expenseRepository;
        this.storagePort = storagePort;
        this.imageSecurityService = imageSecurityService;
    }

    /** T060: Upload — validate, strip EXIF, store, persist Receipt row. */
    @Transactional
    public Receipt upload(UUID userId, UUID expenseId, MultipartFile file) {
        // 403-never-404 (INV-1/SEC-3)
        Expense expense = expenseRepository.findById(expenseId)
            .orElseThrow(ForbiddenOwnershipException::new);
        if (!expense.getUserId().equals(userId)) throw new ForbiddenOwnershipException();

        if (receiptRepository.existsByExpenseId(expenseId)) {
            throw new BusinessConflictException("Expense already has a receipt");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot read uploaded file", e);
        }

        // Size check before magic-byte (fail fast)
        imageSecurityService.validateSize(bytes.length);

        // Magic-byte type detection — NOT Content-Type header
        ImageSecurityService.ImageMimeType format = imageSecurityService.detectFormat(bytes);
        if (format == null) {
            throw new IllegalArgumentException("Unsupported image format. Accepted: JPEG, PNG, WEBP");
        }

        // Pixel-flood guard (≤ 25 MP)
        imageSecurityService.validatePixels(bytes);

        // Strip EXIF — stored bytes will have 0 EXIF segments
        byte[] stripped = imageSecurityService.stripExif(bytes, format);

        // Server-generated key (SEC-5: client cannot influence key)
        String key = "receipts/" + userId + "/" + UUID.randomUUID();
        storagePort.store(key, stripped, format.mime);

        Receipt receipt = new Receipt();
        receipt.setId(UUID.randomUUID());
        receipt.setExpenseId(expenseId);
        receipt.setUserId(userId);
        receipt.setStorageKey(key);
        receipt.setMimeType(format.mime);
        receipt.setFileSizeBytes(stripped.length);
        receipt.setCreatedAt(Instant.now());
        receiptRepository.save(receipt);

        log.info("Receipt uploaded expenseId={} userId={} key={}", expenseId, userId, key);
        return receipt;
    }

    /** T061: Download — stream from storage with secure response headers. */
    public ReceiptDownload download(UUID userId, UUID expenseId) {
        Receipt receipt = receiptRepository.findByExpenseId(expenseId)
            .orElseThrow(ForbiddenOwnershipException::new);
        if (!receipt.getUserId().equals(userId)) throw new ForbiddenOwnershipException();

        InputStream stream = storagePort.retrieve(receipt.getStorageKey());
        return new ReceiptDownload(stream, receipt.getMimeType());
    }

    /** T061: Delete receipt row + storage object; Expense is retained. */
    @Transactional
    public void delete(UUID userId, UUID expenseId) {
        Receipt receipt = receiptRepository.findByExpenseId(expenseId)
            .orElseThrow(ForbiddenOwnershipException::new);
        if (!receipt.getUserId().equals(userId)) throw new ForbiddenOwnershipException();

        storagePort.delete(receipt.getStorageKey());
        receiptRepository.delete(receipt);
        log.info("Receipt deleted expenseId={} userId={}", expenseId, userId);
    }

    public record ReceiptDownload(InputStream stream, String mimeType) {}
}

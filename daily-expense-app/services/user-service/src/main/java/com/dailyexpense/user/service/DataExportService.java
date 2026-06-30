package com.dailyexpense.user.service;

import com.dailyexpense.shared.exception.ForbiddenOwnershipException;
import com.dailyexpense.user.domain.DataExport;
import com.dailyexpense.user.domain.DataExportStatus;
import com.dailyexpense.user.repository.DataExportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class DataExportService {

    private final DataExportRepository dataExportRepository;

    public DataExportService(DataExportRepository dataExportRepository) {
        this.dataExportRepository = dataExportRepository;
    }

    /**
     * T035 — Request data export: creates record with status REQUESTED and returns exportId.
     */
    @Transactional
    public DataExport requestExport(UUID userId) {
        DataExport export = new DataExport();
        export.setId(UUID.randomUUID());
        export.setUserId(userId);
        export.setStatus(DataExportStatus.REQUESTED);
        export.setRequestedAt(Instant.now());
        export.setCreatedAt(Instant.now());
        export.setUpdatedAt(Instant.now());
        return dataExportRepository.save(export);
    }

    /**
     * T035 — Ownership-checked download: owner→URL; foreign→403 (INV-1, 403-never-404).
     * download_ref is NEVER logged — only returned to the authenticated owner.
     */
    @Transactional(readOnly = true)
    public String getDownloadUrl(UUID callerId, UUID exportId) {
        DataExport export = dataExportRepository.findById(exportId)
                .orElseThrow(ForbiddenOwnershipException::new); // 403, not 404 (INV-1)

        if (!export.getUserId().equals(callerId)) {
            throw new ForbiddenOwnershipException();
        }

        return export.getDownloadRef() != null
                ? export.getDownloadRef()
                : "Export not ready — status: " + export.getStatus().name();
    }
}

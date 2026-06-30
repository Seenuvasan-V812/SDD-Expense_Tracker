package com.dailyexpense.expense.repository;

import com.dailyexpense.expense.domain.ProcessedImport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProcessedImportRepository extends JpaRepository<ProcessedImport, ProcessedImport.ImportKey> {

    Optional<ProcessedImport> findByIdempotencyKeyAndUserId(String idempotencyKey, UUID userId);
}

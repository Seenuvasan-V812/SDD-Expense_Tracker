package com.dailyexpense.user.repository;

import com.dailyexpense.user.domain.DataExport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DataExportRepository extends JpaRepository<DataExport, UUID> {
}

package com.dailyexpense.category.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CategoryOutboxRepository extends JpaRepository<CategoryOutboxEntry, UUID> {
}

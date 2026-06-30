package com.dailyexpense.user.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserOutboxRepository extends JpaRepository<UserOutboxEntry, UUID> {
}

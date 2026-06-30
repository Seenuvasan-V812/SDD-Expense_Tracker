package com.dailyexpense.user.repository;

import com.dailyexpense.user.domain.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, UUID> {

    Optional<EmailVerification> findByTokenHash(String tokenHash);
}

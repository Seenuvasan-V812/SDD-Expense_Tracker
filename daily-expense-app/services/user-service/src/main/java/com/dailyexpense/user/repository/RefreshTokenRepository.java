package com.dailyexpense.user.repository;

import com.dailyexpense.user.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByFamilyId(UUID familyId);

    List<RefreshToken> findAllByUserId(UUID userId);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now OR rt.revokedAt IS NOT NULL")
    int deleteExpiredOrRevoked(@Param("now") Instant now);
}

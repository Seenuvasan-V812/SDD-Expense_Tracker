package com.dailyexpense.expense.repository;

import com.dailyexpense.expense.domain.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TagRepository extends JpaRepository<Tag, UUID> {

    boolean existsByUserIdAndName(UUID userId, String name);

    Optional<Tag> findByIdAndUserId(UUID id, UUID userId);
}

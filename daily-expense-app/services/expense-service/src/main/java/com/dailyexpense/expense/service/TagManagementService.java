package com.dailyexpense.expense.service;

import com.dailyexpense.expense.domain.Tag;
import com.dailyexpense.expense.dto.CreateTagRequest;
import com.dailyexpense.expense.dto.TagResponse;
import com.dailyexpense.expense.repository.TagRepository;
import com.dailyexpense.shared.exception.BusinessConflictException;
import com.dailyexpense.shared.exception.ForbiddenOwnershipException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * T062 — Tag CRUD.
 * Delete detaches from Expenses via expense_tags ON DELETE CASCADE — Expenses are NOT deleted.
 * Duplicate name → 409 (same userId + name).
 * Foreign tag → 403-never-404 (INV-1/SEC-3).
 */
@Service
public class TagManagementService {

    private static final Logger log = LoggerFactory.getLogger(TagManagementService.class);

    private final TagRepository tagRepository;

    public TagManagementService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    @Transactional
    public TagResponse create(UUID userId, CreateTagRequest request) {
        if (tagRepository.existsByUserIdAndName(userId, request.name())) {
            throw new BusinessConflictException("Tag name already exists: " + request.name());
        }
        Tag tag = new Tag();
        tag.setId(UUID.randomUUID());
        tag.setUserId(userId);
        tag.setName(request.name());
        tag.setCreatedAt(Instant.now());
        Tag saved = tagRepository.save(tag);
        log.info("Tag created id={} userId={}", saved.getId(), userId);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public TagResponse getOne(UUID userId, UUID tagId) {
        return toResponse(findOrForbid(tagId, userId));
    }

    @Transactional
    public void delete(UUID userId, UUID tagId) {
        Tag tag = findOrForbid(tagId, userId);
        tagRepository.delete(tag);
        // DB CASCADE on expense_tags removes the tag_id from all Expense tagIds collections
        // Expenses themselves are NOT deleted
        log.info("Tag deleted id={} userId={}", tagId, userId);
    }

    private Tag findOrForbid(UUID tagId, UUID userId) {
        return tagRepository.findByIdAndUserId(tagId, userId)
            .orElseThrow(ForbiddenOwnershipException::new);
    }

    public TagResponse toResponse(Tag t) {
        return new TagResponse(t.getId(), t.getUserId(), t.getName(), t.getCreatedAt());
    }
}

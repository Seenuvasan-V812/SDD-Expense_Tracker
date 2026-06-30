package com.dailyexpense.expense.controller;

import com.dailyexpense.expense.dto.CreateTagRequest;
import com.dailyexpense.expense.dto.TagResponse;
import com.dailyexpense.expense.service.TagManagementService;
import com.dailyexpense.shared.security.CallerContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

/**
 * T062 — Tag CRUD at /api/v1/tags.
 * POST → 201; duplicate name → 409; DELETE detaches from Expenses (not deleted); foreign → 403.
 */
@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    private final TagManagementService tagManagementService;

    public TagController(TagManagementService tagManagementService) {
        this.tagManagementService = tagManagementService;
    }

    @PostMapping
    public ResponseEntity<TagResponse> create(
            @Valid @RequestBody CreateTagRequest request,
            @AuthenticationPrincipal CallerContext caller) {
        TagResponse response = tagManagementService.create(caller.userId(), request);
        return ResponseEntity
            .created(URI.create("/api/v1/tags/" + response.tagId()))
            .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TagResponse> getOne(
            @PathVariable UUID id,
            @AuthenticationPrincipal CallerContext caller) {
        return ResponseEntity.ok(tagManagementService.getOne(caller.userId(), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal CallerContext caller) {
        tagManagementService.delete(caller.userId(), id);
        return ResponseEntity.noContent().build();
    }
}

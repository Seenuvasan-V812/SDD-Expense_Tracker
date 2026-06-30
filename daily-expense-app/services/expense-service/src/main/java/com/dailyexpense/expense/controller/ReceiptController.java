package com.dailyexpense.expense.controller;

import com.dailyexpense.expense.service.ReceiptService;
import com.dailyexpense.expense.service.ReceiptService.ReceiptDownload;
import com.dailyexpense.shared.security.CallerContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.UUID;

/**
 * T060/T061 — Receipt upload / download / delete at /api/v1/expenses/{id}/receipt.
 * SEC headers: Content-Disposition: inline + X-Content-Type-Options: nosniff.
 * DELETE removes object+row; Expense is retained.
 */
@RestController
@RequestMapping("/api/v1/expenses/{expenseId}/receipt")
public class ReceiptController {

    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    /** T060: Upload JPEG/PNG/WEBP — magic-byte checked, EXIF stripped, pixel-flood guarded. */
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<Void> upload(
            @PathVariable UUID expenseId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CallerContext caller) {
        receiptService.upload(caller.userId(), expenseId, file);
        return ResponseEntity.status(201).build();
    }

    /** T061: Stream receipt inline with secure headers. */
    @GetMapping
    public ResponseEntity<StreamingResponseBody> download(
            @PathVariable UUID expenseId,
            @AuthenticationPrincipal CallerContext caller) {
        ReceiptDownload download = receiptService.download(caller.userId(), expenseId);
        StreamingResponseBody body = out -> {
            try (var in = download.stream()) {
                in.transferTo(out);
            }
        };
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, download.mimeType())
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"receipt\"")
            .header("X-Content-Type-Options", "nosniff")
            .body(body);
    }

    /** T061: Delete receipt row + storage object; Expense is retained. */
    @DeleteMapping
    public ResponseEntity<Void> delete(
            @PathVariable UUID expenseId,
            @AuthenticationPrincipal CallerContext caller) {
        receiptService.delete(caller.userId(), expenseId);
        return ResponseEntity.noContent().build();
    }
}

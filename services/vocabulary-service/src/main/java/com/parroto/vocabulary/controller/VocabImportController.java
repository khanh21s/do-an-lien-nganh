package com.parroto.vocabulary.controller;

import com.parroto.common.security.AuthenticatedUser;
import com.parroto.vocabulary.dto.ImportDtos.ImportCommitRequest;
import com.parroto.vocabulary.dto.ImportDtos.ImportCommitResponse;
import com.parroto.vocabulary.dto.ImportDtos.ImportPreviewResponse;
import com.parroto.vocabulary.service.importer.VocabImportService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * P2-C import. Preview: any signed-in user (with {@code deckId}: rows already in that deck are
 * flagged, and the caller must be allowed to manage it). Commit: owner of a personal deck or admin
 * of a public deck.
 */
@RestController
@RequestMapping("/vocabs/decks")
public class VocabImportController {

    private final VocabImportService service;

    public VocabImportController(VocabImportService service) {
        this.service = service;
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportPreviewResponse> preview(@AuthenticationPrincipal AuthenticatedUser principal,
                                                         @RequestParam("file") MultipartFile file,
                                                         @RequestParam(required = false) UUID deckId) {
        return ResponseEntity.ok(service.preview(principal, file, deckId));
    }

    @PostMapping("/{deckId}/import")
    public ResponseEntity<ImportCommitResponse> commit(@AuthenticationPrincipal AuthenticatedUser principal,
                                                       @PathVariable UUID deckId,
                                                       @RequestBody ImportCommitRequest request) {
        return ResponseEntity.ok(service.commit(principal, deckId, request));
    }
}

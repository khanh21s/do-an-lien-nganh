package com.parroto.practice.controller;

import com.parroto.practice.dto.ShadowingAttemptRequest;
import com.parroto.practice.dto.ShadowingAttemptResponse;
import com.parroto.practice.dto.ShadowingUploadUrlRequest;
import com.parroto.practice.dto.ShadowingUploadUrlResponse;
import com.parroto.common.exception.ApiException;
import com.parroto.common.security.AuthenticatedUser;
import com.parroto.practice.service.ShadowingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@RestController
@RequestMapping("/practice/shadowing")
public class ShadowingController {

    private final ShadowingService shadowingService;

    public ShadowingController(ShadowingService shadowingService) {
        this.shadowingService = shadowingService;
    }

    @PostMapping("/request-upload-url")
    public ResponseEntity<ShadowingUploadUrlResponse> requestUploadUrl(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ShadowingUploadUrlRequest request) {
        return ResponseEntity.ok(shadowingService.requestUploadUrl(principal, request));
    }

    /**
     * Local storage mode: raw {@code audio/wav} body (≤ 2 MB) uploaded with the caller's Bearer token.
     * Read straight from the servlet stream with a hard cap so oversized bodies are never buffered whole.
     */
    @PutMapping("/recordings/{recordingId}")
    public ResponseEntity<Void> uploadRecording(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @PathVariable String recordingId,
                                                HttpServletRequest request) throws IOException {
        shadowingService.requireLocalUploads();
        long declared = request.getContentLengthLong();
        if (declared > ShadowingService.MAX_UPLOAD_BYTES) {
            throw ApiException.badRequest("Bản ghi vượt quá 2 MB");
        }
        byte[] body;
        try (InputStream in = request.getInputStream()) {
            body = in.readNBytes(ShadowingService.MAX_UPLOAD_BYTES + 1);
        }
        shadowingService.uploadLocalRecording(principal, recordingId, body);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/attempt")
    public ResponseEntity<ShadowingAttemptResponse> submit(@AuthenticationPrincipal AuthenticatedUser principal,
                                                           @Valid @RequestBody ShadowingAttemptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shadowingService.submitAttempt(principal, request));
    }

    @GetMapping("/{attemptId}/recording")
    public void streamRecording(@AuthenticationPrincipal AuthenticatedUser principal,
                                @PathVariable UUID attemptId, HttpServletResponse response) {
        shadowingService.streamRecording(principal, attemptId, response);
    }
}

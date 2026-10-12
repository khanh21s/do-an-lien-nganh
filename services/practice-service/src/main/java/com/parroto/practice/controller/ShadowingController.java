package com.parroto.practice.controller;

import com.parroto.practice.dto.ShadowingAttemptRequest;
import com.parroto.practice.dto.ShadowingAttemptResponse;
import com.parroto.practice.dto.ShadowingUploadUrlRequest;
import com.parroto.practice.dto.ShadowingUploadUrlResponse;
import com.parroto.practice.security.AuthenticatedUser;
import com.parroto.practice.service.ShadowingService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/attempt")
    public ResponseEntity<ShadowingAttemptResponse> submit(@AuthenticationPrincipal AuthenticatedUser principal,
                                                             @Valid @RequestBody ShadowingAttemptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(shadowingService.submitAttempt(principal, request));
    }

    @GetMapping("/{attemptId}/recording")
    public void streamRecording(@PathVariable UUID attemptId, HttpServletResponse response) {
        shadowingService.streamRecording(attemptId, response);
    }
}

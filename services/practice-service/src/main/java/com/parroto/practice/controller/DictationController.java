package com.parroto.practice.controller;

import com.parroto.practice.dto.DictationAttemptRequest;
import com.parroto.practice.dto.DictationAttemptResponse;
import com.parroto.practice.security.AuthenticatedUser;
import com.parroto.practice.service.DictationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/practice/dictation")
public class DictationController {

    private final DictationService dictationService;

    public DictationController(DictationService dictationService) {
        this.dictationService = dictationService;
    }

    @PostMapping("/attempt")
    public ResponseEntity<DictationAttemptResponse> submit(@AuthenticationPrincipal AuthenticatedUser principal,
                                                             @Valid @RequestBody DictationAttemptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dictationService.submitAttempt(principal, request));
    }
}

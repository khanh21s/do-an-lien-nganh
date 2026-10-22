package com.parroto.vocabulary.controller;

import com.parroto.common.security.AuthenticatedUser;
import com.parroto.vocabulary.dto.DictionaryResponse;
import com.parroto.vocabulary.dto.DictionarySuggestion;
import com.parroto.vocabulary.service.dictionary.DictionaryService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/** P2-C: GET /vocabs/dictionary/{word}, public (the caller's own cards are included when a Bearer is sent). */
@RestController
@RequestMapping("/vocabs/dictionary")
public class DictionaryController {

    private final DictionaryService service;

    public DictionaryController(DictionaryService service) {
        this.service = service;
    }

    @GetMapping("/{word}")
    public ResponseEntity<DictionaryResponse> lookup(@AuthenticationPrincipal AuthenticatedUser principal,
                                                     @PathVariable String word) {
        // Personalised when signed in, so only the browser may cache it, briefly.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                .body(service.lookup(principal, word));
    }

    @GetMapping("/suggest")
    public ResponseEntity<List<DictionarySuggestion>> suggest(@AuthenticationPrincipal AuthenticatedUser principal,
                                                              @RequestParam("q") String query) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePrivate())
                .body(service.suggest(principal, query));
    }
}

package com.parroto.vocabulary.controller;

import com.parroto.vocabulary.dto.CardResponse;
import com.parroto.vocabulary.dto.MessageResponse;
import com.parroto.vocabulary.dto.UpdateCardRequest;
import com.parroto.vocabulary.security.AdminGuard;
import com.parroto.vocabulary.security.AuthenticatedUser;
import com.parroto.vocabulary.service.DeckService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/vocabs/cards")
public class CardController {

    private final DeckService deckService;
    private final AdminGuard adminGuard;

    public CardController(DeckService deckService, AdminGuard adminGuard) {
        this.deckService = deckService;
        this.adminGuard = adminGuard;
    }

    @PutMapping("/{id}")
    public ResponseEntity<CardResponse> updateCard(@AuthenticationPrincipal AuthenticatedUser principal,
                                                     @PathVariable UUID id,
                                                     @Valid @RequestBody UpdateCardRequest request) {
        adminGuard.requireAdmin(principal);
        return ResponseEntity.ok(deckService.updateCard(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> deleteCard(@AuthenticationPrincipal AuthenticatedUser principal,
                                                        @PathVariable UUID id) {
        adminGuard.requireAdmin(principal);
        deckService.deleteCard(id);
        return ResponseEntity.ok(new MessageResponse("Đã xoá thẻ"));
    }
}

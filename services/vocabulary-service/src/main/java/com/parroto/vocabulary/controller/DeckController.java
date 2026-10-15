package com.parroto.vocabulary.controller;

import com.parroto.common.dto.MessageResponse;
import com.parroto.common.dto.PageResponse;
import com.parroto.vocabulary.dto.*;
import com.parroto.common.security.AdminGuard;
import com.parroto.common.security.AuthenticatedUser;
import com.parroto.vocabulary.service.DeckService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/vocabs/decks")
public class DeckController {

    private final DeckService deckService;
    private final AdminGuard adminGuard;

    public DeckController(DeckService deckService, AdminGuard adminGuard) {
        this.deckService = deckService;
        this.adminGuard = adminGuard;
    }

    @GetMapping
    public ResponseEntity<PageResponse<DeckResponse>> list(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Boolean isProOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(deckService.listDecks(level, isProOnly, page, limit));
    }

    @GetMapping("/{id}/cards")
    public ResponseEntity<PageResponse<CardResponse>> cards(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id,
            @RequestParam(required = false) String searchWord,
            @RequestParam(required = false) String seed,
            @RequestParam(required = false) String groupId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(deckService.getDeckCards(principal, id, searchWord, seed, groupId, page, limit));
    }

    @PostMapping
    public ResponseEntity<DeckResponse> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @Valid @RequestBody CreateDeckRequest request) {
        adminGuard.requireAdmin(principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(deckService.createDeck(request, principal.userUuid()));
    }

    /** T14-VOC: admin edits a public deck's metadata; personal decks are 404. */
    @PutMapping("/{id}")
    public ResponseEntity<DeckResponse> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @PathVariable UUID id,
                                               @RequestBody UpdateDeckRequest request) {
        adminGuard.requireAdmin(principal);
        return ResponseEntity.ok(deckService.updateDeck(id, request));
    }

    @PostMapping("/{id}/cards")
    public ResponseEntity<List<CardResponse>> addCards(@AuthenticationPrincipal AuthenticatedUser principal,
                                                         @PathVariable UUID id,
                                                         @RequestParam(required = false) UUID groupId,
                                                         @Valid @RequestBody CreateCardsRequest request) {
        adminGuard.requireAdmin(principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(deckService.addCards(id, request, groupId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @PathVariable UUID id) {
        adminGuard.requireAdmin(principal);
        deckService.deleteDeck(id);
        return ResponseEntity.ok(new MessageResponse("Deck deleted successfully"));
    }
}

package com.parroto.vocabulary.controller;

import com.parroto.common.dto.MessageResponse;
import com.parroto.common.security.AuthenticatedUser;
import com.parroto.vocabulary.dto.CardResponse;
import com.parroto.vocabulary.dto.MyDeckDtos.PersonalCardRequest;
import com.parroto.vocabulary.dto.MyDeckDtos.PersonalDeckList;
import com.parroto.vocabulary.dto.MyDeckDtos.PersonalDeckRequest;
import com.parroto.vocabulary.dto.MyDeckDtos.PersonalDeckResponse;
import com.parroto.vocabulary.service.deck.PersonalDeckService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** P2-C "Bộ thẻ của tôi": the caller's personal decks and their cards (gateway: token required). */
@RestController
@RequestMapping("/vocabs/user")
public class PersonalDeckController {

    private final PersonalDeckService service;

    public PersonalDeckController(PersonalDeckService service) {
        this.service = service;
    }

    @GetMapping("/decks")
    public ResponseEntity<PersonalDeckList> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(service.list(principal.userUuid()));
    }

    @PostMapping("/decks")
    public ResponseEntity<PersonalDeckResponse> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                                       @RequestBody PersonalDeckRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(principal.userUuid(), request));
    }

    @PutMapping("/decks/{deckId}")
    public ResponseEntity<PersonalDeckResponse> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                                       @PathVariable UUID deckId,
                                                       @RequestBody PersonalDeckRequest request) {
        return ResponseEntity.ok(service.update(principal.userUuid(), deckId, request));
    }

    @DeleteMapping("/decks/{deckId}")
    public ResponseEntity<MessageResponse> delete(@AuthenticationPrincipal AuthenticatedUser principal,
                                                  @PathVariable UUID deckId) {
        service.delete(principal.userUuid(), deckId);
        return ResponseEntity.ok(new MessageResponse("Đã xoá bộ thẻ"));
    }

    @PostMapping("/decks/{deckId}/cards")
    public ResponseEntity<CardResponse> addCard(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @PathVariable UUID deckId,
                                                @RequestBody PersonalCardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addCard(principal.userUuid(), deckId, request));
    }

    @PutMapping("/cards/{cardId}")
    public ResponseEntity<CardResponse> updateCard(@AuthenticationPrincipal AuthenticatedUser principal,
                                                   @PathVariable UUID cardId,
                                                   @RequestBody PersonalCardRequest request) {
        return ResponseEntity.ok(service.updateCard(principal.userUuid(), cardId, request));
    }

    @DeleteMapping("/cards/{cardId}")
    public ResponseEntity<MessageResponse> deleteCard(@AuthenticationPrincipal AuthenticatedUser principal,
                                                      @PathVariable UUID cardId) {
        service.deleteCard(principal.userUuid(), cardId);
        return ResponseEntity.ok(new MessageResponse("Đã xoá thẻ"));
    }
}

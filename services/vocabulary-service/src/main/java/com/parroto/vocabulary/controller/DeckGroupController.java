package com.parroto.vocabulary.controller;

import com.parroto.common.dto.MessageResponse;
import com.parroto.common.security.AuthenticatedUser;
import com.parroto.vocabulary.dto.MyDeckDtos.DeckDetailResponse;
import com.parroto.vocabulary.dto.MyDeckDtos.GroupOrderRequest;
import com.parroto.vocabulary.dto.MyDeckDtos.GroupRequest;
import com.parroto.vocabulary.dto.MyDeckDtos.GroupResponse;
import com.parroto.vocabulary.dto.MyDeckDtos.MoveCardsRequest;
import com.parroto.vocabulary.dto.MyDeckDtos.MoveCardsResponse;
import com.parroto.vocabulary.service.deck.DeckGroupService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * P2-C deck detail + groups, for public decks (GET: anyone, writes: admin) and personal decks
 * (owner only, others 404). GETs are public at the gateway (Bearer forwarded when present).
 */
@RestController
@RequestMapping("/vocabs/decks/{deckId}")
public class DeckGroupController {

    private final DeckGroupService service;

    public DeckGroupController(DeckGroupService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<DeckDetailResponse> detail(@AuthenticationPrincipal AuthenticatedUser principal,
                                                     @PathVariable UUID deckId) {
        return ResponseEntity.ok(service.detail(principal, deckId));
    }

    @GetMapping("/groups")
    public ResponseEntity<List<GroupResponse>> groups(@AuthenticationPrincipal AuthenticatedUser principal,
                                                      @PathVariable UUID deckId) {
        return ResponseEntity.ok(service.list(principal, deckId));
    }

    @PostMapping("/groups")
    public ResponseEntity<GroupResponse> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @PathVariable UUID deckId, @RequestBody GroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(principal, deckId, request));
    }

    @PutMapping("/groups/order")
    public ResponseEntity<List<GroupResponse>> reorder(@AuthenticationPrincipal AuthenticatedUser principal,
                                                       @PathVariable UUID deckId,
                                                       @RequestBody GroupOrderRequest request) {
        return ResponseEntity.ok(service.reorder(principal, deckId, request));
    }

    @PutMapping("/groups/{groupId}")
    public ResponseEntity<GroupResponse> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @PathVariable UUID deckId, @PathVariable UUID groupId,
                                                @RequestBody GroupRequest request) {
        return ResponseEntity.ok(service.update(principal, deckId, groupId, request));
    }

    @DeleteMapping("/groups/{groupId}")
    public ResponseEntity<MessageResponse> delete(@AuthenticationPrincipal AuthenticatedUser principal,
                                                  @PathVariable UUID deckId, @PathVariable UUID groupId) {
        service.delete(principal, deckId, groupId);
        return ResponseEntity.ok(new MessageResponse("Đã xoá nhóm (các thẻ được giữ lại, không thuộc nhóm nào)"));
    }

    @PutMapping("/cards/group")
    public ResponseEntity<MoveCardsResponse> moveCards(@AuthenticationPrincipal AuthenticatedUser principal,
                                                       @PathVariable UUID deckId,
                                                       @RequestBody MoveCardsRequest request) {
        return ResponseEntity.ok(service.moveCards(principal, deckId, request));
    }
}

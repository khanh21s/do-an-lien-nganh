package com.parroto.vocabulary.controller;

import com.parroto.vocabulary.dto.GameDtos;
import com.parroto.vocabulary.security.AuthenticatedUser;
import com.parroto.vocabulary.service.GameService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** T15 "Dịch nhanh": Vietnamese meaning -> type the English word, scored server-side. */
@RestController
@RequestMapping("/vocabs/games")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping("/sessions")
    public ResponseEntity<GameDtos.GameState> start(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @RequestBody GameDtos.StartGameRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(gameService.start(principal, request));
    }

    @PostMapping("/sessions/{id}/hint")
    public ResponseEntity<GameDtos.Hint> hint(@AuthenticationPrincipal AuthenticatedUser principal,
                                              @PathVariable UUID id) {
        return ResponseEntity.ok(gameService.hint(principal.userUuid(), id));
    }

    @PostMapping("/sessions/{id}/answer")
    public ResponseEntity<GameDtos.AnswerResult> answer(@AuthenticationPrincipal AuthenticatedUser principal,
                                                        @PathVariable UUID id,
                                                        @RequestBody GameDtos.AnswerRequest request) {
        return ResponseEntity.ok(gameService.answer(principal.userUuid(), id, request));
    }

    @PostMapping("/sessions/{id}/finish")
    public ResponseEntity<GameDtos.Summary> finish(@AuthenticationPrincipal AuthenticatedUser principal,
                                                   @PathVariable UUID id) {
        return ResponseEntity.ok(gameService.finish(principal.userUuid(), id));
    }

    @GetMapping("/me")
    public ResponseEntity<GameDtos.Stats> stats(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(gameService.stats(principal.userUuid()));
    }
}

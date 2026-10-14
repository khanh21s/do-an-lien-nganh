package com.parroto.vocabulary.controller;

import com.parroto.vocabulary.dto.GameDtos;
import com.parroto.vocabulary.security.AuthenticatedUser;
import com.parroto.vocabulary.service.GameProgressService;
import com.parroto.vocabulary.service.GameService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** T15/T16 "Dịch nhanh": Vietnamese meaning -> type the English word, scored server-side. */
@RestController
@RequestMapping("/vocabs/games")
public class GameController {

    private final GameService gameService;
    private final GameProgressService progressService;

    public GameController(GameService gameService, GameProgressService progressService) {
        this.gameService = gameService;
        this.progressService = progressService;
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

    /** T16: daily points goal (100/200/300/500). */
    @PutMapping("/me/goal")
    public ResponseEntity<GameDtos.Progress> setGoal(@AuthenticationPrincipal AuthenticatedUser principal,
                                                     @RequestBody GameDtos.GoalRequest request) {
        return ResponseEntity.ok(progressService.setDailyGoal(principal.userUuid(), request.dailyGoal()));
    }

    /** T16: {@code board} = daily | sprint-week | survival-week. */
    @GetMapping("/leaderboard")
    public ResponseEntity<GameDtos.Leaderboard> leaderboard(@AuthenticationPrincipal AuthenticatedUser principal,
                                                            @RequestParam(defaultValue = "daily") String board) {
        return ResponseEntity.ok(progressService.leaderboard(board, principal.userUuid()));
    }
}

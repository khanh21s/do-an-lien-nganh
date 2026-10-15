package com.parroto.vocabulary.controller;

import com.parroto.common.security.AuthenticatedUser;
import com.parroto.vocabulary.dto.GamificationDtos.CheckInResult;
import com.parroto.vocabulary.dto.GamificationDtos.GamificationMe;
import com.parroto.vocabulary.dto.GamificationDtos.XpLeaderboard;
import com.parroto.vocabulary.service.gamification.GamificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** P2-B: XP / level / diamonds / check-in / daily missions of the signed-in user. */
@RestController
@RequestMapping("/vocabs/gamification")
public class GamificationController {

    private final GamificationService gamificationService;

    public GamificationController(GamificationService gamificationService) {
        this.gamificationService = gamificationService;
    }

    @GetMapping("/me")
    public ResponseEntity<GamificationMe> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(gamificationService.me(principal));
    }

    /** Once per Vietnam day; repeated calls return {@code alreadyCheckedIn: true} and grant nothing. */
    @PostMapping("/check-in")
    public ResponseEntity<CheckInResult> checkIn(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(gamificationService.checkIn(principal));
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<XpLeaderboard> leaderboard(@AuthenticationPrincipal AuthenticatedUser principal,
                                                     @RequestParam(defaultValue = "week") String period) {
        return ResponseEntity.ok(gamificationService.leaderboard(period, principal));
    }
}

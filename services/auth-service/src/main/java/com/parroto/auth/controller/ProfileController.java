package com.parroto.auth.controller;

import com.parroto.auth.dto.ChangePasswordRequest;
import com.parroto.common.dto.MessageResponse;
import com.parroto.auth.dto.ProfileResponse;
import com.parroto.auth.dto.UpdateProfileRequest;
import com.parroto.common.security.AuthenticatedUser;
import com.parroto.auth.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/profile")
    public ResponseEntity<ProfileResponse> getProfile(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(profileService.getProfile(principal.userUuid()));
    }

    @PutMapping("/profile")
    public ResponseEntity<ProfileResponse> updateProfile(@AuthenticationPrincipal AuthenticatedUser principal,
                                                           @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(profileService.updateProfile(principal.userUuid(), request));
    }

    @PutMapping("/change-password")
    public ResponseEntity<MessageResponse> changePassword(@AuthenticationPrincipal AuthenticatedUser principal,
                                                            @Valid @RequestBody ChangePasswordRequest request) {
        profileService.changePassword(principal.userUuid(), request.currentPassword(), request.newPassword());
        return ResponseEntity.ok(new MessageResponse("Password updated successfully"));
    }

    @DeleteMapping("/account")
    public ResponseEntity<MessageResponse> deleteAccount(@AuthenticationPrincipal AuthenticatedUser principal) {
        profileService.deleteAccount(principal.userUuid());
        return ResponseEntity.ok(new MessageResponse("Account deactivated successfully"));
    }
}

package com.parroto.auth.controller;

import com.parroto.auth.dto.InternalUserResponse;
import com.parroto.common.exception.ApiException;
import com.parroto.auth.repository.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Service-to-service only (not exposed through the API gateway): lets other
 * services resolve a user's email, e.g. subscription-service sending payment
 * confirmation emails.
 */
@RestController
public class InternalUserController {

    private final UserRepository userRepository;

    public InternalUserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/internal/users/{id}")
    public InternalUserResponse getUser(@PathVariable UUID id) {
        var user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        return new InternalUserResponse(user.getId(), user.getEmail(), Boolean.TRUE.equals(user.getIsActive()));
    }
}

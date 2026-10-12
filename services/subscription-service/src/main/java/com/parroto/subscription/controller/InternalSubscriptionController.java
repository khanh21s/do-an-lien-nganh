package com.parroto.subscription.controller;

import com.parroto.subscription.service.SubscriptionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Service-to-service only (not exposed through the API gateway): lets other services
 * (e.g. vocabulary-service gating Pro-only decks) check premium status without
 * duplicating subscription logic.
 */
@RestController
public class InternalSubscriptionController {

    private final SubscriptionService subscriptionService;

    public InternalSubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/internal/subscriptions/{userId}/active")
    public Map<String, Boolean> isActive(@PathVariable UUID userId) {
        return Map.of("isPremium", subscriptionService.isPremium(userId));
    }
}

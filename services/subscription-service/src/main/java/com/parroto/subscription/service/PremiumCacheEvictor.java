package com.parroto.subscription.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * SUB-04: deletes vocabulary-service's cached premium flag ({@code premium:{userId}}, shared Redis,
 * architecture §4.4) after premium is granted, so Pro decks unlock immediately instead of after
 * the 5-minute TTL. Redis failures are logged, not propagated: the payment is already committed and
 * the stale cache entry expires on its own.
 */
@Component
public class PremiumCacheEvictor {

    private static final Logger log = LoggerFactory.getLogger(PremiumCacheEvictor.class);

    private final RedisTemplate<String, Object> redisTemplate;

    public PremiumCacheEvictor(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public static String key(UUID userId) {
        return "premium:" + userId;
    }

    /** Evicts after the current transaction commits (immediately if there is none). */
    public void evictAfterCommit(UUID userId) {
        AfterCommit.run(() -> evict(userId));
    }

    void evict(UUID userId) {
        try {
            redisTemplate.delete(key(userId));
        } catch (DataAccessException e) {
            log.warn("Could not evict {} from Redis: {}", key(userId), e.getMessage());
        }
    }
}

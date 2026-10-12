package com.parroto.auth.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class TokenBlacklistService {

    private static final String PREFIX = "token_blacklist:";

    private final StringRedisTemplate redisTemplate;

    public TokenBlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void blacklist(String jti, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            return;
        }
        redisTemplate.opsForValue().set(PREFIX + jti, "1", Duration.ofSeconds(ttlSeconds));
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + jti));
    }
}

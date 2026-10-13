package com.parroto.subscription.service;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class CacheService {

    /** SCAN page-size hint and DEL batch size. */
    private static final int SCAN_BATCH = 500;

    private final RedisTemplate<String, Object> redisTemplate;

    public CacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void set(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = redisTemplate.opsForValue().get(key);
        return type.isInstance(value) ? (T) value : null;
    }

    /**
     * Deletes every key matching {@code pattern} using an incremental SCAN cursor and batched DEL,
     * never the blocking KEYS command (X-06).
     */
    public void invalidateByPattern(String pattern) {
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(SCAN_BATCH).build();
        List<String> batch = new ArrayList<>(SCAN_BATCH);
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= SCAN_BATCH) {
                    redisTemplate.delete(batch);
                    batch = new ArrayList<>(SCAN_BATCH);
                }
            }
        }
        if (!batch.isEmpty()) {
            redisTemplate.delete(batch);
        }
    }
}

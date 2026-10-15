package com.parroto.common.config;

import com.parroto.common.cache.CacheService;
import com.parroto.common.cache.RedisConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * JSON {@code RedisTemplate<String, Object>} (bean name {@code redisTemplate}, replacing Spring Boot's
 * default one) + {@link CacheService}. Imported by the services that cache responses in Redis.
 */
@Configuration(proxyBeanMethods = false)
@Import({RedisConfig.class, CacheService.class})
public class ParrotoCacheConfig {
}

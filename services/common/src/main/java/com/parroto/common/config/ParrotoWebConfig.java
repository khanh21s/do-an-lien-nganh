package com.parroto.common.config;

import com.parroto.common.security.JwtAuthFilter;
import com.parroto.common.security.JwtValidator;
import com.parroto.common.web.RequestIdFilter;
import com.parroto.common.web.RestAuthEntryPoint;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Beans every Parroto service needs for its stateless REST security: request-id propagation,
 * the JSON 401 entry point, and validation of the shared-secret access JWT (+ Redis blacklist).
 * Services pull it in explicitly from their own {@code config/CommonConfig} with {@code @Import}.
 * Requires a {@code StringRedisTemplate} (spring-boot-starter-data-redis) and {@code jwt.secret}.
 */
@Configuration(proxyBeanMethods = false)
@Import({RequestIdFilter.class, RestAuthEntryPoint.class, JwtValidator.class, JwtAuthFilter.class})
public class ParrotoWebConfig {
}

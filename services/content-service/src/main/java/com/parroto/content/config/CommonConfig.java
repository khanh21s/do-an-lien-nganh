package com.parroto.content.config;

import com.parroto.common.config.ParrotoWebConfig;
import com.parroto.common.config.ParrotoCacheConfig;
import com.parroto.common.security.AdminGuard;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Shared infrastructure from services/common (artifact com.parroto:parroto-common), imported
 * explicitly: com.parroto.common is outside this service's component-scan base package.
 */
@Configuration(proxyBeanMethods = false)
@Import({ParrotoWebConfig.class, ParrotoCacheConfig.class, AdminGuard.class})
public class CommonConfig {
}

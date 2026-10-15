package com.parroto.practice.config;

import com.parroto.common.config.ParrotoWebConfig;
import com.parroto.common.config.ParrotoCacheConfig;
import com.parroto.common.security.AdminGuard;
import com.parroto.common.security.InternalTokenFilter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Shared infrastructure from services/common (artifact com.parroto:parroto-common), imported
 * explicitly: com.parroto.common is outside this service's component-scan base package.
 */
@Configuration(proxyBeanMethods = false)
// AdminGuard: P2-A1 admins bypass the Pro-lesson practice gate.
@Import({ParrotoWebConfig.class, ParrotoCacheConfig.class, InternalTokenFilter.class, AdminGuard.class})
public class CommonConfig {
}

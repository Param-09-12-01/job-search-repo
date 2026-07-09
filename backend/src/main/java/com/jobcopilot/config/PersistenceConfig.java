package com.jobcopilot.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA auditing (createdAt/updatedAt population) and binds {@link AppProperties}.
 */
@Configuration
@EnableJpaAuditing
@EnableConfigurationProperties(AppProperties.class)
public class PersistenceConfig {
}

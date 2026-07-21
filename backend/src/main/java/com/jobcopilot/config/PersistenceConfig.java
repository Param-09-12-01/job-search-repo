package com.jobcopilot.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Enables JPA auditing (createdAt/updatedAt population) and binds {@link AppProperties}.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "utcDateTimeProvider")
@EnableConfigurationProperties(AppProperties.class)
public class PersistenceConfig {

    @Bean
    public DateTimeProvider utcDateTimeProvider() {
        return () -> Optional.of(java.time.LocalDateTime.now(ZoneOffset.UTC));
    }
}

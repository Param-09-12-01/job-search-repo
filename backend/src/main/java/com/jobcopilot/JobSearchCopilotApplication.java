package com.jobcopilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Job Search Copilot backend.
 *
 * <p>The platform aggregates job postings from multiple external providers, scores them against
 * a single user's profile, and helps the user prepare (but never auto-submit) applications.</p>
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableCaching
public class JobSearchCopilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobSearchCopilotApplication.class, args);
    }
}
